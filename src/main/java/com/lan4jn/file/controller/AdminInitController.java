package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.entity.Permission;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.service.PermissionService;
import com.lan4jn.file.service.RoleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminInitController {

    private final RoleService roleService;
    private final PermissionService permissionService;

    public AdminInitController(RoleService roleService, PermissionService permissionService) {
        this.roleService = roleService;
        this.permissionService = permissionService;
    }

    @PostMapping("/init")
    public Result<String> initialize() {
        Role adminRole = roleService.getRoleByCode("ADMIN");
        if (adminRole == null) {
            adminRole = new Role();
            adminRole.setCode("ADMIN");
            adminRole.setName("管理员");
            adminRole.setDescription("系统管理员");
            adminRole.setCreateTime(System.currentTimeMillis());
            roleService.createRole(adminRole);
        }

        Role userRole = roleService.getRoleByCode("USER");
        if (userRole == null) {
            userRole = new Role();
            userRole.setCode("USER");
            userRole.setName("普通用户");
            userRole.setDescription("普通用户");
            userRole.setCreateTime(System.currentTimeMillis());
            roleService.createRole(userRole);
        }

        initDefaultPermissions();
        return Result.success("初始化完成，默认管理员账户 admin/admin123");
    }

    private void initDefaultPermissions() {
        createPermissionIfAbsent("file:upload", "文件上传", "button");
        createPermissionIfAbsent("file:delete", "文件删除", "button");
        createPermissionIfAbsent("file:download", "文件下载", "button");
        createPermissionIfAbsent("share:create", "创建分享", "button");
        createPermissionIfAbsent("user:manage", "用户管理", "menu");
        createPermissionIfAbsent("role:manage", "角色管理", "menu");
    }

    private void createPermissionIfAbsent(String code, String name, String type) {
        if (permissionService.getPermissionByCode(code) != null) {
            return;
        }
        Permission permission = new Permission();
        permission.setCode(code);
        permission.setName(name);
        permission.setType(type);
        permissionService.createPermission(permission);
    }
}
