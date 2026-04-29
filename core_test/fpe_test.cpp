#include <iostream>

void cause_divide_by_zero() {
    std::cout << "[测试 1] 准备制造除零异常 (SIGFPE)..." << std::endl;
    // 使用 volatile 关键字，防止编译器过度聪明把这行必错代码优化掉
    volatile int a = 10;
    volatile int b = 0;
    
    // 致命错误：除数为 0
    int c = a / b; 
    
    std::cout << "结果: " << c << std::endl;
}

int main() {
    cause_divide_by_zero();
    return 0;
}
