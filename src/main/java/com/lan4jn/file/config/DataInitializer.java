package com.lan4jn.file.config;

import com.lan4jn.file.entity.Permission;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.entity.RoleBucketPermission;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.repository.PermissionRepository;
import com.lan4jn.file.repository.RoleBucketPermissionRepository;
import com.lan4jn.file.repository.RoleRepository;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.rbac.PermissionCodes;
import com.lan4jn.file.rbac.PermissionType;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleBucketPermissionRepository roleBucketPermissionRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PermissionRepository permissionRepository,
                           RoleBucketPermissionRepository roleBucketPermissionRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.roleBucketPermissionRepository = roleBucketPermissionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Role adminRole = roleRepository.findByCode("ADMIN").orElseGet(() -> {
            Role role = new Role();
            role.setCode("ADMIN");
            role.setName("管理员");
            role.setDescription("系统管理员");
            role.setCreateTime(System.currentTimeMillis());
            return roleRepository.save(role);
        });

        Role userRole = roleRepository.findByCode("USER").orElseGet(() -> {
            Role role = new Role();
            role.setCode("USER");
            role.setName("普通用户");
            role.setDescription("普通用户");
            role.setCreateTime(System.currentTimeMillis());
            return roleRepository.save(role);
        });

        boolean adminChanged = false;
        adminChanged |= grant(adminRole, PermissionCodes.MENU_BUCKETS, "存储桶管理", PermissionType.MENU);
        adminChanged |= grant(adminRole, PermissionCodes.MENU_FILES, "文件管理", PermissionType.MENU);
        adminChanged |= grant(adminRole, PermissionCodes.MENU_SHARES, "分享管理", PermissionType.MENU);
        adminChanged |= grant(adminRole, PermissionCodes.MENU_USERS, "用户管理", PermissionType.MENU);
        adminChanged |= grant(adminRole, PermissionCodes.MENU_ROLES, "角色管理", PermissionType.MENU);
        adminChanged |= grant(adminRole, PermissionCodes.MENU_MINIO_KEYS, "MinIO密钥", PermissionType.MENU);
        adminChanged |= grant(adminRole, PermissionCodes.MENU_CONFIG, "系统配置", PermissionType.MENU);
        adminChanged |= grant(adminRole, PermissionCodes.MENU_AUDIT_LOG, "审计日志", PermissionType.MENU);
        adminChanged |= grant(adminRole, PermissionCodes.API_BUCKET_MANAGE, "存储桶接口权限", PermissionType.API);
        adminChanged |= grant(adminRole, PermissionCodes.API_FILE_BROWSE, "文件浏览接口权限", PermissionType.API);
        adminChanged |= grant(adminRole, PermissionCodes.API_FILE_OPERATE, "文件操作接口权限", PermissionType.API);
        adminChanged |= grant(adminRole, PermissionCodes.API_SHARE_MANAGE, "分享接口权限", PermissionType.API);
        adminChanged |= grant(adminRole, PermissionCodes.API_USER_MANAGE, "用户接口权限", PermissionType.API);
        adminChanged |= grant(adminRole, PermissionCodes.API_ROLE_MANAGE, "角色接口权限", PermissionType.API);
        adminChanged |= grant(adminRole, PermissionCodes.API_MINIO_KEY_MANAGE, "MinIO密钥接口权限", PermissionType.API);
        adminChanged |= grant(adminRole, PermissionCodes.API_CONFIG_MANAGE, "系统配置接口权限", PermissionType.API);
        adminChanged |= grant(adminRole, PermissionCodes.API_PERMISSION_READ, "权限读取接口权限", PermissionType.API);
        adminChanged |= grant(adminRole, PermissionCodes.API_AUDIT_LOG_READ, "审计日志读取接口权限", PermissionType.API);
        adminChanged |= grant(adminRole, PermissionCodes.SYSTEM_CONFIG_ACCESS, "系统配置专属权限", PermissionType.SYSTEM);
        if (adminChanged) {
            roleRepository.save(adminRole);
        }

        boolean userChanged = false;
        userChanged |= grant(userRole, PermissionCodes.MENU_BUCKETS, "存储桶管理", PermissionType.MENU);
        userChanged |= grant(userRole, PermissionCodes.MENU_FILES, "文件管理", PermissionType.MENU);
        userChanged |= grant(userRole, PermissionCodes.MENU_SHARES, "分享管理", PermissionType.MENU);
        userChanged |= grant(userRole, PermissionCodes.API_FILE_BROWSE, "文件浏览接口权限", PermissionType.API);
        userChanged |= grant(userRole, PermissionCodes.API_FILE_OPERATE, "文件操作接口权限", PermissionType.API);
        userChanged |= grant(userRole, PermissionCodes.API_SHARE_MANAGE, "分享接口权限", PermissionType.API);
        if (userChanged) {
            roleRepository.save(userRole);
        }

        grantDefaultBucketPermissionIfMissing(adminRole);
        grantDefaultBucketPermissionIfMissing(userRole);

        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setNickname("Administrator");
            admin.setEnabled(true);
            admin.setCreateTime(System.currentTimeMillis());
            admin.setRoles(new HashSet<>(Set.of(adminRole)));
            userRepository.save(admin);
        }
    }

    private boolean grant(Role role, String code, String name, String type) {
        final boolean[] permissionChanged = {false};
        Permission permission = permissionRepository.findByCode(code).map(entity -> {
            if (!name.equals(entity.getName()) || !type.equals(entity.getType())) {
                entity.setName(name);
                entity.setType(type);
                permissionChanged[0] = true;
                return permissionRepository.save(entity);
            }
            return entity;
        }).orElseGet(() -> {
            Permission entity = new Permission();
            entity.setCode(code);
            entity.setName(name);
            entity.setType(type);
            permissionChanged[0] = true;
            return permissionRepository.save(entity);
        });
        boolean alreadyGranted = role.getPermissions().stream()
                .anyMatch(item -> code.equals(item.getCode()));
        if (!alreadyGranted) {
            role.getPermissions().add(permission);
            return true;
        }
        return permissionChanged[0];
    }

    private void grantDefaultBucketPermissionIfMissing(Role role) {
        if (role.getId() == null || roleBucketPermissionRepository.existsByRoleId(role.getId())) {
            return;
        }
        RoleBucketPermission permission = new RoleBucketPermission();
        permission.setRole(role);
        permission.setBucketName("*");
        permission.setAction("*");
        roleBucketPermissionRepository.save(permission);
    }
}
