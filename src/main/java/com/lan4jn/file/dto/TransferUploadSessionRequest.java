package com.lan4jn.file.dto;

public class TransferUploadSessionRequest {
    private String fileName;
    private String originalFileName;
    private String targetFileName;
    private String contentType;
    private Long size;
    private String folder;
    private String bucket;
    private String uploadTraceId;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getTargetFileName() {
        return targetFileName;
    }

    public void setTargetFileName(String targetFileName) {
        this.targetFileName = targetFileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getFolder() {
        return folder;
    }

    public void setFolder(String folder) {
        this.folder = folder;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getUploadTraceId() {
        return uploadTraceId;
    }

    public void setUploadTraceId(String uploadTraceId) {
        this.uploadTraceId = uploadTraceId;
    }
}
