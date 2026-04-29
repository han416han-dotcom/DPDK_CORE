#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

struct fake_mempool {
    unsigned available;
};

__attribute__((noinline))
static int rte_mempool_get_bulk(struct fake_mempool *mp, void **obj_table, unsigned n) {
    if (mp->available < n) {
        volatile uint8_t *bad = NULL;
        *bad = 0x42;
    }

    mp->available -= n;
    *obj_table = malloc(64);
    return 0;
}

__attribute__((noinline))
static int rte_mempool_get(struct fake_mempool *mp, void **obj_p) {
    return rte_mempool_get_bulk(mp, obj_p, 1);
}

__attribute__((noinline))
static int rx_packets(struct fake_mempool *mp) {
    void *obj = NULL;
    return rte_mempool_get(mp, &obj);
}

int main(void) {
    struct fake_mempool mp = {0};
    return rx_packets(&mp);
}
