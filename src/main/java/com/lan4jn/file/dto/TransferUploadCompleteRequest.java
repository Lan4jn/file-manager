package com.lan4jn.file.dto;

public class TransferUploadCompleteRequest {
    private String uploadToken;
    private String uploadTraceId;

    public String getUploadToken() {
        return uploadToken;
    }

    public void setUploadToken(String uploadToken) {
        this.uploadToken = uploadToken;
    }

    public String getUploadTraceId() {
        return uploadTraceId;
    }

    public void setUploadTraceId(String uploadTraceId) {
        this.uploadTraceId = uploadTraceId;
    }
}
