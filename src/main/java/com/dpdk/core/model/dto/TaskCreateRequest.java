package com.dpdk.core.model.dto;

import jakarta.validation.constraints.NotNull;

public class TaskCreateRequest {
    @NotNull(message = "GDB日志文件ID不能为空")
    private Long gdbLogFileId;

    @NotNull(message = "可执行文件ID不能为空")
    private Long execFileId;

    private String taskName;

    public Long getGdbLogFileId() { return gdbLogFileId; }
    public void setGdbLogFileId(Long gdbLogFileId) { this.gdbLogFileId = gdbLogFileId; }
    public Long getExecFileId() { return execFileId; }
    public void setExecFileId(Long execFileId) { this.execFileId = execFileId; }
    public String getTaskName() { return taskName; }
    public void setTaskName(String taskName) { this.taskName = taskName; }
}
