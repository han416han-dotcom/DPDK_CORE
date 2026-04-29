#!/usr/bin/env python3
"""
DPDK Core Analyzer 测试数据生成器
生成标准化 GDB 日志文件，覆盖各种 crash 场景。

Dependency:
  pip install pyelftools   # (可选) 从真实 ELF 提取符号地址

用法:
  python generate_test_logs.py                        # 生成模拟数据
  python generate_test_logs.py ./app_double_free       # 用真实 ELF 地址
"""

import os
import sys
import json
import random
import subprocess

OUTPUT_DIR = os.path.dirname(os.path.abspath(__file__))


def section(name, body):
    """生成带 SECTION 标记的段落"""
    lines = [
        f'═══ SECTION:{name} ═══',
        body.strip(),
        f'═══ END_SECTION:{name} ═══',
    ]
    return '\n'.join(lines) + '\n'


def log_header():
    return (
        '═══ DPDK_CORE_ANALYZER_VERSION:1.0 ═══\n'
        '═══ GENERATED_AT:2026-04-28T15:00:00+0800\n'
        '═══ END_GENERATED_AT ═══\n'
    )


# ============================================================
# 测试用例 1: 空指针解引用 (SIGSEGV)
# ============================================================
def gen_nullptr_log() -> str:
    thread_info = '''\
* 1    Thread 0x7f8a12345640 (LWP 31234)  main () at crash_test.c:9
  2    Thread 0x7f8a22345640 (LWP 31235)  worker_thread () at worker.c:42
'''
    bt = '''\
Thread 2 (Thread 0x7f8a22345640 (LWP 31235)):
#0  0x00007f8a34567890 in pthread_cond_wait () from /lib64/libpthread.so.0
#1  0x0000556789012345 in worker_thread (arg=0x556789012300) at worker.c:42

Thread 1 (Thread 0x7f8a12345640 (LWP 31234)):
#0  0x00005567890123ab in cause_crash (ptr=0x0) at crash_test.c:6
#1  0x00005567890123cd in main () at crash_test.c:10
'''
    registers = '''\
rax            0x0      0
rbx            0x556789012300  93824992250112
rcx            0x0      0
rdx            0x0      0
rsi            0x0      0
rdi            0x0      0
rbp            0x7fff12345678  0x7fff12345678
rsp            0x7fff12345670  0x7fff12345670
rip            0x5567890123ab  0x5567890123ab <cause_crash+4>
'''
    crash_info = '''\
$1 = {si_signo = 11, si_errno = 0, si_code = 1, si_addr = 0x0}
'''
    memory = '''\
0x556789000000-0x556789001000  r-xp   /home/user/crash_test
0x7f8a12345000-0x7f8a34567000  r-xp   /lib64/libc.so.6
'''
    return (
        log_header()
        + section('THREAD_INFO', thread_info)
        + section('ALL_THREAD_BT', bt)
        + section('REGISTERS', registers)
        + section('CRASH_INFO', crash_info)
        + section('MEMORY_MAPPING', memory)
        + '═══ DPDK_CORE_ANALYZER_EOF ═══\n'
    )


# ============================================================
# 测试用例 2: 除零异常 (SIGFPE)
# ============================================================
def gen_fpe_log() -> str:
    bt = '''\
Thread 1 (Thread 0x7f8b12345640 (LWP 31300)):
#0  0x00007f8b45678901 in __GI_raise (sig=sig@entry=8) at ../sysdeps/unix/sysv/linux/raise.c:50
#1  0x00007f8b45678a02 in __GI_abort () at ../sysdeps/unix/sysv/linux/abort.c:79
#2  0x00007f8b45678b03 in __libc_message (action=do_abort, fmt=fmt@entry=0x...) at ../sysdeps/unix/sysv/linux/libc_fatal.c:155
#3  0x00007f8b45678c04 in __GI___assert_fail (assertion=0x..., file=0x..., line=42, function=0x...) at assert.c:92
#4  0x0000556789abcd01 in cause_divide_by_zero (a=10, b=0) at fpe_test.cpp:10
#5  0x0000556789abcd50 in main () at fpe_test.cpp:17
'''
    return (
        log_header()
        + section('THREAD_INFO', '* 1    Thread 0x7f8b12345640 (LWP 31300)  main ()\n')
        + section('ALL_THREAD_BT', bt)
        + section('REGISTERS', 'rax            0x6      6\n')
        + section('CRASH_INFO', '$1 = {si_signo = 8, si_errno = 0, si_code = 1, si_addr = 0x0}\n')
        + section('MEMORY_MAPPING', '')
        + '═══ DPDK_CORE_ANALYZER_EOF ═══\n'
    )


