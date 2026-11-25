package com.lan4jn.file.dto;

public class MultipartUploadInitRequest {
    private String fileName;
    private String targetFileName;
    private String folder;
    private String bucket;
    private String contentType;
    private Long size;
    private String fileFingerprint;
    private Long partSize;

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getTargetFileName() { return targetFileName; }
    public void setTargetFileName(String targetFileName) { this.targetFileName = targetFileName; }
    public String getFolder() { return folder; }
    public void setFolder(String folder) { this.folder = folder; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public String getFileFingerprint() { return fileFingerprint; }
    public void setFileFingerprint(String fileFingerprint) { this.fileFingerprint = fileFingerprint; }
    public Long getPartSize() { return partSize; }
    public void setPartSize(Long partSize) { this.partSize = partSize; }
}
