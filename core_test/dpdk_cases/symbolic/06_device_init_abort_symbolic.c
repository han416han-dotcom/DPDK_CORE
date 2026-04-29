#include <stdint.h>
#include <stdlib.h>

__attribute__((noinline))
static int rte_eth_dev_configure(int port_id,
                                 uint16_t nb_rx_queue,
                                 uint16_t nb_tx_queue,
                                 const void *dev_conf) {
    (void)nb_rx_queue;
    (void)nb_tx_queue;
    (void)dev_conf;

    if (port_id < 0) {
        abort();
    }

    return 0;
}

__attribute__((noinline))
static int bring_up_port(void) {
    return rte_eth_dev_configure(-1, 1, 1, NULL);
}

int main(void) {
    return bring_up_port();
}
