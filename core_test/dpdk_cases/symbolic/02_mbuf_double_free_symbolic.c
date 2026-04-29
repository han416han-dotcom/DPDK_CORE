#include <stdio.h>
#include <stdlib.h>

struct fake_mbuf {
    int freed;
    char payload[128];
};

__attribute__((noinline))
static void rte_mbuf_sanity_check(struct fake_mbuf *m, int is_header) {
    (void)is_header;
    if (m->freed) {
        abort();
    }
}

__attribute__((noinline))
static void rte_pktmbuf_free(struct fake_mbuf *m) {
    rte_mbuf_sanity_check(m, 1);
    m->freed = 1;
}

__attribute__((noinline))
static void free_packet(struct fake_mbuf *m) {
    rte_pktmbuf_free(m);
    rte_pktmbuf_free(m);
}

int main(void) {
    struct fake_mbuf *m = calloc(1, sizeof(*m));
    if (m == NULL) {
        return 1;
    }

    free_packet(m);
    return 0;
}
