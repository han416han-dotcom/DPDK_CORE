#include <stdint.h>

__attribute__((noinline))
static void recycle_stomped_mbuf(void) {
    volatile uint32_t *poison = (uint32_t *)0xdeadbeef;
    *poison = 0x12345678;
}

int main(void) {
    recycle_stomped_mbuf();
    return 0;
}
