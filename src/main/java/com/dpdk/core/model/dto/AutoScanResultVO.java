package com.dpdk.core.model.dto;

import java.util.ArrayList;
import java.util.List;

public class AutoScanResultVO {
    private String scanRoot;
    private Integer totalSources;
    private Integer matchedSources;
    private List<AutoScanCandidateVO> candidates = new ArrayList<>();
    private List<String> warnings = new ArrayList<>();

    public String getScanRoot() {
        return scanRoot;
    }

    public void setScanRoot(String scanRoot) {
        this.scanRoot = scanRoot;
    }

    public Integer getTotalSources() {
        return totalSources;
    }

    public void setTotalSources(Integer totalSources) {
        this.totalSources = totalSources;
    }

    public Integer getMatchedSources() {
        return matchedSources;
    }

    public void setMatchedSources(Integer matchedSources) {
        this.matchedSources = matchedSources;
    }

    public List<AutoScanCandidateVO> getCandidates() {
        return candidates;
    }

    public void setCandidates(List<AutoScanCandidateVO> candidates) {
        this.candidates = candidates;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
