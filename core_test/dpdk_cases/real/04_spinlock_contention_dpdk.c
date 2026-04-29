#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <unistd.h>

#include <rte_eal.h>
#include <rte_spinlock.h>

static rte_spinlock_t g_lock = RTE_SPINLOCK_INITIALIZER;
static volatile int g_lock_is_held = 0;

__attribute__((noinline))
static void *holder_thread(void *arg) {
    (void)arg;
    rte_spinlock_lock(&g_lock);
    g_lock_is_held = 1;
    sleep(30);
    rte_spinlock_unlock(&g_lock);
    return NULL;
}

__attribute__((noinline))
static void *waiter_thread(void *arg) {
    (void)arg;
    while (!g_lock_is_held) {
        usleep(1000);
    }

    rte_spinlock_lock(&g_lock);
    rte_spinlock_unlock(&g_lock);
    return NULL;
}

__attribute__((noinline))
static void *watchdog_thread(void *arg) {
    (void)arg;
    sleep(2);
    raise(SIGQUIT);
    return NULL;
}

int main(int argc, char **argv) {
    pthread_t holder;
    pthread_t waiters[4];
    pthread_t watchdog;
    int ret;
    size_t i;

    ret = rte_eal_init(argc, argv);
    if (ret < 0) {
        fprintf(stderr, "rte_eal_init failed\n");
        return 1;
    }

    if (pthread_create(&holder, NULL, holder_thread, NULL) != 0) {
        perror("pthread_create holder");
        return 1;
    }

    for (i = 0; i < 4; ++i) {
        if (pthread_create(&waiters[i], NULL, waiter_thread, NULL) != 0) {
            perror("pthread_create waiter");
            return 1;
        }
    }

    if (pthread_create(&watchdog, NULL, watchdog_thread, NULL) != 0) {
        perror("pthread_create watchdog");
        return 1;
    }

    pthread_join(watchdog, NULL);
    return 0;
}
