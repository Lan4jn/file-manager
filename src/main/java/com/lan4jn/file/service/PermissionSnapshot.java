package com.lan4jn.file.service;

import java.util.Map;
import java.util.Set;

public record PermissionSnapshot(
        Long userId,
        String username,
        boolean superAdmin,
        Set<String> roles,
        Set<String> menuPermissions,
        Set<String> apiPermissions,
        Map<String, Set<String>> bucketPermissions
) {
}
