#include <iostream>

void cause_out_of_bounds() {
    std::cout << "[测试 4] 准备制造数组越界写异常 (SIGSEGV)..." << std::endl;
    
    int arr[5] = {1, 2, 3, 4, 5}; // 只有5个元素的数组
    
    // 致命错误：强行向后写入 10000 个数据，严重破坏内存
    for(int i = 0; i <= 10000; ++i) { 
        arr[i] = 999;
    }
}

int main() {
    cause_out_of_bounds();
    return 0;
}
