#include <iostream>
#include <cstdlib>

void cause_double_free() {
    std::cout << "[测试 2] 准备制造 Double Free 异常 (SIGABRT)..." << std::endl;
    
    // 申请一块内存
    int* ptr = (int*)malloc(sizeof(int));
    *ptr = 42;
    
    // 第一次释放（合法）
    free(ptr);
    
    // 致命错误：指针没清空，导致同一块内存被二次释放
    free(ptr); 
}

int main() {
    cause_double_free();
    return 0;
}
