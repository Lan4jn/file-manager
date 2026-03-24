package com.lan4jn.file.dto;

import java.util.ArrayList;
import java.util.List;

public class BucketPermissionDTO {

    private String bucketName;
    private List<String> actions = new ArrayList<>();

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public List<String> getActions() {
        return actions;
    }

    public void setActions(List<String> actions) {
        this.actions = actions;
    }
}
