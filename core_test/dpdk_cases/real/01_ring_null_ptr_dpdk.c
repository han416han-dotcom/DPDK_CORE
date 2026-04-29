#include <stdio.h>

#include <rte_eal.h>
#include <rte_ring.h>

__attribute__((noinline))
static int process_packets(struct rte_ring *rx_ring) {
    void *obj = NULL;
    return rte_ring_dequeue(rx_ring, &obj);
}

int main(int argc, char **argv) {
    int ret = rte_eal_init(argc, argv);
    if (ret < 0) {
        fprintf(stderr, "rte_eal_init failed\n");
        return 1;
    }

    return process_packets(NULL);
}
