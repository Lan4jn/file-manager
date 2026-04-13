package com.lan4jn.file.dto;

import java.util.ArrayList;
import java.util.List;

public class FileTransferPreviewRequest {

    private String operation;
    private String bucket;
    private List<String> sources = new ArrayList<>();
    private String targetFolder;
    private String conflictPolicy;

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources;
    }

    public String getTargetFolder() {
        return targetFolder;
    }

    public void setTargetFolder(String targetFolder) {
        this.targetFolder = targetFolder;
    }

    public String getConflictPolicy() {
        return conflictPolicy;
    }

    public void setConflictPolicy(String conflictPolicy) {
        this.conflictPolicy = conflictPolicy;
    }
}
