package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.entity.Permission;
import com.lan4jn.file.rbac.PermissionCodes;
import com.lan4jn.file.service.AuthorizationService;
import com.lan4jn.file.service.PermissionAssertService;
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
    private final PermissionAssertService permissionAssertService;

    public AdminPermissionController(PermissionService permissionService,
                                     AuthorizationService authorizationService,
                                     PermissionAssertService permissionAssertService) {
        this.permissionService = permissionService;
        this.authorizationService = authorizationService;
        this.permissionAssertService = permissionAssertService;
    }

    @GetMapping("/permissions")
    public Result<List<Permission>> getAllPermissions() {
        permissionAssertService.assertApiPermission(PermissionCodes.API_PERMISSION_READ);
        return Result.success(permissionService.getAllPermissions());
    }

    @GetMapping("/permissions/tree")
    public Result<List<Permission>> getPermissionsTree() {
        permissionAssertService.assertApiPermission(PermissionCodes.API_PERMISSION_READ);
        return Result.success(authorizationService.getPermissionTree());
    }

    @GetMapping("/permissions/{id}")
    public Result<Permission> getPermission(@PathVariable Long id) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_PERMISSION_READ);
        return Result.success(permissionService.getPermissionById(id));
    }

    @PostMapping("/permissions")
    public Result<Permission> createPermission(@RequestBody Permission permission) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_ROLE_MANAGE);
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
        permissionAssertService.assertApiPermission(PermissionCodes.API_ROLE_MANAGE);
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
        permissionAssertService.assertApiPermission(PermissionCodes.API_ROLE_MANAGE);
        permissionService.deletePermission(id);
        return Result.success();
    }
}
