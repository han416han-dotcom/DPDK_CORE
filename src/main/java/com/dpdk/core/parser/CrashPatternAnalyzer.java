package com.dpdk.core.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 崩溃模式诊断阶段 — 基于 GDB 三板斧（bt / info threads / frame info locals）
 * 进行智能崩溃根因推断，不依赖“信号=结论”的简单映射。
 *
 * <p>四层检测优先级：
 * <ol>
 *   <li>寄存器级：fault_address / rip 直接推断（与信号无关）</li>
 *   <li>栈顶函数模式匹配：search #0~#5 找触发函数</li>
 *   <li>跨线程栈对比：多线程锁竞争 / 数据竞争检测</li>
 *   <li>信号辅助（fallback）</li>
 * </ol>
 */
@Component
public class CrashPatternAnalyzer implements Parser {

    private static final Logger log = LoggerFactory.getLogger(CrashPatternAnalyzer.class);

    private static final String UNKNOWN_TYPE = "未知错误";
    private static final String UNKNOWN_ID = "UNKNOWN";

    // ---------- 第一层：abort 链函数（#0 不是根因，往下搜） ----------
    private static final Set<String> ABORT_FUNCTIONS = Set.of(
            "__GI_raise", "raise", "__raise",
            "gsignal", "pthread_kill", "tgkill",
            "__GI_abort", "abort", "__libc_abort",
            "__chk_fail", "__fortify_fail"
    );

    // ---------- 第二层：内存池/ring/mbuf 函数 ----------
    private static final Set<String> MEMPOOL_FUNCTIONS = Set.of(
            "rte_mempool_get", "rte_mempool_put",
            "rte_mempool_create", "rte_mempool_free",
            "rte_ring_enqueue", "rte_ring_dequeue",
            "rte_ring_enqueue_bulk", "rte_ring_dequeue_bulk",
            "rte_ring_enqueue_burst", "rte_ring_dequeue_burst",
            "__rte_ring_do_enqueue", "__rte_ring_do_dequeue",
            "rte_pktmbuf_alloc", "rte_pktmbuf_free",
            "rte_pktmbuf_free_bulk",
            "rte_mbuf_sanity_check",
            "rte_pktmbuf_clone", "rte_pktmbuf_copy",
            "rte_mbuf_raw_free", "rte_mbuf_raw_alloc",
            "rte_mempool_default_cache"
    );

    // ---------- 第二层：驱动/PMD 函数 ----------
    private static final Set<String> DRIVER_FUNCTIONS = Set.of(
            "rte_eth_dev_start", "rte_eth_dev_stop",
            "rte_eth_dev_close", "rte_eth_dev_configure",
            "rte_eth_dev_rx_queue_start", "rte_eth_dev_tx_queue_start",
            "rte_eth_rx_burst", "rte_eth_tx_burst",
            "rte_kni_alloc", "rte_kni_release", "rte_kni_handle_request",
            "rte_pmd_init", "rte_pmd_eth_init",
            "rte_eal_init", "rte_eal_cleanup"
    );
    private static final Pattern PMD_FUNC_PATTERN = Pattern.compile(
            "(ixgbe|i40e|mlx[45]|bnxt|cxgbe|ena|virtio|vhost|ice|igb|e1000)" +
            "_(rx|tx|recv|xmit|recv_pkts|send_pkts|recv_raw_pkts|pkts_recv|_burst)"
    );

    // 宽泛模式匹配：ring/mempool/mbuf 家族（覆盖 __rte_ring_headtail_move_head 等内部函数）
    private static final Pattern RING_FAMILY_PATTERN = Pattern.compile(
            "rte_ring_|__rte_ring_|rte_mempool_|rte_pktmbuf_|rte_mbuf_"
    );

    // 宽泛模式匹配：eth/dev/driver 家族（覆盖 eth_stats_qstats_get 等）
    private static final Pattern DRIVER_FAMILY_PATTERN = Pattern.compile(
            "rte_eth_|eth_.*_get|eth_.*_burst|rte_pmd_|rte_kni_|rte_eal_|rte_bus_"
    );

