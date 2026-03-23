package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.dto.UserDTO;
import com.lan4jn.file.entity.Permission;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.service.ConfigService;
import com.lan4jn.file.service.PermissionService;
import com.lan4jn.file.service.RoleService;
import com.lan4jn.file.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")

public class AdminController {

    @Autowired
    private UserService userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private ConfigService configService;

    // ==================== User Management ====================

    @GetMapping("/users")
    public Result<List<UserDTO>> getAllUsers() {
        return Result.success(userService.getAllUsers());
    }

    @GetMapping("/users/{id}")
    public Result<UserDTO> getUser(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    @PostMapping("/users")
    public Result<UserDTO> createUser(@RequestBody UserDTO userDTO) {
        return Result.success(userService.createUser(userDTO));
    }

    @PutMapping("/users/{id}")
    public Result<UserDTO> updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        return Result.success(userService.updateUser(id, userDTO));
    }

    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    @PostMapping("/users/{id}/password")
    public Result<Void> updatePassword(@PathVariable Long id,
                                        @RequestBody java.util.Map<String, String> passwordData) {
        userService.updatePassword(id, passwordData.get("oldPassword"), passwordData.get("newPassword"));
        return Result.success();
    }

    // ==================== Role Management ====================

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
        return Result.success(roleService.createRole(role));
    }

    @PutMapping("/roles/{id}")
    public Result<Role> updateRole(@PathVariable Long id, @RequestBody Role role) {
        return Result.success(roleService.updateRole(id, role));
    }

    @DeleteMapping("/roles/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.success();
    }

    @PostMapping("/roles/{id}/permissions")
    public Result<Role> assignPermissions(@PathVariable Long id, @RequestBody List<Long> permissionIds) {
        return Result.success(roleService.assignPermissions(id, permissionIds));
    }

    // ==================== Permission Management ====================

    @GetMapping("/permissions")
    public Result<List<Permission>> getAllPermissions() {
        return Result.success(permissionService.getAllPermissions());
    }

    @GetMapping("/permissions/tree")
    public Result<List<Permission>> getPermissionsTree() {
        return Result.success(permissionService.getPermissionsTree());
    }

    @GetMapping("/permissions/{id}")
    public Result<Permission> getPermission(@PathVariable Long id) {
        return Result.success(permissionService.getPermissionById(id));
    }

    @PostMapping("/permissions")
    public Result<Permission> createPermission(@RequestBody Permission permission) {
        return Result.success(permissionService.createPermission(permission));
    }

    @PutMapping("/permissions/{id}")
    public Result<Permission> updatePermission(@PathVariable Long id, @RequestBody Permission permission) {
        return Result.success(permissionService.updatePermission(id, permission));
    }

    @DeleteMapping("/permissions/{id}")
    public Result<Void> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return Result.success();
    }

    // ==================== File Config Management ====================

    @GetMapping("/file-config")
    public Result<Map<String, Object>> getFileConfig() {
        Set<String> extensions = configService.getAllowedExtensions();
        Long maxSize = configService.getMaxFileSize();

        Map<String, Object> config = new HashMap<>();
        config.put("allowedExtensions", extensions);
        config.put("maxFileSize", maxSize);

        return Result.success(config);
    }

    @PostMapping("/file-config")
    public Result<Void> updateFileConfig(@RequestBody Map<String, Object> configData) {
        if (configData.containsKey("allowedExtensions")) {
            List<String> extensions = (List<String>) configData.get("allowedExtensions");
            configService.setAllowedExtensions(extensions);
        }

        if (configData.containsKey("maxFileSize")) {
            Long maxSize = Long.valueOf(configData.get("maxFileSize").toString());
            configService.setMaxFileSize(maxSize);
        }

        return Result.success();
    }

    // ==================== Share Config Management ====================

    @GetMapping("/share-config")
    public Result<Map<String, Object>> getShareConfig() {
        Long batchShareMaxSize = configService.getBatchShareMaxSize();
        Long batchDownloadMaxSize = configService.getBatchDownloadMaxSize();
        Integer shareExpireHours = configService.getShareExpireHours();

        Map<String, Object> config = new HashMap<>();
        config.put("batchShareMaxSize", batchShareMaxSize);
        config.put("batchDownloadMaxSize", batchDownloadMaxSize);
        config.put("shareExpireHours", shareExpireHours);

        return Result.success(config);
    }

    @PostMapping("/share-config")
    public Result<Void> updateShareConfig(@RequestBody Map<String, Object> configData) {
        if (configData.containsKey("batchShareMaxSize")) {
            Long maxSize = Long.valueOf(configData.get("batchShareMaxSize").toString());
            configService.setBatchShareMaxSize(maxSize);
        }

        if (configData.containsKey("batchDownloadMaxSize")) {
            Long maxSize = Long.valueOf(configData.get("batchDownloadMaxSize").toString());
            configService.setBatchDownloadMaxSize(maxSize);
        }

        if (configData.containsKey("shareExpireHours")) {
            Integer hours = Integer.valueOf(configData.get("shareExpireHours").toString());
            configService.setShareExpireHours(hours);
        }

        return Result.success();
    }

    // ==================== Initialize ====================

    @PostMapping("/init")
    public Result<String> initialize() {
        // Create default roles first
        Role adminRole = roleService.getRoleByCode("ADMIN");
        if (adminRole == null) {
            adminRole = new Role();
            adminRole.setCode("ADMIN");
            adminRole.setName("绠＄悊鍛?");
            adminRole.setDescription("绯荤粺绠＄悊鍛?");
            adminRole.setCreateTime(System.currentTimeMillis());
            adminRole = roleService.createRole(adminRole);
        }

        Role userRole = roleService.getRoleByCode("USER");
        if (userRole == null) {
            userRole = new Role();
            userRole.setCode("USER");
            userRole.setName("鏅€氱敤鎴?");
            userRole.setDescription("鏅€氱敤鎴?");
            userRole.setCreateTime(System.currentTimeMillis());
            userRole = roleService.createRole(userRole);
        }

        // Create default permissions
        initDefaultPermissions();

        return Result.success("鍒濆鍖栧畬鎴愶紝绠＄悊鍛樿处鍙? admin/admin123");
    }

    private void initDefaultPermissions() {
        // File permissions
        if (permissionService.getPermissionByCode("file:upload") == null) {
            Permission p1 = new Permission();
            p1.setCode("file:upload");
            p1.setName("鏂囦欢涓婁紶");
            p1.setType("button");
            permissionService.createPermission(p1);
        }

        if (permissionService.getPermissionByCode("file:delete") == null) {
            Permission p2 = new Permission();
            p2.setCode("file:delete");
            p2.setName("鏂囦欢鍒犻櫎");
            p2.setType("button");
            permissionService.createPermission(p2);
        }

        if (permissionService.getPermissionByCode("file:download") == null) {
            Permission p3 = new Permission();
            p3.setCode("file:download");
            p3.setName("鏂囦欢涓嬭浇");
            p3.setType("button");
            permissionService.createPermission(p3);
        }

        // Share permissions
        if (permissionService.getPermissionByCode("share:create") == null) {
            Permission p4 = new Permission();
            p4.setCode("share:create");
            p4.setName("鍒涘缓鍒嗕韩");
            p4.setType("button");
            permissionService.createPermission(p4);
        }

        // User management
        if (permissionService.getPermissionByCode("user:manage") == null) {
            Permission p5 = new Permission();
            p5.setCode("user:manage");
            p5.setName("鐢ㄦ埛绠＄悊");
            p5.setType("menu");
            permissionService.createPermission(p5);
        }

        // Role management
        if (permissionService.getPermissionByCode("role:manage") == null) {
            Permission p6 = new Permission();
            p6.setCode("role:manage");
            p6.setName("瑙掕壊绠＄悊");
            p6.setType("menu");
            permissionService.createPermission(p6);
        }
    }
}

