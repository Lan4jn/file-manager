package com.lan4jn.file.dto;

import com.lan4jn.file.model.TransportMode;

public class TransferDownloadSessionResponse {
    private TransportMode mode;
    private String fileName;
    private String downloadUrl;
    private Boolean useTask;
    private String taskId;

    public TransportMode getMode() {
        return mode;
    }

    public void setMode(TransportMode mode) {
        this.mode = mode;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public Boolean getUseTask() {
        return useTask;
    }

    public void setUseTask(Boolean useTask) {
        this.useTask = useTask;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }
}
