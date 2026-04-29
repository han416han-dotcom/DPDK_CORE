#include <stdint.h>
#include <stdio.h>

#include <rte_debug.h>
#include <rte_eal.h>

__attribute__((noinline))
static void validate_rx_queue(uint16_t nb_desc) {
    RTE_VERIFY(nb_desc >= 128);
}

int main(int argc, char **argv) {
    int ret = rte_eal_init(argc, argv);
    if (ret < 0) {
        fprintf(stderr, "rte_eal_init failed\n");
        return 1;
    }

    validate_rx_queue(0);
    return 0;
}
