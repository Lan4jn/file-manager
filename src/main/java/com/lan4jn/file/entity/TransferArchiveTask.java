package com.lan4jn.file.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "transfer_archive_task")
public class TransferArchiveTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String taskType;

    @Column(length = 32)
    private String scene;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, length = 32)
    private String stage;

    @Column(nullable = false, length = 128)
    private String ownerUsername;

    @Column(nullable = false, length = 255)
    private String bucketName;

    @Column(length = 4000)
    @JsonIgnore
    private String sourceDescriptor;

    private Integer fileCountTotal;

    private Integer fileCountProcessed;

    private Long bytesWritten;

    @Column(length = 255)
    private String archiveFileName;

    @Column(length = 1000)
    @JsonIgnore
    private String archiveLocation;

    @Column(length = 128)
    private String shareCode;

    private Long createTime;

    private Long finishTime;

    private Long expireTime;

    @Column(length = 2000)
    private String errorMessage;

    @Transient
    private String downloadUrl;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
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

    public String getSourceDescriptor() {
        return sourceDescriptor;
    }

    public void setSourceDescriptor(String sourceDescriptor) {
        this.sourceDescriptor = sourceDescriptor;
    }

    public Integer getFileCountTotal() {
        return fileCountTotal;
    }

    public void setFileCountTotal(Integer fileCountTotal) {
        this.fileCountTotal = fileCountTotal;
    }

    public Integer getFileCountProcessed() {
        return fileCountProcessed;
    }

    public void setFileCountProcessed(Integer fileCountProcessed) {
        this.fileCountProcessed = fileCountProcessed;
    }

    public Long getBytesWritten() {
        return bytesWritten;
    }

    public void setBytesWritten(Long bytesWritten) {
        this.bytesWritten = bytesWritten;
    }

    public String getArchiveFileName() {
        return archiveFileName;
    }

    public void setArchiveFileName(String archiveFileName) {
        this.archiveFileName = archiveFileName;
    }

    public String getArchiveLocation() {
        return archiveLocation;
    }

    public void setArchiveLocation(String archiveLocation) {
        this.archiveLocation = archiveLocation;
    }

    public String getShareCode() {
        return shareCode;
    }

    public void setShareCode(String shareCode) {
        this.shareCode = shareCode;
    }

    public Long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Long createTime) {
        this.createTime = createTime;
    }

    public Long getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(Long finishTime) {
        this.finishTime = finishTime;
    }

    public Long getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(Long expireTime) {
        this.expireTime = expireTime;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }
}
