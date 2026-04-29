#include <stdio.h>

#include <rte_eal.h>
#include <rte_lcore.h>
#include <rte_mbuf.h>
#include <rte_mempool.h>

__attribute__((noinline))
static void free_packet_twice(struct rte_mbuf *m) {
    rte_pktmbuf_free(m);
    rte_pktmbuf_free(m);
}

int main(int argc, char **argv) {
    struct rte_mempool *pool;
    struct rte_mbuf *m;
    int ret;

    ret = rte_eal_init(argc, argv);
    if (ret < 0) {
        fprintf(stderr, "rte_eal_init failed\n");
        return 1;
    }

    pool = rte_pktmbuf_pool_create(
        "double_free_pool",
        128,
        0,
        0,
        RTE_MBUF_DEFAULT_BUF_SIZE,
        rte_socket_id()
    );
    if (pool == NULL) {
        fprintf(stderr, "rte_pktmbuf_pool_create failed\n");
        return 1;
    }

    m = rte_pktmbuf_alloc(pool);
    if (m == NULL) {
        fprintf(stderr, "rte_pktmbuf_alloc failed\n");
        return 1;
    }

    free_packet_twice(m);
    return 0;
}
