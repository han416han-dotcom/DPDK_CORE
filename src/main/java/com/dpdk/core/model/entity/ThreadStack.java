package com.dpdk.core.model.entity;

import com.baomidou.mybatisplus.annotation.*;

@TableName("thread_stacks")
public class ThreadStack {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;
    private String threadId;
    private String threadName;
    private String osThreadId;
    private Boolean isLcore;
    private Integer lcoreId;
    private Boolean crashThread;
    private Integer stackDepth;
    private String rawHeader;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public String getThreadName() { return threadName; }
    public void setThreadName(String threadName) { this.threadName = threadName; }
    public String getOsThreadId() { return osThreadId; }
    public void setOsThreadId(String osThreadId) { this.osThreadId = osThreadId; }
    public Boolean getIsLcore() { return isLcore; }
    public void setIsLcore(Boolean isLcore) { this.isLcore = isLcore; }
    public Integer getLcoreId() { return lcoreId; }
    public void setLcoreId(Integer lcoreId) { this.lcoreId = lcoreId; }
    public Boolean getCrashThread() { return crashThread; }
    public void setCrashThread(Boolean crashThread) { this.crashThread = crashThread; }
    public Integer getStackDepth() { return stackDepth; }
    public void setStackDepth(Integer stackDepth) { this.stackDepth = stackDepth; }
    public String getRawHeader() { return rawHeader; }
    public void setRawHeader(String rawHeader) { this.rawHeader = rawHeader; }
}