# ============================================================
# 测试用例 3: Double Free (SIGABRT)
# ============================================================
def gen_double_free_log() -> str:
    bt = '''\
Thread 1 (Thread 0x7f8c12345640 (LWP 21101)):
#0  0x00007f8c567890ab in __GI_raise (sig=sig@entry=6) at ../sysdeps/unix/sysv/linux/raise.c:50
#1  0x00007f8c567891bc in __GI_abort () at ../sysdeps/unix/sysv/linux/abort.c:79
#2  0x00007f8c567892cd in __libc_message (action=do_abort) at ../sysdeps/unix/sysv/linux/libc_fatal.c:155
#3  0x00007f8c567893de in malloc_printerr (str=0x...) at malloc.c:5344
#4  0x00007f8c567894ef in _int_free (have_lock=0, p=0x..., av=0x...) at malloc.c:4175
#5  0x0000556789bcde01 in cause_double_free () at double_free_test.cpp:15
#6  0x0000556789bcde50 in main () at double_free_test.cpp:21
'''
    return (
        log_header()
        + section('THREAD_INFO', '* 1    Thread 0x7f8c12345640 (LWP 21101)  main ()\n')
        + section('ALL_THREAD_BT', bt)
        + section('REGISTERS', 'rax            0x0      0\nrbx            0x556789bcde00  93824992250112\n')
        + section('CRASH_INFO', '$1 = {si_signo = 6, si_errno = 0, si_code = -6, si_addr = 0x556789bcde00}\n')
        + section('MEMORY_MAPPING', '')
        + '═══ DPDK_CORE_ANALYZER_EOF ═══\n'
    )


# ============================================================
# 测试用例 4: 栈溢出 (SIGSEGV)
# ============================================================
def gen_stack_overflow_log() -> str:
    # 深度递归: 模拟 100+ 帧
    frames = []
    for i in range(100):
        frames.append(f'#{i}  0x0000556789cdef{i:02x} in cause_stack_overflow (depth={200-i}) at stack_overflow_test.cpp:10')
    bt = 'Thread 1 (Thread 0x7f8d12345640 (LWP 21102)):\n' + '\n'.join(frames[:20]) + '\n'
    bt += f'#20 0x0000556789cdef20 in cause_stack_overflow (depth=180) at stack_overflow_test.cpp:10\n'
    bt += 'Backtrace stopped: previous frame identical to this frame (corrupt stack?)\n'
    return (
        log_header()
        + section('THREAD_INFO', '* 1    Thread 0x7f8d12345640 (LWP 21102)  main ()\n')
        + section('ALL_THREAD_BT', bt)
        + section('REGISTERS', 'rsp            0x7fff12345000  0x7fff12345000\nrip            0x556789cdef05  0x556789cdef05\n')
        + section('CRASH_INFO', '$1 = {si_signo = 11, si_errno = 0, si_code = 1, si_addr = 0x7fff12344000}\n')
        + section('MEMORY_MAPPING', '')
        + '═══ DPDK_CORE_ANALYZER_EOF ═══\n'
    )


# ============================================================
# 测试用例 5: 数组越界 (SIGSEGV)
# ============================================================
def gen_oob_log() -> str:
    bt = '''\
Thread 1 (Thread 0x7f8e12345640 (LWP 21103)):
#0  0x00007f8e678901ab in __GI_raise (sig=sig@entry=11) at ../sysdeps/unix/sysv/linux/raise.c:50
#1  0x00007f8e678902bc in __GI_abort () at ../sysdeps/unix/sysv/linux/abort.c:79
#2  0x00007f8e678903cd in __libc_message () at ../sysdeps/unix/sysv/linux/libc_fatal.c:155
#3  0x00007f8e678904de in malloc_printerr (str=0x...) at malloc.c:5344
#4  0x00007f8e678905ef in _int_malloc (av=0x..., bytes=...) at malloc.c:4175
#5  0x0000556789def001 in cause_out_of_bounds () at out_of_bounds_test.cpp:9
#6  0x0000556789def050 in main () at out_of_bounds_test.cpp:15
'''
    return (
        log_header()
        + section('THREAD_INFO', '* 1    Thread 0x7f8e12345640 (LWP 21103)  main ()\n')
        + section('ALL_THREAD_BT', bt)
        + section('REGISTERS', '')
        + section('CRASH_INFO', '')
        + section('MEMORY_MAPPING', '')
        + '═══ DPDK_CORE_ANALYZER_EOF ═══\n'
    )


