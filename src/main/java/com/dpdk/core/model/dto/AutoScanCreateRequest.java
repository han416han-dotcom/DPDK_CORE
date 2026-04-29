package com.dpdk.core.model.dto;

import jakarta.validation.constraints.NotBlank;

public class AutoScanCreateRequest {
    @NotBlank(message = "sourcePath 不能为空")
    private String sourcePath;

    private String sourceType;
    private String execPath;
    private String taskName;

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getExecPath() {
        return execPath;
    }

    public void setExecPath(String execPath) {
        this.execPath = execPath;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }
}
