#include <stdint.h>

struct fake_mbuf {
    uint16_t data_len;
    uint16_t pkt_len;
};

__attribute__((noinline))
static uint16_t ixgbe_recv_pkts(void *rxq, struct fake_mbuf **rx_pkts, uint16_t nb_pkts) {
    volatile uint32_t *bad = NULL;
    (void)rxq;
    (void)rx_pkts;
    (void)nb_pkts;
    return (uint16_t)(*bad);
}

__attribute__((noinline))
static int rx_burst_loop(void) {
    struct fake_mbuf *pkts[32] = {0};
    return ixgbe_recv_pkts(NULL, pkts, 32);
}

int main(void) {
    return rx_burst_loop();
}
