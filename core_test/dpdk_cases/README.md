# DPDK Crash Classification Fixtures

This directory contains two groups of C fixtures for the current analyzer implementation.

- `real/`: uses real DPDK APIs and is suitable for generating authentic DPDK core files.
- `symbolic/`: uses plain C with DPDK-like function names to make specific classifier branches easier to hit without depending on a NIC, hugepages, or a full DPDK runtime.

## 1. What the project currently classifies

The current implementation is driven by `src/main/java/com/dpdk/core/parser/CrashPatternAnalyzer.java`.

| crashTypeId | Label | Implemented subPattern | Notes |
|---|---|---|---|
| `NULL_POINTER` | 空指针解引用 | - | Triggered directly when `fault_address == 0x0`. |
| `MEMPOOL_CORRUPTION` | 内存池操作异常 | `memory_stomp`, `double_free`, `mbuf_free_crash`, `pool_exhaustion` | Covers mempool/ring/mbuf paths and poisoned addresses like `0xdeadbeef`. |
| `DRIVER_CONFLICT` | 驱动适配冲突 | `pmd_crash`, `non_aligned` | The generic driver fallback exists, but the code does not currently persist a `device_init` subPattern. |
| `THREAD_CONTENTION` | 多核线程资源竞争 | `spinlock_contention`, `ring_misuse`, `race_condition` | Requires multiple thread stacks to show lock/ring contention. |
| `BUFFER_OVERFLOW` | 缓冲区溢出 | - | Usually matched by copy functions such as `memcpy`. |
| `ASSERTION_FAILURE` | 断言失败 | - | Triggered by `rte_panic`, `RTE_VERIFY`, `__assert_fail`, and similar abort chains. |
| `USE_AFTER_FREE` | 释放后使用 | `double_free`, `use_after_free` | Present in code, but generic double-free often falls into other abort paths first. |
| `SIGNAL_ABORT` | 主动终止 | - | Generic abort fallback when no stronger pattern matches. |
| `ARITHMETIC_ERROR` | 算术异常 | - | `SIGFPE` fallback. |
| `BUS_ERROR` | 总线错误 / 非法指令 | - | `SIGBUS` and `SIGILL` fallbacks both use this id. |
| `UNKNOWN` | 未知错误 | - | Final fallback. |

## 2. Fixture map

### `real/`

| File | Expected classification | Notes |
|---|---|---|
| `01_ring_null_ptr_dpdk.c` | `NULL_POINTER` | Real DPDK EAL startup, then passes `NULL` into `rte_ring_dequeue`. |
| `02_assertion_failure_dpdk.c` | `ASSERTION_FAILURE` | Uses `RTE_VERIFY` to force a DPDK abort chain. |
| `03_copy_crash_dpdk.c` | `BUFFER_OVERFLOW` | Starts EAL, then crashes inside `memcpy`. |
| `04_spinlock_contention_dpdk.c` | `THREAD_CONTENTION` / `spinlock_contention` | Generates a multi-thread core with several waiters spinning inside `rte_spinlock_lock`. |
| `05_mbuf_double_free_dpdk.c` | Best effort: `MEMPOOL_CORRUPTION` / `double_free` | Works best on DPDK builds with stronger mbuf sanity checks enabled. |

### `symbolic/`

| File | Expected classification | Why symbolic |
|---|---|---|
| `01_pool_exhaustion_symbolic.c` | `MEMPOOL_CORRUPTION` / `pool_exhaustion` | The current rule depends on seeing mempool frames and a `0x0` fault at the same time. |
| `02_mbuf_double_free_symbolic.c` | `MEMPOOL_CORRUPTION` / `double_free` | Keeps `rte_mbuf_sanity_check` and `rte_pktmbuf_free` in the abort stack. |
| `03_memory_stomp_symbolic.c` | `MEMPOOL_CORRUPTION` / `memory_stomp` | Uses a poisoned `0xdeadbeef` fault address. |
| `04_pmd_crash_symbolic.c` | `DRIVER_CONFLICT` / `pmd_crash` | Real PMD crashes need specific NICs and drivers. |
| `05_non_aligned_driver_symbolic.c` | `DRIVER_CONFLICT` / `non_aligned` | Keeps `memcpy` and a PMD-like function in the same stack. |
| `06_device_init_abort_symbolic.c` | generic `DRIVER_CONFLICT` | Exercises the driver-family fallback without needing real hardware. |
| `07_arithmetic_error_symbolic.c` | `ARITHMETIC_ERROR` | Simple `SIGFPE` fallback sample. |

