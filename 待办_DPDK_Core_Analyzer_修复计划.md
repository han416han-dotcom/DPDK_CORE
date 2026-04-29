# DPDK Core Analyzer — 修复计划

> 更新日期: 2026-04-29
> 基于多轮测试与需求对照分析

---

## 核心任务要求

1. **CoreDump 实时解析工具** — 自动捕获 DPDK 应用 CoreDump 事件，AI 秒级提取 Core 文件、运行日志中的异常特征，无需手动执行 gdb/elfutils 命令
2. **AI 辅助一键定位模块** — 集成 DPDK 常见异常案例库，AI 快速匹配特征与根因（如内存池操作异常、多核线程资源竞争、驱动适配冲突等），第一时间输出疑似根因与排查路径
3. **可视化快速定位平台** — 支持上传 Core 文件/日志，AI 自动生成调用栈可视化图谱、异常时序线，直观标记问题节点，降低定位门槛

---

## 目录

- [一、已完成修复汇总](#一已完成修复汇总)
- [二、关于 lcore 线程识别](#二关于-lcore-线程识别)
- [三、关于 ELF strip 与符号解析](#三关于-elf-strip-与符号解析)
- [四、需求-实现差距分析](#四需求-实现差距分析)
- [五、补充修复计划](#五补充修复计划)

---

## 一、已完成修复汇总

### 1.1 GDB 脚本优化

- 添加 `set backtrace limit 0` 确保无限回溯
- **删除** FALLBACK_BT 段（`thread apply all frame apply all -s -c "bt 0"`）— 该命令导致 GDB 挂起/超时，是 GDB 完全不可用的根因
- GDB 脚本路径改为始终从 jar classpath 加载，不依赖文件系统

### 1.2 CoreDumpProcessor 崩溃修复

- `Files.size()` 在 `Files.deleteIfExists()` 之后调用引发 `NoSuchFileException` 级联崩溃—修复为先保存 `outputSize` 变量再删除文件

### 1.3 崩溃地址 0x0 处理

- `parse_gdb_log.py`: 当 `fault_address == 0x0`（SIGABRT/SIGFPE 的 si_addr 正常为 0x0），回退到线程 #0 第一帧 PC 地址

### 1.4 lcore 识别加固

- `DpdkAnalyzer.java`: 从"只查栈底帧"改为"扫描所有帧"匹配 `eal_worker_thread_loop`，更健壮
- 增加框架提取 `lcore_id=` 参数

### 1.5 符号解析增强

- `SymbolResolver.java`: 空符号表时自动重试仅读 `.dynsym` 节
- `parse_elf.py`: 新增 `dynsym_only` 输入参数

### 1.6 可视化与前端资产

- 新增 `ThreadDistChart.vue` — 线程分布水平柱状图，每线程按栈帧数排列，颜色区分类别
- 新增 `DpdkRatioChart.vue` — DPDK 函数占比环形图，中心显示百分比
- 新增 `favicon.svg` — DPDK 风格浏览器 tab 图标（蓝色底 + 白色方格）
- 替换可视化 tab 中两个 `ChartPlaceholder` 为真实 ECharts 图表
- `pom.xml`: npm build 从 `prepare-package` 提前到 `generate-resources`，确保构建产物及时打包

### 1.7 其他修复

- `Aggregator.java`: 崩溃线程 `stack_depth <= 1` 输出 WARN 日志，提示编译需加 `-g -fno-omit-frame-pointer`
- `application-prod.yml`: 上传目录 `/data/dpdk-core/uploads` → `/opt/dpdk-core/uploads`

---

## 二、关于 lcore 线程识别

### 2.1 现象回顾

任务解析结果中 `is_lcore` 全部为 `false`，lcore = 0。

### 2.2 实际数据验证

通过逐一线程查看调用栈，确认线程角色分布如下：

| 线程 | 栈底/标志性函数 | 角色 | 应为 lcore? |
|------|----------------|------|------------|
| #1 (crash) | `main` | 崩溃主线程 | ✗ |
| #3 | `eal_intr_thread_main` | 中断处理服务线程 | ✗ |
| #4 | `mp_handle` | 多进程通信服务线程 | ✗ |
| #5 | `socket_listener` | Telemetry 监听服务线程 | ✗ |
| #6 | `start_thread`/`clone3` | 无 `eal_worker_thread_loop` | ✗ |
| #2/#7 | `start_thread`/`clone3` | 同上 | ✗ |

**结论：此 core dump 确实没有 lcore worker 线程，lcore = 0 是正确的。**

崩溃时数据面线程已退出（或未启动），仅剩下 DPDK 基础设施线程和主线程。这不是 Bug，而是**数据本身的特性**。

### 2.3 已执行的加固

虽然当前数据正确，但为了防御未来 core dump 中真正出现 lcore worker 线程时能正确识别，已将检测逻辑从"只检查栈底帧"改为"扫描所有帧"。这样可以应对栈底为 `start_thread`/`clone3` 而 `eal_worker_thread_loop` 在栈中间的情况。

---

## 三、关于 ELF strip 与符号解析

### 3.1 现象

解析日志中出现：
```
SymbolResolver: ELF 符号表为空 (文件可能已被 strip)
```

### 3.2 影响分析

| 场景 | 有符号表 | 无符号表（被 strip） |
|------|---------|-------------------|
| GDB 已解析的帧 | ✅ 有函数名+源文件 | ✅ 同左（来自 GDB） |
| GDB 标记为 `??` 的帧 | ✅ 可通过地址查符号表回填 | ❌ 无法二次解析 |
| 地址→函数名映射 | ✅ 可偏移计算 | ❌ 无法 |

当前测试数据中所有帧均为 GDB 可直接解析的（full match，confidence=100），strip 影响不大。但如果遇到 `??` 帧（常见于 inline 函数被优化、strip 后丢失函数名），SymbolResolver 二次解析将失效。

### 3.3 已执行的修复

- 当 ELF 符号表为空时，自动尝试只读 `.dynsym`（动态符号表，strip 后通常保留）
- 若 `.dynsym` 也为空，记录 WARN 但不中断流程

---

## 四、需求-实现差距分析

### 4.1 需求一：CoreDump 实时解析工具

> 自动捕获 DPDK 应用 CoreDump 事件，AI 秒级提取 Core 文件、运行日志中的异常特征，无需手动执行 gdb/elfutils 命令

| 要求 | 实现状态 |
|------|---------|
| 自动捕获 CoreDump 事件 | ❌ **未实现** — 用户需手动上传 core 文件，无服务端 watchdog 监听目录自动触发 |
| 秒级提取异常特征（信号、地址、函数） | ✅ 已实现 — Pipeline 自动调用 GDB → Python 解析 → 提取 crash_signal/fault_address/寄存器 |
| 无需手动 gdb/elfutils | ✅ 已实现 — CoreDumpProcessor 自动运行 GDB，PythonExecutor 自动调用 ELF 解析 |

### 4.2 需求二：AI 辅助一键定位模块

> 集成 DPDK 常见异常案例库，AI 快速匹配特征与根因（如内存池操作异常、多核线程资源竞争、驱动适配冲突等），第一时间输出疑似根因与排查路径

| 要求 | 实现状态 |
|------|---------|
| DPDK 常见异常案例库 | ❌ **未实现** — 无结构化案例库，无历史案例对比 |
| AI 匹配特征与根因 | ⚠️ **部分** — DiagnosisPanel 为简单规则（if SIGSEGV+addr=0x0 → 空指针），非真正 AI/ML |
| 内存池操作异常检测 | ⚠️ **部分** — DpdkAnalyzer 标记 `rte_mempool_get/put`、`rte_ring_dequeue/enqueue` 等函数，但无根因分析 |
| 多核线程资源竞争检测 | ❌ **未实现** |
| 驱动适配冲突检测 | ❌ **未实现** |
| 输出疑似根因与排查路径 | ⚠️ **部分** — 仅覆盖 5 种信号基础判断，建议文案固定 |

### 4.3 需求三：可视化快速定位平台

> 支持上传 Core 文件/日志，AI 自动生成调用栈可视化图谱、异常时序线，直观标记问题节点，降低定位门槛

| 要求 | 实现状态 |
|------|---------|
| 上传 Core 文件/日志 | ✅ 已实现 — UploadPage.vue 三步向导（GDB_LOG / CORE_DUMP / EXECUTABLE） |
| 调用栈可视化图谱 | ✅ 已实现 — FlameGraph.vue 每线程水平条展示，崩溃帧红色、DPDK 函数橙色 |
| 异常时序线 | ⚠️ **部分实现** — 线程分布图（ThreadDistChart）和 DPDK 函数占比图（DpdkRatioChart）已完成，时序线图尚未规划 |
| 直观标记问题节点 | ✅ 已实现 — 崩溃线程红框、lcore 标签、DPDK 标签、低可信度标签 |

### 4.4 图片与静态资产

| 项目 | 当前状态 | 建议 |
|------|---------|------|
| Favicon | ✅ 已实现 — `favicon.svg`，蓝色 DPDK 风格图标 | 浏览器 tab 显示正常 |
| Logo | ⚠️ 用 `<el-icon><Cpu /></el-icon>` 纯图标 | 可选替换为图片 logo |
| 空状态插图 | ❌ 纯文字"暂无数据" | 上传页/任务列表空状态可加插图 |
| 线程分布图 | ✅ 已实现 — `ThreadDistChart.vue` 水平柱状图 | 正式图表已替换占位 |
| DPDK 函数占比图 | ✅ 已实现 — `DpdkRatioChart.vue` 环形图 | 正式图表已替换占位 |

---

## 五、补充修复计划

### 优先级排序

| 优先级 | 功能项 | 说明 | 涉及文件 | 预估工作量 |
|--------|--------|------|---------|-----------|
| P1 | 扩充 DiagnosisPanel 规则库 | 覆盖更多崩溃模式，细化建议文案 | `frontend/src/components/DiagnosisPanel.vue` | 中 |
| P2 | 服务端 watchdog 自动捕获 CoreDump | 监听目录 + 自动入队解析 | 新增 `CoreDumpWatcher.java` + 配置 | 大 |
| P3 | 构建结构化异常案例库 | 支持历史案例匹配与相似度推荐 | 新增案例库表 + 匹配引擎 | 大 |
| P3 | 多核资源竞争专项检测 | 检测锁竞争、CAS 失败等模式 | `DpdkAnalyzer.java` | 中 |
| P3 | 驱动适配冲突检测 | 检查驱动的版本/参数兼容性 | 新增 `DriverAnalyzer.java` | 中 |

### 建议执行顺序

1. **P1 智能诊断增强** — 扩充 DiagnosisPanel 的规则覆盖面和建议质量
2. **P2 Watchdog 自动捕获** — 新增目录监听服务，实现真正"自动"
3. **P3 案例库+专项分析** — 长期演进方向

---

*本文档由 opencode 根据多轮测试数据与源码分析生成。*