    // ---------- 第二层：锁/竞争函数 ----------
    private static final Set<String> LOCK_FUNCTIONS = Set.of(
            "rte_spinlock_lock", "rte_spinlock_trylock",
            "rte_spinlock_unlock", "rte_spinlock_is_locked",
            "rte_ticketlock_lock", "rte_ticketlock_trylock",
            "_raw_spin_lock", "_raw_spin_lock_irqsave",
            "pthread_mutex_lock", "pthread_mutex_trylock",
            "pthread_rwlock_rdlock", "pthread_rwlock_wrlock"
    );

    // ---------- 第二层：拷贝/溢出函数 ----------
    private static final Set<String> COPY_FUNCTIONS = Set.of(
            "memcpy", "__memcpy_avx_unaligned", "__memcpy_sse2",
            "rte_memcpy", "rte_mov16", "rte_mov32", "rte_mov64",
            "rte_mov128", "rte_mov256",
            "strcpy", "strncpy", "sprintf", "snprintf",
            "memmove", "memset",
            "__memcpy_avx2", "__memmove_avx_unaligned",
            "__memcpy_chk", "__memmove_chk"
    );

    // ---------- 第二层：释放函数 ----------
    private static final Set<String> FREE_FUNCTIONS = Set.of(
            "free", "__libc_free", "rte_free",
            "delete", "operator delete",
            "rte_pktmbuf_free", "rte_pktmbuf_free_bulk",
            "rte_mempool_put", "rte_mempool_put_bulk",
            "rte_mbuf_raw_free"
    );

    // ---------- 第二层：断言函数 ----------
    private static final Set<String> ASSERT_FUNCTIONS = Set.of(
            "__assert_fail", "__assert_fail_base",
            "__assert_perror_fail",
            "rte_panic", "__rte_panic",
            "RTE_VERIFY"
    );

    // 地址异常模式（可能指示栈溢出 / use-after-free / 内存踩踏）
    private static final Set<String> SUSPICIOUS_ADDRESS_PATTERNS = Set.of(
            "deadbeef", "cafebabe", "baadf00d",
            "feedface", "feeefeee",
            "abba", "cccc"
    );

    @Override
    public ParseStage getStage() {
        return ParseStage.CRASH_DIAGNOSE;
    }

