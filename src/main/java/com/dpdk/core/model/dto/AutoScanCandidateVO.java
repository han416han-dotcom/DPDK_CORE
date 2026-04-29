package com.dpdk.core.model.dto;

public class AutoScanCandidateVO {
    private String sourceName;
    private String sourcePath;
    private String sourceType;
    private String taskNameSuggestion;
    private Boolean matched;
    private String execName;
    private String execPath;
    private Integer matchScore;
    private String matchRule;

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

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

    public String getTaskNameSuggestion() {
        return taskNameSuggestion;
    }

    public void setTaskNameSuggestion(String taskNameSuggestion) {
        this.taskNameSuggestion = taskNameSuggestion;
    }

    public Boolean getMatched() {
        return matched;
    }

    public void setMatched(Boolean matched) {
        this.matched = matched;
    }

    public String getExecName() {
        return execName;
    }

    public void setExecName(String execName) {
        this.execName = execName;
    }

    public String getExecPath() {
        return execPath;
    }

    public void setExecPath(String execPath) {
        this.execPath = execPath;
    }

    public Integer getMatchScore() {
        return matchScore;
    }

    public void setMatchScore(Integer matchScore) {
        this.matchScore = matchScore;
    }

    public String getMatchRule() {
        return matchRule;
    }

    public void setMatchRule(String matchRule) {
        this.matchRule = matchRule;
    }
}
