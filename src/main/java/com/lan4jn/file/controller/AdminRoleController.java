package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.dto.RolePermissionDetailDTO;
import com.lan4jn.file.dto.RolePermissionSaveRequest;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.rbac.PermissionCodes;
import com.lan4jn.file.service.AuthorizationService;
import com.lan4jn.file.service.PermissionAssertService;
import com.lan4jn.file.service.RoleService;
import com.lan4jn.file.utils.RequestValidationUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminRoleController {

    private final RoleService roleService;
    private final AuthorizationService authorizationService;
    private final PermissionAssertService permissionAssertService;

    public AdminRoleController(RoleService roleService,
                               AuthorizationService authorizationService,
                               PermissionAssertService permissionAssertService) {
        this.roleService = roleService;
        this.authorizationService = authorizationService;
        this.permissionAssertService = permissionAssertService;
    }

    @GetMapping("/roles")
    public Result<List<Role>> getAllRoles() {
        permissionAssertService.assertApiPermission(PermissionCodes.API_ROLE_MANAGE);
        return Result.success(roleService.getAllRoles());
    }

    @GetMapping("/roles/{id}")
    public Result<Role> getRole(@PathVariable Long id) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_ROLE_MANAGE);
        return Result.success(roleService.getRoleById(id));
    }

    @PostMapping("/roles")
    public Result<Role> createRole(@RequestBody Role role) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_ROLE_MANAGE);
        role.setCode(RequestValidationUtils.requireText(role.getCode(), "code"));
        role.setName(RequestValidationUtils.requireText(role.getName(), "name"));
        role.setDescription(RequestValidationUtils.nullableText(role.getDescription(), "description"));
        return Result.success(roleService.createRole(role));
    }

    @PutMapping("/roles/{id}")
    public Result<Role> updateRole(@PathVariable Long id, @RequestBody Role role) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_ROLE_MANAGE);
        role.setCode(RequestValidationUtils.requireText(role.getCode(), "code"));
        role.setName(RequestValidationUtils.requireText(role.getName(), "name"));
        role.setDescription(RequestValidationUtils.nullableText(role.getDescription(), "description"));
        return Result.success(roleService.updateRole(id, role));
    }

    @DeleteMapping("/roles/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_ROLE_MANAGE);
        roleService.deleteRole(id);
        return Result.success();
    }

    @GetMapping("/roles/{id}/permissions-detail")
    public Result<RolePermissionDetailDTO> getRolePermissionDetail(@PathVariable Long id) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_ROLE_MANAGE);
        return Result.success(authorizationService.getRolePermissionDetail(id));
    }

    @PostMapping("/roles/{id}/permissions-detail")
    public Result<RolePermissionDetailDTO> saveRolePermissionDetail(@PathVariable Long id,
                                                                    @RequestBody RolePermissionSaveRequest request) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_ROLE_MANAGE);
        return Result.success(authorizationService.saveRolePermissions(id, request));
    }

    @PostMapping("/roles/{id}/permissions")
    public Result<Role> assignPermissions(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        permissionAssertService.assertApiPermission(PermissionCodes.API_ROLE_MANAGE);
        return Result.success(authorizationService.assignPermissionsToRole(id, permissionIds));
    }
}
