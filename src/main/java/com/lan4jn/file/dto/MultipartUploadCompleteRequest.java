package com.lan4jn.file.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class MultipartUploadCompleteRequest {
    private List<PartItem> parts;
    private String uploadTraceId;

    public List<PartItem> getParts() { return parts; }
    public void setParts(List<PartItem> parts) { this.parts = parts; }
    public String getUploadTraceId() { return uploadTraceId; }
    public void setUploadTraceId(String uploadTraceId) { this.uploadTraceId = uploadTraceId; }

    public static class PartItem {
        private int partNumber;
        private String eTag;

        public int getPartNumber() { return partNumber; }
        public void setPartNumber(int partNumber) { this.partNumber = partNumber; }

        @JsonProperty("eTag")
        public String getETag() { return eTag; }
        public void setETag(String eTag) { this.eTag = eTag; }
    }
}
