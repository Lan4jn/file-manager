package com.lan4jn.file.model;

import java.time.LocalDateTime;

public class BucketStatsRefreshStatus {

    private String status = "IDLE";
    private int processedBuckets;
    private int totalBuckets;
    private String currentBucket;
    private String message;
    private LocalDateTime lastFinishedAt;

    public BucketStatsRefreshStatus() {
    }

    public BucketStatsRefreshStatus(BucketStatsRefreshStatus source) {
        this.status = source.status;
        this.processedBuckets = source.processedBuckets;
        this.totalBuckets = source.totalBuckets;
        this.currentBucket = source.currentBucket;
        this.message = source.message;
        this.lastFinishedAt = source.lastFinishedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getProcessedBuckets() {
        return processedBuckets;
    }

    public void setProcessedBuckets(int processedBuckets) {
        this.processedBuckets = processedBuckets;
    }

    public int getTotalBuckets() {
        return totalBuckets;
    }

    public void setTotalBuckets(int totalBuckets) {
        this.totalBuckets = totalBuckets;
    }

    public String getCurrentBucket() {
        return currentBucket;
    }

    public void setCurrentBucket(String currentBucket) {
        this.currentBucket = currentBucket;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public LocalDateTime getLastFinishedAt() {
        return lastFinishedAt;
    }

    public void setLastFinishedAt(LocalDateTime lastFinishedAt) {
        this.lastFinishedAt = lastFinishedAt;
    }
}