# ============================================================
# 测试用例 6: DPDK lcore 多线程崩溃 (最重要的测试)
# 模拟 DPDK 应用典型崩溃: rte_ring_dequeue 空指针
# ============================================================
def gen_dpdk_crash_log() -> str:
    thread_info = '''\
* 1    Thread 0x7f0012340000 (LWP 41001)  lcore-worker-1 () at main.c:120
  2    Thread 0x7f0012340100 (LWP 41002)  lcore-worker-2 () at main.c:120
  3    Thread 0x7f0012340200 (LWP 41003)  lcore-worker-3 () at main.c:120
  4    Thread 0x7f0012340300 (LWP 41004)  lcore-slave-1 () at slave.c:55
  5    Thread 0x7f0012340400 (LWP 41005)  main () at main.c:200
'''
    bt = '''\
Thread 5 (Thread 0x7f0012340400 (LWP 41005)):
#0  0x00007f0056789012 in __GI_epoll_wait () at ../sysdeps/unix/sysv/linux/epoll_wait.c:30
#1  0x00007f005678a023 in rte_epoll_wait (epfd=5, events=0x...) at /dpdk/lib/eal/linux/eal_epoll.c:152
#2  0x00007f005678b034 in eal_intr_handle_interrupts () at /dpdk/lib/eal/linux/eal_interrupt.c:345
#3  0x00007f005678c045 in eal_intr_thread_main () at /dpdk/lib/eal/linux/eal_interrupt.c:456
#4  0x00007f005678d056 in control_thread_start () at /dpdk/lib/eal/linux/eal_thread.c:89

Thread 4 (Thread 0x7f0012340300 (LWP 41004)):
#0  0x00007f0056789012 in __GI_epoll_wait () at ../sysdeps/unix/sysv/linux/epoll_wait.c:30
#1  0x00007f005678a023 in rte_epoll_wait () at eal_epoll.c:152

Thread 3 (Thread 0x7f0012340200 (LWP 41003)):
#0  0x0000000000456789 in rte_ring_dequeue_bulk (r=0x0, obj_table=0x7f0012340300, n=32, available=0x0) at /dpdk/lib/librte_ring/rte_ring.c:456
#1  0x00000000004578ab in rte_ring_dequeue (r=0x0, obj_p=0x7f0012340300) at /dpdk/lib/librte_ring/rte_ring.h:234
#2  0x00000000004589cd in process_packets (lcore_id=2, ring=0x0) at dpdk_app/pipeline.c:120
#3  0x0000000000459aef in lcore_worker_loop (arg=0x2) at dpdk_app/main.c:120
#4  0x000000000045abcd in eal_thread_loop (arg=0x...) at /dpdk/lib/eal/linux/eal_thread.c:156

Thread 2 (Thread 0x7f0012340100 (LWP 41002)):
#0  0x0000000000456789 in rte_mempool_get_ops (mp=0x7f0012340500, obj_table=0x..., n=32) at /dpdk/lib/librte_mempool/rte_mempool.c:345
#1  0x00000000004568ab in rte_mempool_get_bulk (mp=0x..., obj_table=0x..., n=32) at /dpdk/lib/librte_mempool/rte_mempool.h:120
#2  0x00000000004569cd in rte_mbuf_alloc (mp=0x7f0012340500) at /dpdk/lib/librte_mbuf/rte_mbuf.c:234
#3  0x0000000000458aef in rx_packets (lcore_id=1, ring=0x7f0012340600) at dpdk_app/pipeline.c:89
#4  0x0000000000459aef in lcore_worker_loop (arg=0x1) at dpdk_app/main.c:120
#5  0x000000000045abcd in eal_thread_loop (arg=0x...) at /dpdk/lib/eal/linux/eal_thread.c:156

Thread 1 (Thread 0x7f0012340000 (LWP 41001)):
#0  0x0000000000456789 in rte_panic (funcname=0x...) at /dpdk/lib/eal/common/rte_panic.c:42
#1  0x00000000004578ab in __rte_panic (funcname=0x..., format=0x...) at /dpdk/lib/eal/common/rte_panic.c:56
#2  0x00000000004589cd in rte_ring_dequeue_bulk (r=0x0, obj_table=0x..., n=32, available=0x0) at /dpdk/lib/librte_ring/rte_ring.c:460
#3  0x0000000000459aef in rte_ring_dequeue (r=0x0, obj_p=0x...) at /dpdk/lib/librte_ring/rte_ring.h:234
#4  0x000000000045abcd in process_packets (lcore_id=1, ring=0x0) at dpdk_app/pipeline.c:120
#5  0x000000000045bcde in lcore_worker_loop (arg=0x1) at dpdk_app/main.c:120
#6  0x000000000045cdef in eal_thread_loop (arg=0x...) at /dpdk/lib/eal/linux/eal_thread.c:156
'''
    registers = '''\
rax            0x0      0
rbx            0x0      0
rcx            0x0      0
rdx            0x20     32
rsi            0x7f0012340300  13977777777792
rdi            0x0      0
rip            0x456789 0x456789 <rte_ring_dequeue_bulk+16>
'''
    crash_info = '''\
$1 = {si_signo = 11, si_errno = 0, si_code = 1, si_addr = 0x0}
'''
    memory = '''\
0x0000000000400000-0x0000000000460000  r-xp   /home/user/dpdk_app
0x0000000000600000-0x0000000000620000  rw-p   /home/user/dpdk_app
0x7f0012340000-0x7f0012450000  rw-p   [hugepages]
'''
    return (
        log_header()
        + section('THREAD_INFO', thread_info)
        + section('ALL_THREAD_BT', bt)
        + section('REGISTERS', registers)
        + section('CRASH_INFO', crash_info)
        + section('MEMORY_MAPPING', memory)
        + '═══ DPDK_CORE_ANALYZER_EOF ═══\n'
    )


