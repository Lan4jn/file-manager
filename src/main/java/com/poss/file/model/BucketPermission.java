package com.poss.file.model;

public class BucketPermission {
    private String bucket;
    private Boolean canRead;
    private Boolean canWrite;
    private Boolean canDelete;

    public BucketPermission() {}

    public BucketPermission(String bucket, Boolean canRead, Boolean canWrite, Boolean canDelete) {
        this.bucket = bucket;
        this.canRead = canRead;
        this.canWrite = canWrite;
        this.canDelete = canDelete;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public Boolean getCanRead() {
        return canRead;
    }

    public void setCanRead(Boolean canRead) {
        this.canRead = canRead;
    }

    public Boolean getCanWrite() {
        return canWrite;
    }

    public void setCanWrite(Boolean canWrite) {
        this.canWrite = canWrite;
    }

    public Boolean getCanDelete() {
        return canDelete;
    }

    public void setCanDelete(Boolean canDelete) {
        this.canDelete = canDelete;
    }
}
