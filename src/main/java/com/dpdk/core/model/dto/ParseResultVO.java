package com.dpdk.core.model.dto;

import com.dpdk.core.model.entity.*;
import java.util.List;
import java.util.Map;

/**
 * 解析结果视图对象 — 供前端展示
 */
public class ParseResultVO {
    private Long taskId;
    private String taskName;
    private String status;
    private String crashSignal;
    private String faultAddress;
    private Integer totalThreads;
    private List<ThreadInfo> threads;
    private List<ParseLog> logs;
    private Diagnosis diagnosis;

    public static class Diagnosis {
        private String crashType;
        private String crashTypeId;
        private int confidence;
        private String crashFunction;
        private String sourceLocation;
        private String signal;
        private String faultAddress;
        private boolean isAbortChain;
        private String rootCause;
        private String suggestion;
        private List<String> keyFunctions;
        private boolean contentionDetected;
        private List<Map<String, String>> relatedThreads;
        private String subPattern;
        private String subPatternLabel;
        private String abortSourceFunc;
        private int abortSourceDepth;

        public String getCrashType() { return crashType; }
        public void setCrashType(String crashType) { this.crashType = crashType; }
        public String getCrashTypeId() { return crashTypeId; }
        public void setCrashTypeId(String crashTypeId) { this.crashTypeId = crashTypeId; }
        public int getConfidence() { return confidence; }
        public void setConfidence(int confidence) { this.confidence = confidence; }
        public String getCrashFunction() { return crashFunction; }
        public void setCrashFunction(String crashFunction) { this.crashFunction = crashFunction; }
        public String getSourceLocation() { return sourceLocation; }
        public void setSourceLocation(String sourceLocation) { this.sourceLocation = sourceLocation; }
        public String getSignal() { return signal; }
        public void setSignal(String signal) { this.signal = signal; }
        public String getFaultAddress() { return faultAddress; }
        public void setFaultAddress(String faultAddress) { this.faultAddress = faultAddress; }
        public boolean getIsAbortChain() { return isAbortChain; }
        public void setIsAbortChain(boolean isAbortChain) { this.isAbortChain = isAbortChain; }
        public String getRootCause() { return rootCause; }
        public void setRootCause(String rootCause) { this.rootCause = rootCause; }
        public String getSuggestion() { return suggestion; }
        public void setSuggestion(String suggestion) { this.suggestion = suggestion; }
        public List<String> getKeyFunctions() { return keyFunctions; }
        public void setKeyFunctions(List<String> keyFunctions) { this.keyFunctions = keyFunctions; }
        public boolean getContentionDetected() { return contentionDetected; }
        public void setContentionDetected(boolean contentionDetected) { this.contentionDetected = contentionDetected; }
        public List<Map<String, String>> getRelatedThreads() { return relatedThreads; }
        public void setRelatedThreads(List<Map<String, String>> relatedThreads) { this.relatedThreads = relatedThreads; }
        public String getSubPattern() { return subPattern; }
        public void setSubPattern(String subPattern) { this.subPattern = subPattern; }
        public String getSubPatternLabel() { return subPatternLabel; }
        public void setSubPatternLabel(String subPatternLabel) { this.subPatternLabel = subPatternLabel; }
        public String getAbortSourceFunc() { return abortSourceFunc; }
        public void setAbortSourceFunc(String abortSourceFunc) { this.abortSourceFunc = abortSourceFunc; }
        public int getAbortSourceDepth() { return abortSourceDepth; }
        public void setAbortSourceDepth(int abortSourceDepth) { this.abortSourceDepth = abortSourceDepth; }
    }

    public static class ThreadInfo {
        private Long id;
        private String threadId;
        private String threadName;
        private Boolean isLcore;
        private Integer lcoreId;
        private Boolean crashThread;
        private Integer stackDepth;
        private List<FrameInfo> frames;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getThreadId() { return threadId; }
        public void setThreadId(String threadId) { this.threadId = threadId; }
        public String getThreadName() { return threadName; }
        public void setThreadName(String threadName) { this.threadName = threadName; }
        public Boolean getIsLcore() { return isLcore; }
        public void setIsLcore(Boolean isLcore) { this.isLcore = isLcore; }
        public Integer getLcoreId() { return lcoreId; }
        public void setLcoreId(Integer lcoreId) { this.lcoreId = lcoreId; }
        public Boolean getCrashThread() { return crashThread; }
        public void setCrashThread(Boolean crashThread) { this.crashThread = crashThread; }
        public Integer getStackDepth() { return stackDepth; }
        public void setStackDepth(Integer stackDepth) { this.stackDepth = stackDepth; }
        public List<FrameInfo> getFrames() { return frames; }
        public void setFrames(List<FrameInfo> frames) { this.frames = frames; }
    }

    public static class FrameInfo {
        private Integer index;
        private String address;
        private String functionName;
        private String sourceFile;
        private Integer sourceLine;
        private String offsetInFunc;
        private String args;
        private Boolean resolved;
        private Boolean isDpdkFunc;
        private Integer confidence;

        public Integer getIndex() { return index; }
        public void setIndex(Integer index) { this.index = index; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public String getFunctionName() { return functionName; }
        public void setFunctionName(String functionName) { this.functionName = functionName; }
        public String getSourceFile() { return sourceFile; }
        public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
        public Integer getSourceLine() { return sourceLine; }
        public void setSourceLine(Integer sourceLine) { this.sourceLine = sourceLine; }
        public String getOffsetInFunc() { return offsetInFunc; }
        public void setOffsetInFunc(String offsetInFunc) { this.offsetInFunc = offsetInFunc; }
        public String getArgs() { return args; }
        public void setArgs(String args) { this.args = args; }
        public Boolean getResolved() { return resolved; }
        public void setResolved(Boolean resolved) { this.resolved = resolved; }
        public Boolean getIsDpdkFunc() { return isDpdkFunc; }
        public void setIsDpdkFunc(Boolean isDpdkFunc) { this.isDpdkFunc = isDpdkFunc; }
        public Integer getConfidence() { return confidence; }
        public void setConfidence(Integer confidence) { this.confidence = confidence; }
    }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCrashSignal() { return crashSignal; }
    public void setCrashSignal(String crashSignal) { this.crashSignal = crashSignal; }
    public String getFaultAddress() { return faultAddress; }
    public void setFaultAddress(String faultAddress) { this.faultAddress = faultAddress; }
    public Integer getTotalThreads() { return totalThreads; }
    public void setTotalThreads(Integer totalThreads) { this.totalThreads = totalThreads; }
    public List<ThreadInfo> getThreads() { return threads; }
    public void setThreads(List<ThreadInfo> threads) { this.threads = threads; }
    public List<ParseLog> getLogs() { return logs; }
    public void setLogs(List<ParseLog> logs) { this.logs = logs; }
    public Diagnosis getDiagnosis() { return diagnosis; }
    public void setDiagnosis(Diagnosis diagnosis) { this.diagnosis = diagnosis; }
}
