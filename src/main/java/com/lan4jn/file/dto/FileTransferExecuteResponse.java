package com.lan4jn.file.dto;

import java.util.ArrayList;
import java.util.List;

public class FileTransferExecuteResponse {

    private int successCount;
    private int failedCount;
    private List<String> resolvedTargets = new ArrayList<>();
    private List<String> failedSources = new ArrayList<>();

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public List<String> getResolvedTargets() {
        return resolvedTargets;
    }

    public void setResolvedTargets(List<String> resolvedTargets) {
        this.resolvedTargets = resolvedTargets;
    }

    public List<String> getFailedSources() {
        return failedSources;
    }

    public void setFailedSources(List<String> failedSources) {
        this.failedSources = failedSources;
    }
}
