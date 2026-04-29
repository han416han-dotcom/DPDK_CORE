package com.dpdk.core.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("parse_tasks")
public class ParseTask {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long gdbLogFileId;
    private Long execFileId;
    private String taskName;
    private String status;       // PENDING / RUNNING / COMPLETED / FAILED
    private String parseVersion;
    private Integer totalThreads;
    private String crashSignal;
    private String faultAddress;
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getGdbLogFileId() { return gdbLogFileId; }
    public void setGdbLogFileId(Long gdbLogFileId) { this.gdbLogFileId = gdbLogFileId; }
    public Long getExecFileId() { return execFileId; }
    public void setExecFileId(Long execFileId) { this.execFileId = execFileId; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getParseVersion() { return parseVersion; }
    public void setParseVersion(String parseVersion) { this.parseVersion = parseVersion; }
    public Integer getTotalThreads() { return totalThreads; }
    public void setTotalThreads(Integer totalThreads) { this.totalThreads = totalThreads; }
    public String getCrashSignal() { return crashSignal; }
    public void setCrashSignal(String crashSignal) { this.crashSignal = crashSignal; }
    public String getFaultAddress() { return faultAddress; }
    public void setFaultAddress(String faultAddress) { this.faultAddress = faultAddress; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