# ============================================================
# 测试用例 7: DPDK mbuf 双释崩溃
# ============================================================
def gen_dpdk_mbuf_double_free_log() -> str:
    bt = '''\
Thread 1 (Thread 0x7f0023450000 (LWP 41010)):
#0  0x0000000000456789 in rte_panic (funcname=0x...) at /dpdk/lib/eal/common/rte_panic.c:42
#1  0x00000000004578ab in __rte_panic (funcname=0x...) at /dpdk/lib/eal/common/rte_panic.c:56
#2  0x00007f0056789234 in rte_mbuf_sanity_check (m=0x..., is_hdr=1) at /dpdk/lib/librte_mbuf/rte_mbuf.c:567
#3  0x00007f0056789345 in __rte_pktmbuf_free_seg (m=0x...) at /dpdk/lib/librte_mbuf/rte_mbuf.c:678
#4  0x00007f0056789456 in rte_pktmbuf_free (m=0x...) at /dpdk/lib/librte_mbuf/rte_mbuf.h:789
#5  0x000000000045bcde in free_packet (pkt=0x...) at dpdk_app/pipeline.c:234
#6  0x000000000045cdef in lcore_worker_loop (arg=0x1) at dpdk_app/main.c:120
#7  0x000000000045def0 in eal_thread_loop (arg=0x...) at /dpdk/lib/eal/linux/eal_thread.c:156
'''
    return (
        log_header()
        + section('THREAD_INFO', '* 1    Thread 0x7f0023450000 (LWP 41010)  lcore-worker-1 ()\n')
        + section('ALL_THREAD_BT', bt)
        + section('REGISTERS', '')
        + section('CRASH_INFO', '$1 = {si_signo = 6, si_errno = 0, si_code = -6, si_addr = 0x7f0023450100}\n')
        + section('MEMORY_MAPPING', '')
        + '═══ DPDK_CORE_ANALYZER_EOF ═══\n'
    )


