package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.service.AuthorizationService;
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

    public AdminRoleController(RoleService roleService, AuthorizationService authorizationService) {
        this.roleService = roleService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/roles")
    public Result<List<Role>> getAllRoles() {
        return Result.success(roleService.getAllRoles());
    }

    @GetMapping("/roles/{id}")
    public Result<Role> getRole(@PathVariable Long id) {
        return Result.success(roleService.getRoleById(id));
    }

    @PostMapping("/roles")
    public Result<Role> createRole(@RequestBody Role role) {
        role.setCode(RequestValidationUtils.requireText(role.getCode(), "code"));
        role.setName(RequestValidationUtils.requireText(role.getName(), "name"));
        role.setDescription(RequestValidationUtils.nullableText(role.getDescription(), "description"));
        return Result.success(roleService.createRole(role));
    }

    @PutMapping("/roles/{id}")
    public Result<Role> updateRole(@PathVariable Long id, @RequestBody Role role) {
        role.setCode(RequestValidationUtils.requireText(role.getCode(), "code"));
        role.setName(RequestValidationUtils.requireText(role.getName(), "name"));
        role.setDescription(RequestValidationUtils.nullableText(role.getDescription(), "description"));
        return Result.success(roleService.updateRole(id, role));
    }

    @DeleteMapping("/roles/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.success();
    }

    @PostMapping("/roles/{id}/permissions")
    public Result<Role> assignPermissions(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        return Result.success(authorizationService.assignPermissionsToRole(id, permissionIds));
    }
}
