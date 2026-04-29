#include <stdio.h>
#include <string.h>

#include <rte_eal.h>

__attribute__((noinline))
static void build_packet_copy(void) {
    char src[64] = "copy path used to exercise the buffer overflow classifier";
    memcpy((void *)0x1, src, sizeof(src));
}

int main(int argc, char **argv) {
    int ret = rte_eal_init(argc, argv);
    if (ret < 0) {
        fprintf(stderr, "rte_eal_init failed\n");
        return 1;
    }

    build_packet_copy();
    return 0;
}
