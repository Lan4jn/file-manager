package com.lan4jn.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transfer_upload_session")
public class TransferUploadSession {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 128)
    private String ownerUsername;

    @Column(nullable = false, length = 255)
    private String bucketName;

    @Column(nullable = false, length = 1000)
    private String objectName;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(length = 255)
    private String contentType;

    @Column(nullable = false)
    private Long totalSize;

    @Column(nullable = false)
    private Long partSize;

    @Column(nullable = false)
    private Integer totalParts;

    @Column(nullable = false, length = 255)
    private String minioUploadId;

    @Column(nullable = false, length = 255)
    private String fileFingerprint;

    @Column(nullable = false, length = 32)
    private String status;

    private Long createTime;
    private Long updateTime;
    private Long expireTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
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

    public Long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(Long totalSize) {
        this.totalSize = totalSize;
    }

    public Long getPartSize() {
        return partSize;
    }

    public void setPartSize(Long partSize) {
        this.partSize = partSize;
    }

    public Integer getTotalParts() {
        return totalParts;
    }

    public void setTotalParts(Integer totalParts) {
        this.totalParts = totalParts;
    }

    public String getMinioUploadId() {
        return minioUploadId;
    }

    public void setMinioUploadId(String minioUploadId) {
        this.minioUploadId = minioUploadId;
    }

    public String getFileFingerprint() {
        return fileFingerprint;
    }

    public void setFileFingerprint(String fileFingerprint) {
        this.fileFingerprint = fileFingerprint;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Long updateTime) {
        this.updateTime = updateTime;
    }

    public Long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Long expireTime) {
        this.expireTime = expireTime;
    }
}
