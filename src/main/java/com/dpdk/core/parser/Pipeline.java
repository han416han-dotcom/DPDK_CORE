package com.dpdk.core.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 解析管道 — 编排所有 Parser 按阶段顺序执行。
 *
 * 自动收集所有 Parser Bean，按 Stage 排序后串联执行。
 * 每个阶段可以注册多个 Parser，框架保证按 order 升序执行。
 */
@Component
public class Pipeline {

    private static final Logger log = LoggerFactory.getLogger(Pipeline.class);

    private final List<Parser> parsers;

    @Autowired
    public Pipeline(List<Parser> parsers) {
        // 按阶段排序
        this.parsers = parsers.stream()
                .sorted(Comparator.comparingInt(p -> p.getStage().getOrder()))
                .collect(Collectors.toList());

        log.info("解析管道初始化完成, 注册 {} 个解析器: {}",
                this.parsers.size(),
                this.parsers.stream().map(p -> p.getName() + "@" + p.getStage().name()).collect(Collectors.joining(", ")));
    }

    /**
     * 执行完整解析管道
     *
     * @param context 解析上下文
     * @return true=所有阶段成功; false=有阶段失败(但可能部分结果可用)
     */
    public boolean execute(ParseContext context) {
        log.info("开始执行解析管道, taskId={}, 共 {} 个阶段", context.getTaskId(), parsers.size());

        boolean allSuccess = true;

        for (Parser parser : parsers) {
            String stageName = parser.getStage().getDisplayName();
            String parserName = parser.getName();

            log.debug("执行解析器: {} (阶段: {})", parserName, stageName);

            long start = System.currentTimeMillis();

            try {
                boolean continuePipeline = parser.parse(context);
                long elapsed = System.currentTimeMillis() - start;

                if (continuePipeline) {
                    log.debug("解析器 {} 完成, 耗时 {}ms", parserName, elapsed);
                } else {
                    context.addLog("WARN", stageName,
                            parserName + " 中断 ({}ms)".replace("{}", String.valueOf(elapsed)));
                    log.warn("解析器 {} 要求中断管道", parserName);
                    allSuccess = false;
                    break;
                }
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - start;
                context.addLog("ERROR", stageName,
                        parserName + " 异常: " + e.getMessage());
                log.error("解析器 {} 异常 ({}ms)", parserName, elapsed, e);
                allSuccess = false;
            }
        }

        log.info("解析管道执行完毕, taskId={}, 全部成功={}", context.getTaskId(), allSuccess);
        return allSuccess;
    }

    /**
     * 获取解析管道信息
     */
    public List<Map<String, Object>> getPipelineInfo() {
        return parsers.stream().map(p -> {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", p.getName());
            info.put("stage", p.getStage().name());
            info.put("stageOrder", p.getStage().getOrder());
            info.put("version", p.getVersion());
            return info;
        }).collect(Collectors.toList());
    }
}
