#include <iostream>

// 定义一个全局的函数指针，但故意不给它赋值（默认为空指针）
void (*callback_func)() = nullptr;

void cause_bad_function_call() {
    std::cout << "[测试 5] 准备制造非法函数指针调用异常 (SIGSEGV)..." << std::endl;
    
    // 致命错误：尝试执行一个指向 0x0 内存地址的函数
    // 这在底层开发中常由于回调函数未初始化或被意外篡改导致
    callback_func(); 
}

int main() {
    cause_bad_function_call();
    return 0;
}
