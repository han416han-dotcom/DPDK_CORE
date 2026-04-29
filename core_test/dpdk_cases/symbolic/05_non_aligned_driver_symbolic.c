#include <stdint.h>
#include <string.h>

__attribute__((noinline))
static uint16_t mlx5_rx_burst(void *queue, void *opaque) {
    char src[64] = "simulate an unaligned PMD copy path";
    (void)queue;
    (void)opaque;
    memcpy((void *)0x1, src, sizeof(src));
    return 0;
}

int main(void) {
    return mlx5_rx_burst(NULL, NULL);
}