## 3. Important implementation caveats

- `device_init` is mentioned in `AGENTS.md`, but the current Java implementation does not write `subPattern=device_init`.
- `ring_misuse` is harder to reproduce from a live core because the current logic requires at least two thread stacks that show both lock activity and ring activity.
- Generic double-free outside the DPDK mbuf path may not always land in `USE_AFTER_FREE`; depending on the stack, it can fall through to `SIGNAL_ABORT`.

## 4. Linux VM steps

To keep the generated core small and still preserve the most useful stack and variable data, do both of the following before running a fixture.

Please run these steps in your Linux VM terminal.

### Step 1: Enable core dumps for the current shell

```bash
ulimit -c unlimited
```

### Step 2: Reduce the core dump scope

Linux uses `/proc/self/coredump_filter` to decide which memory mappings should be dumped. Use `0x1` so the dump focuses on private anonymous memory instead of pulling in large shared or hugepage regions.

```bash
echo 0x1 > /proc/self/coredump_filter
```

This change only affects the current shell session.

### Step 3: Build a fixture

From the repository root:

#### 3A. Build a symbolic fixture

```bash
gcc -g -O0 -fno-omit-frame-pointer -pthread \
  core_test/dpdk_cases/symbolic/04_pmd_crash_symbolic.c \
  -o core_test/dpdk_cases/symbolic/04_pmd_crash_symbolic
```

#### 3B. Build a real DPDK fixture

Make sure `pkg-config --libs libdpdk` works first. Then compile:

```bash
cc -g -O0 -fno-omit-frame-pointer \
  $(pkg-config --cflags libdpdk) \
  core_test/dpdk_cases/real/01_ring_null_ptr_dpdk.c \
  -o core_test/dpdk_cases/real/01_ring_null_ptr_dpdk \
  $(pkg-config --libs libdpdk) \
  -pthread
```

If your VM does not provide `libdpdk` through `pkg-config`, point the command at your local DPDK build instead.

### Step 4: Run the fixture

#### 4A. Symbolic fixture

```bash
./core_test/dpdk_cases/symbolic/04_pmd_crash_symbolic
```

#### 4B. Real DPDK fixture with minimal EAL footprint

```bash
./core_test/dpdk_cases/real/01_ring_null_ptr_dpdk -l 0 --no-huge -m 32
```

`-l 0` keeps the process on a single lcore, `--no-huge` avoids hugepages, and `-m 32` is usually enough for these small crash reproducers.

### Step 5: Export a GDB log that matches the analyzer pipeline

After the crash, run:

```bash
gdb -batch \
  -x gdb/generate_dpdk_core_log.gdb \
  ./core_test/dpdk_cases/real/01_ring_null_ptr_dpdk \
  core.* > core_test/dpdk_cases/01_ring_null_ptr_dpdk.log
```

For symbolic fixtures, replace the executable path with the symbolic binary you ran.

### Step 6: Upload to the platform

Upload both files below to the analyzer UI:

- the generated `*.log`
- the matching executable binary

## 5. Suggested run order

If you want the fastest path to verify the analyzer branches, use this order:

1. `symbolic/01_pool_exhaustion_symbolic.c`
2. `symbolic/02_mbuf_double_free_symbolic.c`
3. `symbolic/04_pmd_crash_symbolic.c`
4. `symbolic/05_non_aligned_driver_symbolic.c`
5. `real/04_spinlock_contention_dpdk.c`

If you want the most authentic DPDK core files, start with:

1. `real/01_ring_null_ptr_dpdk.c`
2. `real/02_assertion_failure_dpdk.c`
3. `real/03_copy_crash_dpdk.c`
4. `real/04_spinlock_contention_dpdk.c`
