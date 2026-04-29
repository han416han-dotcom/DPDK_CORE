#include <iostream>

// 递归函数
void cause_stack_overflow(int depth) {
    // 每次调用都在栈上分配 4KB 的大数组，加速栈溢出
    int large_array[1024]; 
    large_array[0] = depth;
    
    // 致命错误：没有退出条件，无限调用自己
    cause_stack_overflow(depth + 1); 
}

int main() {
    std::cout << "[测试 3] 准备制造栈溢出异常 (SIGSEGV)..." << std::endl;
    cause_stack_overflow(1);
    return 0;
}
