package com.lan4jn.file.dto;

import java.util.ArrayList;
import java.util.List;

public class CurrentPermissionInfoDTO {

    private Long userId;
    private String username;
    private boolean superAdmin;
    private String[] roles = new String[0];
    private String[] menuPermissions = new String[0];
    private String[] apiPermissions = new String[0];
    private List<BucketPermissionDTO> bucketPermissions = new ArrayList<>();

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isSuperAdmin() {
        return superAdmin;
    }

    public void setSuperAdmin(boolean superAdmin) {
        this.superAdmin = superAdmin;
    }

    public String[] getRoles() {
        return roles;
    }

    public void setRoles(String[] roles) {
        this.roles = roles;
    }

    public String[] getMenuPermissions() {
        return menuPermissions;
    }

    public void setMenuPermissions(String[] menuPermissions) {
        this.menuPermissions = menuPermissions;
    }

    public String[] getApiPermissions() {
        return apiPermissions;
    }

    public void setApiPermissions(String[] apiPermissions) {
        this.apiPermissions = apiPermissions;
    }

    public List<BucketPermissionDTO> getBucketPermissions() {
        return bucketPermissions;
    }

    public void setBucketPermissions(List<BucketPermissionDTO> bucketPermissions) {
        this.bucketPermissions = bucketPermissions;
    }
}
