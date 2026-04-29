package com.dpdk.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.auto-scan")
public class AutoScanProperties {

    private boolean enabled = true;
    private String scanRoot = "/home/hnhyh/workspace/core";
    private int maxDepth = 6;
    private int headerProbeLines = 80;
    private List<String> elfSearchRoots = new ArrayList<>(List.of(
            ".",
            "elf",
            "bin",
            "build",
            "target"
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getScanRoot() {
        return scanRoot;
    }

    public void setScanRoot(String scanRoot) {
        this.scanRoot = scanRoot;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public int getHeaderProbeLines() {
        return headerProbeLines;
    }

    public void setHeaderProbeLines(int headerProbeLines) {
        this.headerProbeLines = headerProbeLines;
    }

    public List<String> getElfSearchRoots() {
        return elfSearchRoots;
    }

    public void setElfSearchRoots(List<String> elfSearchRoots) {
        this.elfSearchRoots = elfSearchRoots;
    }
}
