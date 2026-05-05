# 自动扫描设计与 Linux 操作说明

## 1. 当前目标

自动扫描模式现在只保留这一条链路：

- 固定扫描目录：`/home/hnhyh/workspace/core`
- 只扫描 `core` 文件
- 自动查找对应 ELF
- 自动创建 `CORE_DUMP + EXECUTABLE` 解析任务

不再支持：

- 自动扫描 `GDB_LOG`
- 自动用扫描到的日志文件创建任务
- 自动从日志头部提取 ELF 路径

手动上传模式仍然保留日志上传能力，这次变化只影响“自动扫描/自动捕获”模式。

## 2. 现在的自动扫描逻辑

### 扫描范围

后端会递归扫描：

```text
/home/hnhyh/workspace/core
```

只识别以下 source：

- 扩展名是 `.core`
- 扩展名是 `.dump`
- 文件名以 `core` 开头
- 或者文件本身 ELF 类型是 `ET_CORE`

### ELF 搜索范围

后端会递归搜索这些目录中的 ELF：

```text
/home/hnhyh/workspace/core
/home/hnhyh/workspace/core/elf
/home/hnhyh/workspace/core/bin
/home/hnhyh/workspace/core/build
/home/hnhyh/workspace/core/target
```

也就是说，ELF 可以放在多级子目录中。

### 匹配规则

现在自动匹配只靠：

1. core 文件名归一化
2. ELF 文件名归一化
3. 是否在同目录

打分规则：

- 归一化名称完全相等：高分
- 前缀匹配：中分
- 包含匹配：低一些
- 同目录：额外加分

所以你后续要想让自动匹配稳定，最重要的是命名规则。

## 3. 推荐目录结构

推荐采用下面这种结构：

```text
/home/hnhyh/workspace/core/
├─ cores/
│  ├─ test_app__12345__1714380000.core
│  ├─ dpdk_ring__22345__1714381000.core
├─ elf/
│  ├─ test_app/
│  │  └─ test_app
│  ├─ dpdk_ring/
│  │  └─ dpdk_ring
```

或者按案例分目录：

```text
/home/hnhyh/workspace/core/
├─ case-001/
│  ├─ test_app__12345__1714380000.core
│  └─ test_app
├─ case-002/
│  ├─ dpdk_ring__22345__1714381000.core
│  └─ dpdk_ring
```

第二种情况下，因为 core 和 ELF 在同目录，自动匹配成功率会更高。

## 4. 推荐命名规则

为了让自动扫描更稳定，建议：

### ELF

```text
test_app
dpdk_ring
mbuf_double_free
```

### Core

```text
test_app__12345__1714380000.core
dpdk_ring__22345__1714381000.core
mbuf_double_free__32345__1714382000.core
```

建议规则：

1. 第一段始终是 ELF basename
2. 第二段可以是 pid
3. 第三段可以是时间戳

这样归一化后，扫描器最容易把 core 和 ELF 对上。

## 5. Linux 上如何固定把 core 生成到指定目录

### 第一步：准备目录

```bash
mkdir -p /home/hnhyh/workspace/core/{cores,elf}
```

### 第二步：允许生成 core

```bash
ulimit -c unlimited
```

如果希望 core 小一点，仍建议：

```bash
echo 0x1 > /proc/self/coredump_filter
```

### 第三步：设置 core 输出目录和命名

```bash
sudo sysctl -w kernel.core_pattern=/home/hnhyh/workspace/core/cores/%e__%p__%t.core
```

含义：

- `%e`：程序名
- `%p`：进程 pid
- `%t`：unix 时间戳

这样产生的 core 会长成：

```text
/home/hnhyh/workspace/core/cores/test_app__12345__1714380000.core
```

### 第四步：把 ELF 放入可扫描目录

例如：

```bash
mkdir -p /home/hnhyh/workspace/core/elf/test_app
cp ./test_app /home/hnhyh/workspace/core/elf/test_app/test_app
```

## 6. 最推荐的 Linux 落地约定

我建议你统一成：

### 目录

```text
/home/hnhyh/workspace/core/
├─ cores/
├─ elf/
```

### core_pattern

```bash
sudo sysctl -w kernel.core_pattern=/home/hnhyh/workspace/core/cores/%e__%p__%t.core
```

### ELF 命名

```text
/home/hnhyh/workspace/core/elf/<app>/<app>
```

### Core 命名

```text
/home/hnhyh/workspace/core/cores/%e__%p__%t.core
```

这是当前自动扫描模式下最稳定的一套。

## 7. 什么时候仍然需要人工指定 ELF

以下情况仍建议你手动指定：

1. core 文件名和 ELF 完全不相关
2. 同一目录下存在多个近似同名 ELF
3. ELF 放在扫描根之外
4. 你保留了多个变体 ELF，例如 strip / 非 strip，名字又很像

## 8. 当前实现边界

当前自动扫描已经支持：

- 固定目录递归扫描 core
- 多级目录递归搜索 ELF
- 本地文件直接注册而不复制
- 自动创建 `CORE_DUMP + EXECUTABLE` 任务

当前没有做：

- 自动扫描日志
- 从日志提取 ELF 路径
- `build-id` 反查 ELF
- inotify 持续监听

如果下一步还要继续做，我建议优先级是：

1. 支持前端手动为未匹配 core 选择 ELF
2. 再做目录监听
3. 最后做真正的“文件一落地就自动建任务”
