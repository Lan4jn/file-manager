package com.lan4jn.file.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class MultipartUploadInitResponse {
    private final String sessionId;
    private final String bucket;
    private final String objectName;
    private final String fileName;
    private final long partSize;
    private final int totalParts;
    private final boolean resumed;
    private final List<UploadedPart> uploadedParts;

    public MultipartUploadInitResponse(String sessionId, String bucket, String objectName, String fileName,
                                       long partSize, int totalParts, boolean resumed, List<UploadedPart> uploadedParts) {
        this.sessionId = sessionId;
        this.bucket = bucket;
        this.objectName = objectName;
        this.fileName = fileName;
        this.partSize = partSize;
        this.totalParts = totalParts;
        this.resumed = resumed;
        this.uploadedParts = uploadedParts;
    }

    public String getSessionId() { return sessionId; }
    public String getBucket() { return bucket; }
    public String getObjectName() { return objectName; }
    public String getFileName() { return fileName; }
    public long getPartSize() { return partSize; }
    public int getTotalParts() { return totalParts; }
    public boolean isResumed() { return resumed; }
    public List<UploadedPart> getUploadedParts() { return uploadedParts; }

    public static class UploadedPart {
        private final int partNumber;
        private final String eTag;

        public UploadedPart(int partNumber, String eTag) {
            this.partNumber = partNumber;
            this.eTag = eTag;
        }

        public int getPartNumber() { return partNumber; }

        @JsonProperty("eTag")
        public String getETag() { return eTag; }
    }
}
