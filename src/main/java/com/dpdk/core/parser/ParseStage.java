package com.dpdk.core.parser;

/**
 * 解析阶段 — 决定 Parser 在管道中的执行顺序
 */
public enum ParseStage {
    CORE_PARSE(-50, "Core解析"),   // core dump → GDB 日志 (仅Linux)
    PREPROCESS(0, "预处理"),
    GDB_LOG_PARSE(100, "GDB日志解析"),
    SYMBOL_RESOLVE(200, "符号解析"),
    DPDK_ANALYZE(300, "DPDK专有分析"),
    CRASH_DIAGNOSE(350, "崩溃模式诊断"),
    AGGREGATE(400, "聚合输出");

    private final int order;
    private final String displayName;

    ParseStage(int order, String displayName) {
        this.order = order;
        this.displayName = displayName;
    }

    public int getOrder() { return order; }
    public String getDisplayName() { return displayName; }
}
