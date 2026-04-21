package com.lan4jn.file.model;

import java.time.LocalDateTime;

public class BucketInfo {
    private String name;
    private LocalDateTime creationDate;
    private Long objectCount;
    private Long totalSize;
    private Boolean compatible;
    private String statsStatus;
    private String statsErrorMessage;
    private LocalDateTime statsUpdatedAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(LocalDateTime creationDate) {
        this.creationDate = creationDate;
    }

    public Long getObjectCount() {
        return objectCount;
    }

    public void setObjectCount(Long objectCount) {
        this.objectCount = objectCount;
    }

    public Long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(Long totalSize) {
        this.totalSize = totalSize;
    }

    public Boolean getCompatible() {
        return compatible;
    }

    public void setCompatible(Boolean compatible) {
        this.compatible = compatible;
    }

    public String getStatsStatus() {
        return statsStatus;
    }

    public void setStatsStatus(String statsStatus) {
        this.statsStatus = statsStatus;
    }

    public String getStatsErrorMessage() {
        return statsErrorMessage;
    }

    public void setStatsErrorMessage(String statsErrorMessage) {
        this.statsErrorMessage = statsErrorMessage;
    }

    public LocalDateTime getStatsUpdatedAt() {
        return statsUpdatedAt;
    }

    public void setStatsUpdatedAt(LocalDateTime statsUpdatedAt) {
        this.statsUpdatedAt = statsUpdatedAt;
    }
}
