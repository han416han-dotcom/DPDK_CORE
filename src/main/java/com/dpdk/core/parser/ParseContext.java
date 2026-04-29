package com.dpdk.core.parser;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 解析上下文 — 贯穿整个解析管道的所有阶段。
 * 每个阶段读取前置阶段的结果，写入本阶段结果。
 *
 * 设计原则: 上下文不携带敏感数据，仅作为数据交换容器。
 */
public class ParseContext {

    /** 任务 ID */
    private final Long taskId;

    /** GDB 日志文件路径 */
    private Path gdbLogPath;

    /** 可执行文件路径 */
    private Path executablePath;

    /** GDB 日志原始内容 (按行) */
    private List<String> rawLines;

    /** 解析出的线程堆栈 (阶段 2 输出) */
    private List<Map<String, Object>> parsedThreads;

    /** ELF 符号表 (阶段 3 输出) */
    private List<Map<String, Object>> symbols;

    /** 崩溃信号信息 */
    private Map<String, Object> crashInfo;

    /** 寄存器信息 */
    private Map<String, String> registers;

    /** DPDK 专有分析结果 (阶段 4 输出) */
    private Map<String, Object> dpdkInfo;

    /** 解析统计 */
    private final Map<String, Object> stats = new LinkedHashMap<>();

    /** 各阶段日志 */
    private final List<ParseLogEntry> logs = new ArrayList<>();

    /** 非致命错误 */
    private final List<ParseError> errors = new ArrayList<>();

    /** 扩展数据 (供自定义 Parser 使用) */
    private final Map<String, Object> extras = new ConcurrentHashMap<>();

    public ParseContext(Long taskId) {
        this.taskId = taskId;
    }

    // ===== 日志方法 =====

    public void addLog(String level, String stage, String message) {
        logs.add(new ParseLogEntry(level, stage, message));
    }

    public void addError(String stage, String message) {
        errors.add(new ParseError(stage, message));
        addLog("ERROR", stage, message);
    }

    public void addWarning(String stage, String message) {
        addLog("WARN", stage, message);
    }

    // ===== Getters & Setters =====

    public Long getTaskId() { return taskId; }
    public Path getGdbLogPath() { return gdbLogPath; }
    public void setGdbLogPath(Path gdbLogPath) { this.gdbLogPath = gdbLogPath; }
    public Path getExecutablePath() { return executablePath; }
    public void setExecutablePath(Path executablePath) { this.executablePath = executablePath; }
    public List<String> getRawLines() { return rawLines; }
    public void setRawLines(List<String> rawLines) { this.rawLines = rawLines; }
    public List<Map<String, Object>> getParsedThreads() { return parsedThreads; }
    public void setParsedThreads(List<Map<String, Object>> parsedThreads) { this.parsedThreads = parsedThreads; }
    public List<Map<String, Object>> getSymbols() { return symbols; }
    public void setSymbols(List<Map<String, Object>> symbols) { this.symbols = symbols; }
    public Map<String, Object> getCrashInfo() { return crashInfo; }
    public void setCrashInfo(Map<String, Object> crashInfo) { this.crashInfo = crashInfo; }
    public Map<String, String> getRegisters() { return registers; }
    public void setRegisters(Map<String, String> registers) { this.registers = registers; }
    public Map<String, Object> getDpdkInfo() { return dpdkInfo; }
    public void setDpdkInfo(Map<String, Object> dpdkInfo) { this.dpdkInfo = dpdkInfo; }
    public Map<String, Object> getStats() { return stats; }
    public List<ParseLogEntry> getLogs() { return logs; }
    public List<ParseError> getErrors() { return errors; }
    public Map<String, Object> getExtras() { return extras; }

    // ===== 内部类 =====

    public static class ParseLogEntry {
        private final String level;
        private final String stage;
        private final String message;

        public ParseLogEntry(String level, String stage, String message) {
            this.level = level;
            this.stage = stage;
            this.message = message;
        }

        public String getLevel() { return level; }
        public String getStage() { return stage; }
        public String getMessage() { return message; }
    }

    public static class ParseError {
        private final String stage;
        private final String message;

        public ParseError(String stage, String message) {
            this.stage = stage;
            this.message = message;
        }

        public String getStage() { return stage; }
        public String getMessage() { return message; }
    }
}
