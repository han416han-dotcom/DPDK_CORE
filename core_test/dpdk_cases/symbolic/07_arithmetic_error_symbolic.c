#include <stdio.h>

__attribute__((noinline))
static int rx_checksum_worker(void) {
    volatile int packets = 1024;
    volatile int divisor = 0;
    return packets / divisor;
}

int main(void) {
    return rx_checksum_worker();
}
