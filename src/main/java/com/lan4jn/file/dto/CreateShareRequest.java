package com.lan4jn.file.dto;

import java.util.List;

public class CreateShareRequest {
    private String objectName;
    private String bucket;
    private Integer expirationHours;
    private String password;
    private String targetType;
    private List<Long> recipientUserIds;

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

    public Integer getExpirationHours() {
        return expirationHours;
    }

    public void setExpirationHours(Integer expirationHours) {
        this.expirationHours = expirationHours;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public List<Long> getRecipientUserIds() {
        return recipientUserIds;
    }

    public void setRecipientUserIds(List<Long> recipientUserIds) {
        this.recipientUserIds = recipientUserIds;
    }
}
