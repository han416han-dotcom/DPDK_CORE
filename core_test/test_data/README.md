# 测试数据说明

## 概述

8 个模拟 GDB 日志文件，覆盖不同 crash 场景。
格式匹配 `gdb/generate_dpdk_core_log.gdb` 标准化规范。
可配合 `core_test/` 下现有的 ELF 可执行文件进行上传测试。

## 文件列表

| 文件 | 场景 | 关键特征 |
|------|------|----------|
| `test_nullptr.log` | 空指针解引用 SIGSEGV | 2 线程, null 地址, cause_crash 崩溃帧 |
| `test_fpe.log` | 除零异常 SIGFPE | 单线程, assert_fail 调用链 |
| `test_double_free.log` | Double Free SIGABRT | malloc_printerr 崩溃 |
| `test_stack_overflow.log` | 栈溢出 SIGSEGV | 21+ 栈帧, 递归 |
| `test_oob.log` | 数组越界 SIGSEGV | 越界写导致 malloc 内部崩溃 |
| `test_dpdk_crash.log` | **DPDK ring 空指针** | 5 线程 (4 lcore), rte_panic+rte_ring_dequeue, hugepage |
| `test_dpdk_mbuf_double_free.log` | **DPDK mbuf 双释** | rte_panic+rte_mbuf_sanity_check+rte_pktmbuf_free |
| `test_dpdk_mempool_exhausted.log` | **DPDK mempool 耗尽** | 4 线程 (3 lcore), rte_mempool_get_bulk 失败 |

## 使用方式

```powershell
# 1. 启动后端
cd DPDK_CORE
mvn spring-boot:run

# 2. 启动前端 (另一个终端)
cd DPDK_CORE/frontend
npm run dev

# 3. 浏览器访问 http://localhost:5173
# 4. 上传: GDB日志 选 test_data/*.log, 可执行文件 选 ../app_double_free 等
# 5. 创建任务查看解析结果
```

## Linux 上生成真实数据

```bash
# 1. 编译测试程序 (带debug)
g++ -g -o app_test double_free_test.cpp

# 2. 运行并收集 core dump
ulimit -c unlimited
./app_test

# 3. 用 GDB 脚本生成日志
gdb -batch -x ../../gdb/generate_dpdk_core_log.gdb ./app_test core.* > output.log

# 4. 将 output.log 和 app_test 上传到分析平台
```

## 配合的 ELF 可执行文件

| 日志文件 | 可配合的 ELF | 说明 |
|---------|-------------|------|
| test_nullptr.log | `../app_bad_func` 或 `../crash_app` | 地址不精确匹配但可测试流程 |
| test_fpe.log | `../app_fpe` (需编译) | 需要在 Linux 上生成 |
| test_dpdk_crash.log | (无) | DPDK 风格, 建议 Linux 上生成真实数据 |
