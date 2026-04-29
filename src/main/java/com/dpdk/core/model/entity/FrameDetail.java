package com.dpdk.core.model.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("frame_details")
public class FrameDetail {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long threadId;
    private Integer frameIndex;
    private String rawLine;
    private String address;
    private String functionName;
    private String sourceFile;
    private Integer sourceLine;
    private String offsetInFunc;
    private String args;        // JSON array
    private Boolean resolved;
    private Boolean isDpdkFunc;
    private Integer confidence;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getThreadId() { return threadId; }
    public void setThreadId(Long threadId) { this.threadId = threadId; }
    public Integer getFrameIndex() { return frameIndex; }
    public void setFrameIndex(Integer frameIndex) { this.frameIndex = frameIndex; }
    public String getRawLine() { return rawLine; }
    public void setRawLine(String rawLine) { this.rawLine = rawLine; }
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
