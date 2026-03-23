package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.entity.Permission;
import com.lan4jn.file.service.AuthorizationService;
import com.lan4jn.file.service.PermissionService;
import com.lan4jn.file.utils.RequestValidationUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPermissionController {

    private final PermissionService permissionService;
    private final AuthorizationService authorizationService;

    public AdminPermissionController(PermissionService permissionService, AuthorizationService authorizationService) {
        this.permissionService = permissionService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/permissions")
    public Result<List<Permission>> getAllPermissions() {
        return Result.success(permissionService.getAllPermissions());
    }

    @GetMapping("/permissions/tree")
    public Result<List<Permission>> getPermissionsTree() {
        return Result.success(authorizationService.getPermissionTree());
    }

    @GetMapping("/permissions/{id}")
    public Result<Permission> getPermission(@PathVariable Long id) {
        return Result.success(permissionService.getPermissionById(id));
    }

    @PostMapping("/permissions")
    public Result<Permission> createPermission(@RequestBody Permission permission) {
        permission.setCode(RequestValidationUtils.requireText(permission.getCode(), "code"));
        permission.setName(RequestValidationUtils.requireText(permission.getName(), "name"));
        permission.setType(RequestValidationUtils.requireText(permission.getType(), "type"));
        permission.setDescription(RequestValidationUtils.nullableText(permission.getDescription(), "description"));
        permission.setUrl(RequestValidationUtils.nullableText(permission.getUrl(), "url"));
        permission.setParentCode(RequestValidationUtils.nullableText(permission.getParentCode(), "parentCode"));
        return Result.success(permissionService.createPermission(permission));
    }

    @PutMapping("/permissions/{id}")
    public Result<Permission> updatePermission(@PathVariable Long id, @RequestBody Permission permission) {
        permission.setCode(RequestValidationUtils.requireText(permission.getCode(), "code"));
        permission.setName(RequestValidationUtils.requireText(permission.getName(), "name"));
        permission.setType(RequestValidationUtils.requireText(permission.getType(), "type"));
        permission.setDescription(RequestValidationUtils.nullableText(permission.getDescription(), "description"));
        permission.setUrl(RequestValidationUtils.nullableText(permission.getUrl(), "url"));
        permission.setParentCode(RequestValidationUtils.nullableText(permission.getParentCode(), "parentCode"));
        return Result.success(permissionService.updatePermission(id, permission));
    }

    @DeleteMapping("/permissions/{id}")
    public Result<Void> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return Result.success();
    }
}
