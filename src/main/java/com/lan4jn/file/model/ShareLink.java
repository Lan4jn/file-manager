package com.lan4jn.file.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_share_link")
public class ShareLink {
    private static final String DEFAULT_TARGET_TYPE = "LINK";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String shareCode;
    private String objectName;
    private String bucket;
    private String fileName;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private String createBy;
    private Long viewCount;
    private Boolean hasPassword;
    private String password;
    private String targetType;
    private Boolean isFolder;  // 鏄惁鏄洰褰曞垎浜?
    private String zipFileName;  // 鍘嬬缉鍖呮枃浠跺悕
    private Boolean cancelled;
    private Boolean hiddenByCreator;

    @Transient
    private String recipientDisplayText;

    public String getShareCode() {
        return shareCode;
    }

    public void setShareCode(String shareCode) {
        this.shareCode = shareCode;
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

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    public Boolean getHasPassword() {
        return hasPassword;
    }

    public void setHasPassword(Boolean hasPassword) {
        this.hasPassword = hasPassword;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTargetType() {
        if (targetType == null || targetType.trim().isEmpty()) {
            return DEFAULT_TARGET_TYPE;
        }
        return targetType.trim().toUpperCase();
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public Boolean getIsFolder() {
        return isFolder;
    }

    public void setIsFolder(Boolean isFolder) {
        this.isFolder = isFolder;
    }

    public String getZipFileName() {
        return zipFileName;
    }

    public void setZipFileName(String zipFileName) {
        this.zipFileName = zipFileName;
    }

    public Boolean getCancelled() {
        return cancelled;
    }

    public void setCancelled(Boolean cancelled) {
        this.cancelled = cancelled;
    }

    public Boolean getHiddenByCreator() {
        return hiddenByCreator;
    }

    public void setHiddenByCreator(Boolean hiddenByCreator) {
        this.hiddenByCreator = hiddenByCreator;
    }

    public String getRecipientDisplayText() {
        return recipientDisplayText;
    }

    public void setRecipientDisplayText(String recipientDisplayText) {
        this.recipientDisplayText = recipientDisplayText;
    }
}
