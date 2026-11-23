package com.lan4jn.file.dto;

public class FileRenameRequest {
    private String bucket;
    private String sourcePath;
    private String targetName;
    private String conflictPolicy;

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getConflictPolicy() {
        return conflictPolicy;
    }

    public void setConflictPolicy(String conflictPolicy) {
        this.conflictPolicy = conflictPolicy;
    }
}
