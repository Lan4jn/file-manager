package com.poss.file.model;

import java.time.LocalDateTime;

public class ShareAccessInfo {
    private String shareCode;
    private String fileName;
    private Boolean hasPassword;
    private Boolean isFolder;
    private LocalDateTime expireTime;

    public String getShareCode() {
        return shareCode;
    }

    public void setShareCode(String shareCode) {
        this.shareCode = shareCode;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Boolean getHasPassword() {
        return hasPassword;
    }

    public void setHasPassword(Boolean hasPassword) {
        this.hasPassword = hasPassword;
    }

    public Boolean getIsFolder() {
        return isFolder;
    }

    public void setIsFolder(Boolean isFolder) {
        this.isFolder = isFolder;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }
}
