package com.dpdk.core.parser.dpdk;

import com.dpdk.core.parser.ParseContext;
import com.dpdk.core.parser.ParseStage;
import com.dpdk.core.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * DPDK 专有分析阶段。
 * - 识别 lcore 线程
 * - 标注 DPDK API 调用
 * - 检测 DPDK 典型崩溃模式
 * - 预留 ring/mempool/mbuf 分析扩展点
 */
@Component
public class DpdkAnalyzer implements Parser {

    private static final Logger log = LoggerFactory.getLogger(DpdkAnalyzer.class);

    // DPDK 关键函数列表
    private static final List<Pattern> DPDK_CRASH_PATTERNS = List.of(
            Pattern.compile("rte_panic"),
            Pattern.compile("__rte_panic"),
            Pattern.compile("RTE_VERIFY"),
            Pattern.compile("rte_ring_dequeue"),
            Pattern.compile("rte_ring_enqueue"),
            Pattern.compile("rte_mempool_get"),
            Pattern.compile("rte_mempool_put"),
            Pattern.compile("rte_pktmbuf_free"),
            Pattern.compile("rte_mbuf_sanity_check"),
            Pattern.compile("rte_free")
    );

    // 线程名中的 lcore 模式
    private static final Pattern LCORE_PATTERN = Pattern.compile(
            "(?i)(lcore|worker)[\\s-_]?(\\d+)"
    );

    // 栈底函数名标识 lcore worker 线程（线程名中无 "lcore" 关键词时的 fallback）
    private static final List<String> LCORE_WORKER_FUNCTIONS = List.of(
            "eal_worker_thread_loop"
    );

    // 栈帧参数中 lcore_id 的可能变量名
    private static final List<String> LCORE_ID_PARAM_NAMES = List.of(
            "lcore_id", "worker_id", "slave_id"
    );

    @Override
    public ParseStage getStage() {
        return ParseStage.DPDK_ANALYZE;
    }

    @Override
    public String getName() {
        return "DpdkAnalyzer";
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean parse(ParseContext context) {
        List<Map<String, Object>> threads = context.getParsedThreads();
        if (threads == null || threads.isEmpty()) {
            context.addWarning(getName(), "无线程数据可分析");
            return true;
        }

        int lcoreCount = 0;
        int dpdkFrameCount = 0;

        for (Map<String, Object> thread : threads) {
            // 1. 识别 lcore 线程
            String threadName = (String) thread.get("thread_name");
            String threadId = (String) thread.get("thread_id");

            boolean isLcore = false;
            Integer lcoreId = null;

            if (threadName != null) {
                var m = LCORE_PATTERN.matcher(threadName);
                if (m.find()) {
                    isLcore = true;
                    try {
                        lcoreId = Integer.parseInt(m.group(2));
                    } catch (NumberFormatException ignored) {}
                }
            }

            // 2. 如果线程名未识别，通过扫描所有帧函数名检测 lcore
            if (!isLcore) {
                List<Map<String, Object>> frames = (List<Map<String, Object>>) thread.get("frames");
                if (frames != null && !frames.isEmpty()) {
                    boolean foundLcoreFunc = false;
                    for (Map<String, Object> frame : frames) {
                        String funcName = (String) frame.get("function_name");
                        if (funcName != null && LCORE_WORKER_FUNCTIONS.stream().anyMatch(funcName::contains)) {
                            isLcore = true;
                            foundLcoreFunc = true;
                            break;
                        }
                    }
                    if (foundLcoreFunc) {
                        lcoreId = extractLcoreId(frames);
                    }
                }
            }

            thread.put("is_lcore", isLcore);
            thread.put("lcore_id", lcoreId);
            if (isLcore) lcoreCount++;

            // 3. 标注 DPDK 函数调用
            List<Map<String, Object>> frames = (List<Map<String, Object>>) thread.get("frames");
            if (frames == null) continue;

            for (Map<String, Object> frame : frames) {
                String funcName = (String) frame.get("function_name");
                if (funcName == null) continue;

                boolean isDpdk = isDpdkFunction(funcName);
                frame.put("is_dpdk_func", isDpdk);
                if (isDpdk) dpdkFrameCount++;

                // 检测 DPDK 崩溃
                if (isDpdkCrashFunction(funcName)) {
                    context.addLog("INFO", getName(), "检测到 DPDK 崩溃函数: " + funcName);
                }
            }
        }

        context.addLog("INFO", getName(),
                String.format("DPDK 分析: %d 个 lcore 线程, %d 个 DPDK 栈帧", lcoreCount, dpdkFrameCount));

        // 预留扩展点 (后续可添加 ring/mempool/mbuf 分析)
        context.getExtras().put("dpdk_lcore_count", lcoreCount);
        context.getExtras().put("dpdk_frame_count", dpdkFrameCount);

        return true;
    }

    private boolean isDpdkFunction(String funcName) {
        return funcName.startsWith("rte_") ||
               funcName.startsWith("__rte_") ||
               funcName.startsWith("eal_") ||
               funcName.contains("dpdk");
    }

    private boolean isDpdkCrashFunction(String funcName) {
        return DPDK_CRASH_PATTERNS.stream().anyMatch(p -> p.matcher(funcName).find());
    }

    @SuppressWarnings("unchecked")
    private Integer extractLcoreId(List<Map<String, Object>> frames) {
        for (Map<String, Object> frame : frames) {
            String args = (String) frame.get("args");
            if (args != null) {
                for (String paramName : LCORE_ID_PARAM_NAMES) {
                    String searchKey = paramName + "=";
                    int idx = args.indexOf(searchKey);
                    if (idx >= 0) {
                        String after = args.substring(idx + searchKey.length());
                        StringBuilder num = new StringBuilder();
                        for (int i = 0; i < after.length(); i++) {
                            char c = after.charAt(i);
                            if (c >= '0' && c <= '9') {
                                num.append(c);
                            } else {
                                break;
                            }
                        }
                        if (!num.isEmpty()) {
                            try {
                                return Integer.parseInt(num.toString());
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        }
        return null;
    }

}
