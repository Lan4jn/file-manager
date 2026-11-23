package com.lan4jn.file.service;

import com.lan4jn.file.rbac.BucketAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;

@Service
public class PermissionAssertService {

    private static final Logger log = LoggerFactory.getLogger(PermissionAssertService.class);

    private final PermissionResolverService permissionResolverService;

    public PermissionAssertService(PermissionResolverService permissionResolverService) {
        this.permissionResolverService = permissionResolverService;
    }

    public PermissionSnapshot currentSnapshot() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            log.warn("Permission check rejected because authentication is missing");
            throw new SecurityException("未登录");
        }
        return permissionResolverService.resolveByUsername(authentication.getName());
    }

    public void assertApiPermission(String permissionCode) {
        PermissionSnapshot snapshot = currentSnapshot();
        if (snapshot.superAdmin() || snapshot.apiPermissions().contains(permissionCode)) {
            return;
        }
        log.warn("Permission denied for user {} on API permission {}", snapshot.username(), permissionCode);
        throw new SecurityException("缺少接口权限: " + permissionCode);
    }

    public void assertBucketPermission(String bucketName, String action) {
        PermissionSnapshot snapshot = currentSnapshot();
        if (snapshot.superAdmin()) {
            return;
        }

        Set<String> actions = snapshot.bucketPermissions().getOrDefault(bucketName, Collections.emptySet());
        Set<String> globalActions = snapshot.bucketPermissions().getOrDefault(BucketAction.ALL_BUCKETS, Collections.emptySet());
        boolean allowed;
        if (!actions.isEmpty()) {
            allowed = actions.contains(action) || actions.contains(BucketAction.ALL_ACTIONS);
        } else {
            allowed = globalActions.contains(action) || globalActions.contains(BucketAction.ALL_ACTIONS);
        }

        if (!allowed) {
            log.warn("Bucket permission denied for user {} on bucket {} action {}", snapshot.username(), bucketName, action);
            throw new SecurityException(buildBucketPermissionDeniedMessage(bucketName, action, actions, globalActions));
        }
    }

    private String buildBucketPermissionDeniedMessage(String bucketName,
                                                      String action,
                                                      Set<String> actions,
                                                      Set<String> globalActions) {
        if (!actions.isEmpty()) {
            return "缺少桶权限: bucket=" + bucketName
                    + ", action=" + action
                    + ", 当前桶权限=" + actions
                    + "；该桶存在专属权限配置，未回退全局权限 " + globalActions;
        }
        return "缺少桶权限: bucket=" + bucketName
                + ", action=" + action
                + ", 当前桶权限=[], 全局桶权限=" + globalActions;
    }

    public boolean hasMenuPermission(String permissionCode) {
        PermissionSnapshot snapshot = currentSnapshot();
        return snapshot.superAdmin() || snapshot.menuPermissions().contains(permissionCode);
    }

    public boolean isSuperAdmin() {
        return currentSnapshot().superAdmin();
    }
}
