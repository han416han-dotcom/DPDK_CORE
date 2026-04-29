package com.dpdk.core.parser.aggregate;

import com.dpdk.core.parser.ParseContext;
import com.dpdk.core.parser.ParseStage;
import com.dpdk.core.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 聚合阶段 — 汇总各阶段解析结果, 计算可信度, 生成最终结论。
 * 作为管道的最后一环, 确保输出数据的一致性和完整性。
 */
@Component
public class Aggregator implements Parser {

    private static final Logger log = LoggerFactory.getLogger(Aggregator.class);

    @Value("${app.parser.confidence-threshold:30}")
    private int confidenceThreshold;

    @Override
    public ParseStage getStage() {
        return ParseStage.AGGREGATE;
    }

    @Override
    public String getName() {
        return "Aggregator";
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean parse(ParseContext context) {
        List<Map<String, Object>> threads = context.getParsedThreads();
        if (threads == null || threads.isEmpty()) {
            context.addWarning(getName(), "无线程数据可聚合");
            return true;
        }

        // 1. 崩溃线程堆栈深度检测
        for (Map<String, Object> thread : threads) {
            if (Boolean.TRUE.equals(thread.get("crash_thread"))) {
                int depth = thread.get("stack_depth") instanceof Number
                        ? ((Number) thread.get("stack_depth")).intValue() : 0;
                if (depth <= 1) {
                    context.addWarning(getName(),
                            "崩溃线程堆栈深度仅 " + depth + " 帧，可能不完整。" +
                            "建议：编译时添加 -g -fno-omit-frame-pointer 以获得完整回溯");
                }
                break;
            }
        }

        // 2. 低可信度帧标记
        int lowConfidenceFrames = 0;

        for (Map<String, Object> thread : threads) {
            List<Map<String, Object>> frames = (List<Map<String, Object>>) thread.get("frames");
            if (frames == null) continue;

            for (Map<String, Object> frame : frames) {
                int confidence = frame.get("confidence") instanceof Number
                        ? ((Number) frame.get("confidence")).intValue() : 0;
                if (confidence < confidenceThreshold) {
                    lowConfidenceFrames++;
                }
            }
        }

        context.getStats().put("low_confidence_frames", lowConfidenceFrames);

        // 3. 识别崩溃根因
        String crashSummary = summarizeCrash(context);
        if (crashSummary != null) {
            context.getExtras().put("crash_summary", crashSummary);
            context.addLog("INFO", getName(), "崩溃总结: " + crashSummary);
        }

        // 4. 线程分类统计
        long lcoreThreads = threads.stream()
                .filter(t -> Boolean.TRUE.equals(t.get("is_lcore")))
                .count();
        long crashThreads = threads.stream()
                .filter(t -> Boolean.TRUE.equals(t.get("crash_thread")))
                .count();

        context.getStats().put("lcore_threads", (int) lcoreThreads);
        context.getStats().put("crash_threads", (int) crashThreads);

        // 5. 计算 DPDK 函数占比
        int totalFrames = threads.stream()
                .mapToInt(t -> {
                    List<Map<String, Object>> f = (List<Map<String, Object>>) t.get("frames");
                    return f != null ? f.size() : 0;
                }).sum();

        int dpdkFrames = threads.stream()
                .flatMap(t -> {
                    List<Map<String, Object>> f = (List<Map<String, Object>>) t.get("frames");
                    return f != null ? f.stream() : Stream.empty();
                })
                .mapToInt(f -> Boolean.TRUE.equals(f.get("is_dpdk_func")) ? 1 : 0)
                .sum();

        context.getStats().put("total_frames", totalFrames);
        context.getStats().put("dpdk_frames", dpdkFrames);

        if (totalFrames > 0) {
            context.getStats().put("dpdk_ratio",
                    String.format("%.1f%%", (dpdkFrames * 100.0 / totalFrames)));
        }

        context.addLog("INFO", getName(),
                String.format("聚合完成: %d 线程 (%d lcore), %d 帧 (%d DPDK, %d 低可信度)",
                        threads.size(), lcoreThreads, totalFrames, dpdkFrames, lowConfidenceFrames));

        return true;
    }

    @SuppressWarnings("unchecked")
    private String summarizeCrash(ParseContext context) {
        Map<String, Object> crashInfo = context.getCrashInfo();
        List<Map<String, Object>> threads = context.getParsedThreads();

        if (crashInfo == null && threads == null) return null;

        StringBuilder sb = new StringBuilder();

        // 崩溃信号
        if (crashInfo != null) {
            String signal = (String) crashInfo.getOrDefault("signal_name", "");
            String faultAddr = (String) crashInfo.getOrDefault("fault_address", "");
            if (!signal.isBlank()) {
                sb.append("信号: ").append(signal);
                if (!faultAddr.isBlank()) {
                    sb.append(", 故障地址: ").append(faultAddr);
                }
                sb.append("; ");
            }
        }

        // 崩溃线程顶级函数
        if (threads != null) {
            for (Map<String, Object> t : threads) {
                if (Boolean.TRUE.equals(t.get("crash_thread"))) {
                    List<Map<String, Object>> frames = (List<Map<String, Object>>) t.get("frames");
                    if (frames != null && !frames.isEmpty()) {
                        Map<String, Object> top = frames.get(0);
                        String func = (String) top.getOrDefault("function_name", "???");
                        sb.append("崩溃帧: #0 ").append(func);
                    }
                    break;
                }
            }
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

}
