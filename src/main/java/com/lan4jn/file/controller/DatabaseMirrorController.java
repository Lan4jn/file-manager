package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.service.DatabaseMirrorService;
import com.lan4jn.file.service.PermissionAssertService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/database-mirrors")
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnProperty(name = "app.datasource-mirror.enabled", havingValue = "true")
public class DatabaseMirrorController {

    private final DatabaseMirrorService databaseMirrorService;
    private final PermissionAssertService permissionAssertService;

    public DatabaseMirrorController(DatabaseMirrorService databaseMirrorService,
                                    PermissionAssertService permissionAssertService) {
        this.databaseMirrorService = databaseMirrorService;
        this.permissionAssertService = permissionAssertService;
    }

    @GetMapping("/status")
    public Result<DatabaseMirrorService.MirrorStatus> status() {
        ensureSuperAdmin();
        return Result.success(databaseMirrorService.getStatus());
    }

    @PostMapping("/sync")
    public Result<Boolean> synchronize() {
        ensureSuperAdmin();
        return Result.success(databaseMirrorService.synchronizeNow());
    }

    private void ensureSuperAdmin() {
        if (!permissionAssertService.isSuperAdmin()) {
            throw new SecurityException("数据库镜像管理仅超级管理员可访问");
        }
    }
}
