#include <stdio.h>

void cause_crash() {
    printf("准备制造内存越界崩溃...\n");
    int *ptr = NULL;
    *ptr = 10; 
}

int main() {
    printf("=== DPDK 模拟崩溃测试启动 ===\n");
    cause_crash();
    printf("这行代码永远不会被执行\n");
    return 0;
}
