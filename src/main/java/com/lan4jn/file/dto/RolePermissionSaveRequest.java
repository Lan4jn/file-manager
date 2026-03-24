package com.lan4jn.file.dto;

import java.util.ArrayList;
import java.util.List;

public class RolePermissionSaveRequest {

    private List<String> menuPermissions = new ArrayList<>();
    private List<String> apiPermissions = new ArrayList<>();
    private List<BucketPermissionDTO> bucketPermissions = new ArrayList<>();

    public List<String> getMenuPermissions() {
        return menuPermissions;
    }

    public void setMenuPermissions(List<String> menuPermissions) {
        this.menuPermissions = menuPermissions;
    }

    public List<String> getApiPermissions() {
        return apiPermissions;
    }

    public void setApiPermissions(List<String> apiPermissions) {
        this.apiPermissions = apiPermissions;
    }

    public List<BucketPermissionDTO> getBucketPermissions() {
        return bucketPermissions;
    }

    public void setBucketPermissions(List<BucketPermissionDTO> bucketPermissions) {
        this.bucketPermissions = bucketPermissions;
    }
}
