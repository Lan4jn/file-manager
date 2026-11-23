package com.lan4jn.file.dto;

import com.lan4jn.file.model.TransportMode;

import java.util.Map;

public class TransferUploadSessionResponse {
    private TransportMode mode;
    private String uploadUrl;
    private String uploadToken;
    private String objectName;
    private String bucket;
    private String fileName;
    private String contentType;
    private String uploadTraceId;
    private Map<String, String> headers;

    public TransportMode getMode() {
        return mode;
    }

    public void setMode(TransportMode mode) {
        this.mode = mode;
    }

    public String getUploadUrl() {
        return uploadUrl;
    }

    public void setUploadUrl(String uploadUrl) {
        this.uploadUrl = uploadUrl;
    }

    public String getUploadToken() {
        return uploadToken;
    }

    public void setUploadToken(String uploadToken) {
        this.uploadToken = uploadToken;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getUploadTraceId() {
        return uploadTraceId;
    }

    public void setUploadTraceId(String uploadTraceId) {
        this.uploadTraceId = uploadTraceId;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }
}
