# =====================================================
# DPDK Core Analyzer - 标准化 GDB 日志生成脚本
# 用法: gdb -batch -x generate_dpdk_core_log.gdb ./app core.dump 2>&1 > output.log
# =====================================================

set pagination off
set confirm off
set print elements 0
set print frame-arguments all
set print thread-events off

# ===== 基本信息 =====
echo ═══ DPDK_CORE_ANALYZER_VERSION:1.0 ═══\n
echo ═══ GENERATED_AT:
shell date +%Y-%m-%dT%H:%M:%S%z
echo ═══ END_GENERATED_AT ═══\n

# ===== 1. 进程/线程概览 =====
echo \n═══ SECTION:THREAD_INFO ═══\n
info threads
echo ═══ END_SECTION:THREAD_INFO ═══\n

# ===== 2. 全部线程完整 Backtrace =====
echo \n═══ SECTION:ALL_THREAD_BT ═══\n
set backtrace limit 0
thread apply all bt full
echo ═══ END_SECTION:ALL_THREAD_BT ═══\n

# ===== 3. 崩溃线程详细寄存器 =====
echo \n═══ SECTION:REGISTERS ═══\n
info registers
echo ═══ END_SECTION:REGISTERS ═══\n

# ===== 4. 崩溃信号 =====
echo \n═══ SECTION:CRASH_INFO ═══\n
print $_siginfo
echo \n
echo ═══ END_SECTION:CRASH_INFO ═══\n

# ===== 5. 全局内存信息 =====
echo \n═══ SECTION:MEMORY_MAPPING ═══\n
info proc mappings
echo ═══ END_SECTION:MEMORY_MAPPING ═══\n

echo \n═══ DPDK_CORE_ANALYZER_EOF ═══\n
quit