    @Override
    public String getName() {
        return "CrashPatternAnalyzer";
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean parse(ParseContext context) {
        List<Map<String, Object>> threads = context.getParsedThreads();
        if (threads == null || threads.isEmpty()) {
            context.addWarning(getName(), "无线程数据可诊断");
            return true;
        }

        Map<String, Object> crashThread = findCrashThread(threads);
        Map<String, Object> crashInfo = context.getCrashInfo();

        // 构建诊断信息
        DiagnosisBuilder diag = new DiagnosisBuilder(context);
        diag.crashThread = crashThread;
        diag.allThreads = threads;

        // ---- 提取基础信息 ----
        diag.extractBasicInfo(crashInfo);

        // ---- 第一层：寄存器级检测（与信号无关） ----
        if (diag.tryRegisterLevel()) {
            diag.finalizeResult(context);
            context.addLog("INFO", getName(),
                    "诊断完成(L1寄存器): " + diag.getCrashType() + " [可信度" + diag.getConfidence() + "]");
            return true;
        }

        // ---- 第二层：栈顶函数 / abort 链搜索 ----
        if (diag.tryFramePatternLevel()) {
            diag.finalizeResult(context);
            context.addLog("INFO", getName(),
                    "诊断完成(L2帧匹配): " + diag.getCrashType() + " [可信度" + diag.getConfidence() + "]");
            return true;
        }

        // ---- 第三层：跨线程竞争检测 ----
        if (diag.tryThreadContentionLevel()) {
            diag.finalizeResult(context);
            context.addLog("INFO", getName(),
                    "诊断完成(L3跨线程): " + diag.getCrashType() + " [可信度" + diag.getConfidence() + "]");
            return true;
        }

        // ---- 第四层：信号辅助 ----
        diag.trySignalFallback();
        diag.finalizeResult(context);
        context.addLog("INFO", getName(),
                "诊断完成(L4信号): " + diag.getCrashType() + " [可信度" + diag.getConfidence() + "]");
        return true;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findCrashThread(List<Map<String, Object>> threads) {
        for (Map<String, Object> t : threads) {
            if (Boolean.TRUE.equals(t.get("crash_thread")))
                return t;
        }
        return threads.isEmpty() ? null : threads.get(0);
    }

    // ================================================================
    // 诊断构造器
    // ================================================================
    static class DiagnosisBuilder {
        final ParseContext context;
        Map<String, Object> crashThread;
        List<Map<String, Object>> allThreads;

        // 基本字段
        String crashType = UNKNOWN_TYPE;
        String crashTypeId = UNKNOWN_ID;
        int confidence = 10;
        String crashFunction = "";
        String sourceLocation = "";
        String signalName = "";
        String faultAddress = "";
        boolean isAbortChain = false;
        String abortSourceFunc = "";
        int abortSourceDepth = -1;

        // 推理字段
        String rootCause = "";
        String suggestion = "";
        List<String> keyFunctions = new ArrayList<>();
        boolean contentionDetected = false;
        List<Map<String, String>> relatedThreads = new ArrayList<>();
        String subPattern = "";
        String subPatternLabel = "";

        DiagnosisBuilder(ParseContext context) {
            this.context = context;
        }

        // ---------- 基础信息提取 ----------
        @SuppressWarnings("unchecked")
        void extractBasicInfo(Map<String, Object> crashInfo) {
            if (crashInfo != null) {
                signalName = (String) crashInfo.getOrDefault("signal_name", "");
                faultAddress = (String) crashInfo.getOrDefault("fault_address", "");
            }
            if (crashThread != null) {
                List<Map<String, Object>> frames = (List<Map<String, Object>>) crashThread.get("frames");
                if (frames != null && !frames.isEmpty()) {
                    Map<String, Object> top = frames.get(0);
                    crashFunction = (String) top.getOrDefault("function_name", "");
                    if (crashFunction == null) crashFunction = "";
                    String src = (String) top.getOrDefault("source_file", "");
                    Integer srcline = top.get("source_line") instanceof Number
                            ? ((Number) top.get("source_line")).intValue() : null;
                    sourceLocation = src != null ? src : "";
                    if (srcline != null) sourceLocation += ":" + srcline;
                }
            }
        }

        // ---------- 第一层：寄存器级检测 ----------
        boolean tryRegisterLevel() {
            // fa=0x0 → 空指针
            if (faultAddress != null && (faultAddress.equals("0x0")
                    || faultAddress.equals("0x0000000000000000"))) {
                setType("NULL_POINTER", "空指针解引用", 95);
                rootCause = "程序在 " + (crashFunction.isEmpty() ? "未知函数" : crashFunction)
                        + " 中解引用了空指针（NULL / 0x0）";
                suggestion = "1. 检查 " + (crashFunction.isEmpty() ? "#0 帧函数" : crashFunction) + " 中的指针参数是否已初始化\n"
                        + "2. 确认相关对象是否已创建成功\n"
                        + "3. 在调用路径中加入 NULL 检查";
                return true;
            }
            // 异常地址模式（0xdeadbeef 等）
            if (faultAddress != null) {
                String lower = faultAddress.toLowerCase();
                for (String suspicious : SUSPICIOUS_ADDRESS_PATTERNS) {
                    if (lower.contains(suspicious)) {
                        setType("MEMPOOL_CORRUPTION", "内存池操作异常", 80);
                        subPattern = "memory_stomp";
                        subPatternLabel = "内存踩踏";
                        rootCause = "故障地址 " + faultAddress
                                + " 是典型的内存标记模式，表明内存已损坏（前序代码越界写 / use-after-free）";
                        suggestion = "1. 检查 " + (crashFunction.isEmpty() ? "崩溃帧" : crashFunction) + " 之前是否有大缓冲区的 memcpy/memmove 操作\n"
                                + "2. 使用 AddressSanitizer (-fsanitize=address) 重编译并重现\n"
                                + "3. 如果涉及 mbuf，检查载荷长度是否超出 pkt_len";
                        return true;
                    }
                }
            }
            return false;
        }

        // ---------- 第二层：帧模式匹配 ----------
        boolean tryFramePatternLevel() {
            if (crashThread == null) return false;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> frames = (List<Map<String, Object>>) crashThread.get("frames");
            if (frames == null || frames.isEmpty()) return false;

            // 展开帧函数名（#0 ~ #5）
            int searchDepth = Math.min(frames.size(), 6);
            List<String> funcStack = new ArrayList<>();
            for (int i = 0; i < searchDepth; i++) {
                String fn = (String) frames.get(i).getOrDefault("function_name", "");
                funcStack.add(fn != null ? fn : "");
            }

            // ---- 检测 abort 链 ----
            if (!funcStack.isEmpty() && isAbortFunc(funcStack.get(0))) {
                isAbortChain = true;
                // 向下搜索真正的触发函数
                String foundFunc = "";
                int foundDepth = -1;
                for (int i = 1; i < funcStack.size(); i++) {
                    String fn = funcStack.get(i);
                    if (fn.isEmpty() || isAbortFunc(fn)) continue;
                    foundFunc = fn;
                    foundDepth = i;
                    break;
                }
                if (foundDepth < 0 && frames.size() > funcStack.size()) {
                    for (int i = funcStack.size(); i < Math.min(frames.size(), 10); i++) {
                        String fn = (String) frames.get(i).getOrDefault("function_name", "");
                        if (fn != null && !fn.isEmpty() && !isAbortFunc(fn)) {
                            foundFunc = fn;
                            foundDepth = i;
                            break;
                        }
                    }
                }
                if (!foundFunc.isEmpty()) {
                    abortSourceFunc = foundFunc;
                    abortSourceDepth = foundDepth;
                    crashFunction = foundFunc;
                }
                // 基于触发函数重新分类
                return tryReclassifyAfterAbort(frames, funcStack, foundFunc);
            }

            // ---- 构建全栈函数集合（用于宽泛匹配） ----
            Set<String> allFuncs = collectAllFuncs(frames);

            // ---- 内存池/ring 异常 ----
            if (containsAny(allFuncs, MEMPOOL_FUNCTIONS) || matchesFamily(allFuncs, RING_FAMILY_PATTERN)) {
                return detectMempoolSubPattern(frames, allFuncs);
            }

            // ---- 驱动/PMD 异常 ----
            if (containsAny(allFuncs, DRIVER_FUNCTIONS) || containsPmdFunc(allFuncs)
                    || matchesFamily(allFuncs, DRIVER_FAMILY_PATTERN)) {
                return detectDriverSubPattern(frames, allFuncs);
            }

            // ---- 缓冲区溢出（拷贝函数） ----
            if (containsAny(allFuncs, COPY_FUNCTIONS)) {
                setType("BUFFER_OVERFLOW", "缓冲区溢出", 65);
                rootCause = "崩溃栈中包含 " + findFirstMatch(allFuncs, COPY_FUNCTIONS)
                        + " 等内存拷贝函数，疑似越界写";
                suggestion = "1. 检查内存拷贝的源/目标缓冲区大小是否匹配\n"
                        + "2. 确认拷贝长度是否超出了已分配内存\n"
                        + "3. 使用 -fsanitize=address 找到溢出点";
                return true;
            }

            // ---- 释放后使用 ----
            if (containsAny(allFuncs, FREE_FUNCTIONS) && !funcStack.get(0).isEmpty()) {
                setType("USE_AFTER_FREE", "释放后使用", 55);
                String freeFunc = findFirstMatch(allFuncs, FREE_FUNCTIONS);
                rootCause = "崩溃栈中包含 " + freeFunc + "，疑似对已释放的内存区域进行操作";
                subPattern = isDoubleFree(funcStack, allFuncs) ? "double_free" : "use_after_free";
                subPatternLabel = subPattern.equals("double_free") ? "重复释放" : "释放后使用";
                suggestion = "1. 在 " + freeFunc + " 前后打印指针值确认是否已被释放\n"
                        + "2. 利用 rte_mbuf_sanity_check 检查 mbuf 合法性\n"
                        + "3. 排查是否存在两条释放路径指向同一个对象";
                return true;
            }

            return false;
        }

        /**
         * abort 链解除后，基于帧 #1~#5 中的 DPDK 函数重新分类
         */
        @SuppressWarnings("unchecked")
        private boolean tryReclassifyAfterAbort(List<Map<String, Object>> frames,
                                                 List<String> funcStack,
                                                 String abortSource) {
            Set<String> allFuncs = collectAllFuncs(frames);
            crashFunction = abortSource.isEmpty() ? funcStack.get(0) : abortSource;

            // 断言型 abort
            if (allFuncs.stream().anyMatch(f -> {
                for (String af : ASSERT_FUNCTIONS) {
                    if (f.contains(af)) return true;
                }
                return false;
            })) {
                setType("ASSERTION_FAILURE", "断言失败", 80);
                rootCause = "程序在执行 " + abortSource + " 时触发断言失败";
                suggestion = "1. 检查 " + abortSource + " 的前置条件（参数/状态）\n"
                        + "2. 查看上方帧定义的断言表达式";
                return true;
            }

            // mempool abort
            if (containsAny(allFuncs, MEMPOOL_FUNCTIONS) || matchesFamily(allFuncs, RING_FAMILY_PATTERN)) {
                return detectMempoolSubPattern(frames, allFuncs);
            }

            // driver abort
            if (containsAny(allFuncs, DRIVER_FUNCTIONS) || containsPmdFunc(allFuncs)
                    || matchesFamily(allFuncs, DRIVER_FAMILY_PATTERN)) {
                return detectDriverSubPattern(frames, allFuncs);
            }

            // generic abort chain (no DPDK-specific function found)
            setType("SIGNAL_ABORT", "主动终止", 40);
            String where = abortSource.isEmpty() ? "#0 " + funcStack.get(0) : "#" + abortSourceDepth + " " + abortSource;
            rootCause = "程序在 " + where + " 处触发主动终止 (SIGABRT)，非 rte_panic/RTE_VERIFY 等 DPDK 断言，"
                    + "可能是 glibc 内部检测到堆损坏/双重释放/缓冲区溢出";
            suggestion = "1. 检查 " + abortSource + " 之前的帧是否有 free/rte_free 等释放操作（堆损坏）\n"
                    + "2. 如果 glibc 版本较新，使用 MALLOC_CHECK_=3 环境变量重新运行以获得更详细的诊断\n"
                    + "3. 使用 valgrind 或 AddressSanitizer 定位堆损坏点";
            return true;
        }

        // ---------- mempool 子模式 ----------
        @SuppressWarnings("unchecked")
        private boolean detectMempoolSubPattern(List<Map<String, Object>> frames,
                                                 Set<String> allFuncs) {
            String matchedFunc = findFirstMatch(allFuncs, MEMPOOL_FUNCTIONS);

            // 双释放
            boolean hasPktmbufFree = allFuncs.stream().anyMatch(f ->
                    f.contains("rte_pktmbuf_free") || f.contains("rte_mempool_put"));
            boolean hasFree = allFuncs.stream().anyMatch(f ->
                    f.contains("rte_free") || f.startsWith("free"));
            boolean isSIGABRT = "SIGABRT".equals(signalName);

            if (hasPktmbufFree && isSIGABRT) {
                setType("MEMPOOL_CORRUPTION", "内存池操作异常", 85);
                subPattern = "double_free";
                subPatternLabel = "重复释放";
                rootCause = "rte_pktmbuf_free / rte_mempool_put 触发了 SIGABRT，"
                        + "很可能是对一个已释放的 mbuf 进行了二次释放";
                suggestion = "1. 检查代码中是否存在两个释放路径指向同一个 mbuf\n"
                        + "2. 确保 TX 发送后不会再次调用 rte_pktmbuf_free\n"
                        + "3. 使用 mbuf->refcnt 验证引用计数未变为负值";
                return true;
            }
            if (hasPktmbufFree && !isSIGABRT) {
                setType("MEMPOOL_CORRUPTION", "内存池操作异常", 80);
                subPattern = subPattern.isEmpty() ? "mbuf_free_crash" : subPattern;
                subPatternLabel = subPatternLabel.isEmpty() ? "mbuf 释放异常" : subPatternLabel;
                rootCause = matchedFunc + " 中访问了无效的 mbuf 或 mempool 结构";
                suggestion = "1. 在 GDB 中对 mbuf 执行 p *mbuf，检查 refcnt 和 pool 指针\n"
                        + "2. 如果 pool 指针是 0xdeadbeef 等乱码→ 存在内存踩踏\n"
                        + "3. 检查是否有前序代码超长拷贝覆盖了 mbuf 头部";
                return true;
            }

            // rte_mempool_get 相关的空指针
            boolean hasMempoolGet = allFuncs.stream().anyMatch(f ->
                    f.contains("rte_mempool_get") || f.contains("rte_ring_dequeue"));
            boolean nullPtrLike = "0x0".equals(faultAddress) || "0x0000000000000000".equals(faultAddress);

            if (hasMempoolGet && nullPtrLike) {
                setType("MEMPOOL_CORRUPTION", "内存池操作异常", 85);
                subPattern = "pool_exhaustion";
                subPatternLabel = "内存池枯竭";
                rootCause = "rte_mempool_get 返回了 NULL，调用者未进行空指针检查即访问导致崩溃";
                suggestion = "1. 在 rte_mempool_get 调用后检查返回值是否为空\n"
                        + "2. 排查是否存在 mbuf 泄漏（分配后未释放）\n"
                        + "3. 检查 mempool 创建大小是否满足峰值流量需求";
                return true;
            }

            // 通用 mempool 异常
            setType("MEMPOOL_CORRUPTION", "内存池操作异常", 75);
            rootCause = matchedFunc + " 中出现异常，可能为 mempool / ring / mbuf 损坏";
            suggestion = "1. 检查 " + matchedFunc + " 的参数是否来自正确的 mempool\n"
                    + "2. 查看 ring 的 prod/cons 计数器是否正常\n"
                    + "3. 排查是否有其他 lcore 并发操作同一 mempool";
            return true;
        }

        // ---------- 驱动子模式 ----------
        private boolean detectDriverSubPattern(List<Map<String, Object>> frames,
                                                Set<String> allFuncs) {
            String matchedFunc = findFirstMatch(allFuncs, DRIVER_FUNCTIONS);

            // PMD 收发包崩溃
            String pmdFunc = null;
            for (String f : allFuncs) {
                if (PMD_FUNC_PATTERN.matcher(f).find()) {
                    pmdFunc = f;
                    break;
                }
            }
            if (pmdFunc != null) {
                setType("DRIVER_CONFLICT", "驱动适配冲突", 80);
                subPattern = "pmd_crash";
                subPatternLabel = "PMD 收发崩溃";
                rootCause = pmdFunc + " 收发包时异常，传入的 mbuf 或队列可能无效";
                suggestion = "1. 检查收发包队列配置（rx/tx descriptors 数量）\n"
                        + "2. 确认 mbuf 来自正确的 mempool\n"
                        + "3. 验证队列是否在之前被 stop/close 过";
                return true;
            }

            // 非对齐访问（rte_memcpy + 驱动路径）
            if (containsAny(allFuncs, COPY_FUNCTIONS) && matchedFunc != null && !matchedFunc.isEmpty()) {
                setType("DRIVER_CONFLICT", "驱动适配冲突", 65);
                subPattern = "non_aligned";
                subPatternLabel = "潜在非对齐访问";
                rootCause = "在驱动函数 " + matchedFunc
                        + " 路径上出现内存拷贝崩溃，可能是非对齐访问或 SIMD 指令兼容性问题";
                suggestion = "1. 检查被操作的内存地址是否为 16/32/64 字节对齐\n"
                        + "2. 确认 CPU 支持代码编译时指定的指令集（如 AVX-512）\n"
                        + "3. 避免将字节数组强转为 uint64_t* 等大对齐类型";
                return true;
            }

            // 设备初始化/停止崩溃
            setType("DRIVER_CONFLICT", "驱动适配冲突", 70);
            rootCause = matchedFunc + " 中异常，可能与设备初始化、offload 配置或硬件兼容性有关";
            suggestion = "1. 检查网卡绑定驱动是否正确（igb_uio / vfio-pci）\n"
                    + "2. 确认 offload 特性（TSO/LRO/checksum）驱动是否支持\n"
                    + "3. 验证大页内存和 IOMMU 配置";
            return true;
        }

        // ---------- 第三层：跨线程竞争 ----------
        @SuppressWarnings("unchecked")
        boolean tryThreadContentionLevel() {
            if (allThreads == null || allThreads.size() < 2) return false;

            List<Map<String, String>> contested = new ArrayList<>();
            int lockCount = 0;
            int ringOpCount = 0;

            for (Map<String, Object> t : allThreads) {
                List<Map<String, Object>> frames =
                        (List<Map<String, Object>>) t.get("frames");
                if (frames == null) continue;

                Set<String> funcs = collectAllFuncs(frames);
                boolean inLock = containsAny(funcs, LOCK_FUNCTIONS);
                boolean inRing = funcs.stream().anyMatch(f ->
                        f.contains("rte_ring_enqueue") || f.contains("rte_ring_dequeue")
                                || f.contains("__rte_ring_"));

                if (inLock) lockCount++;
                if (inRing && inLock) ringOpCount++;

                if (inLock || inRing) {
                    Map<String, String> info = new LinkedHashMap<>();
                    info.put("id", String.valueOf(t.getOrDefault("thread_id", "")));
                    info.put("name", (String) t.getOrDefault("thread_name", ""));
                    String firstFunc = frames.isEmpty() ? "" :
                            (String) frames.get(0).getOrDefault("function_name", "");
                    info.put("func", firstFunc != null ? firstFunc : "");
                    info.put("note", inLock ? "等锁中" : "Ring操作中");
                    contested.add(info);
                }
            }

            if (lockCount >= 2) {
                setType("THREAD_CONTENTION", "多核线程资源竞争", 75);
                contentionDetected = true;
                relatedThreads = contested;
                subPattern = lockCount >= 4 ? "spinlock_contention" :
                        ringOpCount >= 2 ? "ring_misuse" : "race_condition";
                subPatternLabel = subPattern.equals("spinlock_contention") ? "自旋锁竞争" :
                        subPattern.equals("ring_misuse") ? "Ring队列错用" : "数据竞争";

                if ("spinlock_contention".equals(subPattern)) {
                    rootCause = "检测到 " + lockCount + " 个线程同时在自旋锁上等待，锁持有时间过长或发生死锁";
                    suggestion = "1. 检查锁持有者的代码路径是否存在长时间操作\n"
                            + "2. 确认是否存在 A→B→A 的锁顺序反转\n"
                            + "3. 考虑使用无锁数据结构或减少锁粒度";
                } else if ("ring_misuse".equals(subPattern)) {
                    rootCause = "多个线程同时对 Ring 进行操作，可能存在 SP/SC 模式下多生产者/消费者问题";
                    suggestion = "1. 确认 rte_ring_create 时指定了正确的 flags（MP/MC）\n"
                            + "2. 检查是否所有 lcore 都在使用正确的操作模式";
                } else {
                    rootCause = "多个线程之间存在数据竞争，一个线程在操作数据时被另一个线程修改";
                    suggestion = "1. 对共享数据添加 rte_spinlock_lock 保护\n"
                            + "2. 检查判空和取值之间的窗口期（幽灵变量现象）\n"
                            + "3. 考虑使用 per-lcore 数据结构避免共享";
                }
                return true;
            }

            return false;
        }

        // ---------- 第四层：信号 fallback ----------
        void trySignalFallback() {
            if (signalName == null || signalName.isEmpty()) {
                setType(UNKNOWN_ID, UNKNOWN_TYPE, 10);
                rootCause = "无法确定崩溃类型，缺少信号和明确的模式匹配";
                suggestion = "1. 确认 core dump 完整性\n"
                        + "2. 检查是否使用了标准化 GDB 脚本生成日志";
                return;
            }
            String sig = signalName.toUpperCase();
            switch (sig) {
                case "SIGSEGV":
                    setType("BUFFER_OVERFLOW", "缓冲区溢出", 30);
                    rootCause = "段错误 (SIGSEGV)，无法确定具体子类型";
                    suggestion = "1. 可能是空指针、越界或 use-after-free\n"
                            + "2. 建议使用 -fsanitize=address 编译重现";
                    break;
                case "SIGFPE":
                    setType("ARITHMETIC_ERROR", "算术异常", 70);
                    rootCause = "SIGFPE 算术异常，疑似除零或整数溢出";
                    suggestion = "1. 检查代码中是否有除零操作\n"
                            + "2. 检查整数溢出情况";
                    break;
                case "SIGBUS":
                    setType("BUS_ERROR", "总线错误", 60);
                    rootCause = "SIGBUS 总线错误，通常与硬件映射、大页内存、非对齐访问相关";
                    suggestion = "1. 检查 IOMMU/VFIO 配置是否正确\n"
                            + "2. 确认大页内存未被 swap\n"
                            + "3. 排查是否出现非对齐的内存访问";
                    break;
                case "SIGILL":
                    setType("BUS_ERROR", "非法指令", 70);
                    rootCause = "SIGILL 非法指令，CPU 无法识别当前指令，可能与指令集不兼容有关";
                    suggestion = "1. 确认编译时使用的 -march 与目标 CPU 兼容\n"
                            + "2. 检查是否在不支持 AVX-512 的 CPU 上运行了 AVX-512 代码";
                    break;
                case "SIGQUIT":
                    setType("THREAD_CONTENTION", "多核线程资源竞争", 40);
                    rootCause = "SIGQUIT 信号（通常来自 Ctrl+\\ 或看门狗超时），"
                            + "程序在执行 " + crashFunction + " 时被强制终止，可能因死锁或长时间自旋";
                    suggestion = "1. 检查是否有线程长时间持有自旋锁\n"
                            + "2. 确认 ring 操作是否存在无限循环\n"
                            + "3. 检查看门狗（watchdog）超时配置是否过短";
                    break;
                default:
                    setType(UNKNOWN_ID, UNKNOWN_TYPE, 15);
                    rootCause = "信号 " + signalName + "，暂无专用检测逻辑";
                    suggestion = "查看线程分析调用堆栈";
            }
        }

        // ---- 工具方法 ----

        void setType(String id, String label, int conf) {
            crashTypeId = id;
            crashType = label;
            confidence = Math.max(confidence, conf);
        }

        void finalizeResult(ParseContext context) {
            if (keyFunctions.isEmpty() && !crashFunction.isEmpty()) {
                keyFunctions.add(crashFunction);
            }
            Map<String, Object> diagMap = new LinkedHashMap<>();
            diagMap.put("crashType", crashType);
            diagMap.put("crashTypeId", crashTypeId);
            diagMap.put("confidence", confidence);
            diagMap.put("crashFunction", crashFunction);
            diagMap.put("sourceLocation", sourceLocation);
            diagMap.put("signal", signalName);
            diagMap.put("faultAddress", faultAddress);
            diagMap.put("isAbortChain", isAbortChain);
            diagMap.put("rootCause", rootCause);
            diagMap.put("suggestion", suggestion);
            diagMap.put("keyFunctions", keyFunctions);
            diagMap.put("contentionDetected", contentionDetected);
            diagMap.put("relatedThreads", relatedThreads);
            diagMap.put("subPattern", subPattern);
            diagMap.put("subPatternLabel", subPatternLabel);
            diagMap.put("abortSourceFunc", abortSourceFunc);
            diagMap.put("abortSourceDepth", abortSourceDepth);
            context.getExtras().put("diagnosis", diagMap);
        }

        String getCrashType() { return crashType; }
        int getConfidence() { return confidence; }

        @SuppressWarnings("unchecked")
        private static Set<String> collectAllFuncs(List<Map<String, Object>> frames) {
            Set<String> result = new LinkedHashSet<>();
            for (Map<String, Object> f : frames) {
                String fn = (String) f.get("function_name");
                if (fn != null && !fn.isEmpty() && !"??".equals(fn)) {
                    result.add(fn);
                }
            }
            return result;
        }

        private static boolean containsAny(Set<String> funcs, Set<String> targets) {
            for (String f : funcs) {
                for (String t : targets) {
                    if (f.contains(t)) return true;
                }
            }
            return false;
        }

        private static boolean matchesFamily(Set<String> funcs, Pattern pattern) {
            for (String f : funcs) {
                if (pattern.matcher(f).find()) return true;
            }
            return false;
        }

        private static boolean containsPmdFunc(Set<String> funcs) {
            for (String f : funcs) {
                if (PMD_FUNC_PATTERN.matcher(f).find()) return true;
            }
            return false;
        }

        private static String findFirstMatch(Set<String> funcs, Set<String> targets) {
            for (String f : funcs) {
                for (String t : targets) {
                    if (f.contains(t)) return f;
                }
            }
            return "";
        }

        private static boolean isAbortFunc(String fn) {
            for (String af : ABORT_FUNCTIONS) {
                if (fn.equals(af)) return true;
            }
            return false;
        }

        private static boolean isDoubleFree(List<String> funcStack, Set<String> allFuncs) {
            return funcStack.stream().anyMatch(f ->
                    f.contains("rte_pktmbuf_free") || f.contains("rte_mempool_put"))
                    || allFuncs.stream().anyMatch(f -> f.contains("rte_pktmbuf_free"))
                    && funcStack.stream().anyMatch(f -> f.contains("rte_pktmbuf_free")
                    || f.contains("rte_mempool_put"));
        }
    }
}