# ============================================================
# 测试用例 8: DPDK lcore mempool 耗尽 + 多线程堆栈
# ============================================================
def gen_dpdk_mempool_exhausted_log() -> str:
    thread_info = '''\
  1    Thread 0x7f0034560000 (LWP 41020)  lcore-worker-1 ()
  2    Thread 0x7f0034560100 (LWP 41021)  lcore-worker-2 ()
* 3    Thread 0x7f0034560200 (LWP 41022)  lcore-worker-3 ()
  4    Thread 0x7f0034560300 (LWP 41023)  main ()
'''
    bt = '''\
Thread 4 (Thread 0x7f0034560300 (LWP 41023)):
#0  0x00007f0056789012 in __GI_epoll_wait () at epoll_wait.c:30
#1  0x00007f005678a023 in rte_epoll_wait () at eal_epoll.c:152

Thread 3 (Thread 0x7f0034560200 (LWP 41022)):
#0  0x00007f0056789567 in __GI___libc_malloc (bytes=2048) at malloc.c:3123
#1  0x00007f0056789678 in rte_malloc_socket (type=0x..., size=2048, align=64, socket_arg=0) at /dpdk/lib/eal/common/rte_malloc.c:89
#2  0x00007f0056789789 in rte_malloc (type=0x..., size=2048, align=64) at /dpdk/lib/eal/common/rte_malloc.c:120
#3  0x00007f005678989a in rte_mempool_alloc (mp=0x...) at /dpdk/lib/librte_mempool/rte_mempool.c:234
#4  0x000000000045bcde in process_packets (lcore_id=3) at dpdk_app/pipeline.c:156
#5  0x000000000045cdef in lcore_worker_loop (arg=0x3) at dpdk_app/main.c:120

Thread 2 (Thread 0x7f0034560100 (LWP 41021)):
#0  0x0000000000456789 in rte_mempool_get_bulk (mp=0x..., obj_table=0x..., n=32) at rte_mempool.h:120
#1  0x00000000004578ab in rte_mempool_get (mp=0x...) at rte_mempool.h:234
#2  0x00000000004589cd in rx_packets (lcore_id=2) at dpdk_app/pipeline.c:89
#3  0x0000000000459aef in lcore_worker_loop (arg=0x2) at dpdk_app/main.c:120

Thread 1 (Thread 0x7f0034560000 (LWP 41020)):
#0  0x0000000000456789 in rte_panic (funcname=0x...) at rte_panic.c:42
#1  0x00000000004578ab in __rte_panic (funcname=0x..., format=0x...) at rte_panic.c:56
#2  0x00000000004589cd in rte_mempool_get_bulk (mp=0x..., obj_table=0x..., n=32) at rte_mempool.c:345
#3  0x0000000000459aef in rte_mempool_get (mp=0x...) at rte_mempool.h:234
#4  0x000000000045abcd in rx_packets (lcore_id=1) at dpdk_app/pipeline.c:89
#5  0x000000000045bcde in lcore_worker_loop (arg=0x1) at dpdk_app/main.c:120
'''
    return (
        log_header()
        + section('THREAD_INFO', thread_info)
        + section('ALL_THREAD_BT', bt)
        + section('REGISTERS', '')
        + section('CRASH_INFO', '$1 = {si_signo = 6, si_errno = 0, si_code = -6}\n')
        + section('MEMORY_MAPPING', '')
        + '═══ DPDK_CORE_ANALYZER_EOF ═══\n'
    )


# ============================================================
# 主程序
# ============================================================
TEST_CASES = [
    ('test_nullptr.log', gen_nullptr_log, 'NULL pointer deref SIGSEGV'),
    ('test_fpe.log', gen_fpe_log, 'Divide by zero SIGFPE'),
    ('test_double_free.log', gen_double_free_log, 'Double Free SIGABRT'),
    ('test_stack_overflow.log', gen_stack_overflow_log, 'Stack overflow SIGSEGV (100+ frames)'),
    ('test_oob.log', gen_oob_log, 'Array out of bounds SIGSEGV'),
    ('test_dpdk_crash.log', gen_dpdk_crash_log, 'DPDK ring NULL ptr crash (KEY)'),
    ('test_dpdk_mbuf_double_free.log', gen_dpdk_mbuf_double_free_log, 'DPDK mbuf double free (KEY)'),
    ('test_dpdk_mempool_exhausted.log', gen_dpdk_mempool_exhausted_log, 'DPDK mempool exhausted (KEY)'),
]


def main():
    output_dir = os.path.join(OUTPUT_DIR, 'test_data')
    os.makedirs(output_dir, exist_ok=True)

    print(f'生成测试数据到: {output_dir}\n')
    for filename, gen_func, desc in TEST_CASES:
        content = gen_func()
        path = os.path.join(output_dir, filename)
        with open(path, 'w', encoding='utf-8') as f:
            f.write(content)
        lines = content.count('\n')
        print(f'  [OK] {filename:40s} ({lines:3d} lines) - {desc}')

        print(f'\nTotal: {len(TEST_CASES)} test log files')
    print('Upload these files to the analysis platform for testing.')


if __name__ == '__main__':
    main()
