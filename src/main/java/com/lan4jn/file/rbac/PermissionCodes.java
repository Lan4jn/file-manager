package com.lan4jn.file.rbac;

import java.util.List;

public final class PermissionCodes {

    public static final String MENU_BUCKETS = "MENU_BUCKETS";
    public static final String MENU_FILES = "MENU_FILES";
    public static final String MENU_SHARES = "MENU_SHARES";
    public static final String MENU_USERS = "MENU_USERS";
    public static final String MENU_ROLES = "MENU_ROLES";
    public static final String MENU_MINIO_KEYS = "MENU_MINIO_KEYS";
    public static final String MENU_CONFIG = "MENU_CONFIG";
    public static final String MENU_AUDIT_LOG = "MENU_AUDIT_LOG";

    public static final String API_BUCKET_MANAGE = "API_BUCKET_MANAGE";
    public static final String API_FILE_BROWSE = "API_FILE_BROWSE";
    public static final String API_FILE_OPERATE = "API_FILE_OPERATE";
    public static final String API_SHARE_MANAGE = "API_SHARE_MANAGE";
    public static final String API_USER_MANAGE = "API_USER_MANAGE";
    public static final String API_ROLE_MANAGE = "API_ROLE_MANAGE";
    public static final String API_MINIO_KEY_MANAGE = "API_MINIO_KEY_MANAGE";
    public static final String API_CONFIG_MANAGE = "API_CONFIG_MANAGE";
    public static final String API_PERMISSION_READ = "API_PERMISSION_READ";
    public static final String API_AUDIT_LOG_READ = "API_AUDIT_LOG_READ";

    public static final String SYSTEM_CONFIG_ACCESS = "SYSTEM_CONFIG_ACCESS";

    public static final List<String> ALL_MENU_CODES = List.of(
            MENU_BUCKETS, MENU_FILES, MENU_SHARES, MENU_USERS, MENU_ROLES, MENU_MINIO_KEYS, MENU_CONFIG, MENU_AUDIT_LOG
    );

    public static final List<String> ALL_API_CODES = List.of(
            API_BUCKET_MANAGE, API_FILE_BROWSE, API_FILE_OPERATE, API_SHARE_MANAGE,
            API_USER_MANAGE, API_ROLE_MANAGE, API_MINIO_KEY_MANAGE, API_CONFIG_MANAGE, API_PERMISSION_READ,
            API_AUDIT_LOG_READ
    );

    private PermissionCodes() {
    }
}
