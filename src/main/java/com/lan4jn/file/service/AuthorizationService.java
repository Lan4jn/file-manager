package com.lan4jn.file.service;

import com.lan4jn.file.entity.Permission;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.repository.PermissionRepository;
import com.lan4jn.file.repository.RoleRepository;
import com.lan4jn.file.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AuthorizationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public AuthorizationService(UserRepository userRepository,
                                RoleRepository roleRepository,
                                PermissionRepository permissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    public Set<Role> resolveRoles(String[] roleCodes) {
        Set<Role> roles = new HashSet<>();
        if (roleCodes == null) {
            return roles;
        }
        for (String roleCode : roleCodes) {
            roleRepository.findByCode(roleCode).ifPresent(roles::add);
        }
        return roles;
    }

    @Transactional
    public User assignRolesToUser(Long userId, String[] roleCodes) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setRoles(resolveRoles(roleCodes));
        return userRepository.save(user);
    }

    @Transactional
    public Role assignPermissionsToRole(Long roleId, List<Long> permissionIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("角色不存在"));

        Set<Permission> permissions = new HashSet<>();
        if (permissionIds != null) {
            for (Long permissionId : permissionIds) {
                permissionRepository.findById(permissionId).ifPresent(permissions::add);
            }
        }

        role.setPermissions(permissions);
        return roleRepository.save(role);
    }

    public List<Permission> getPermissionTree() {
        return permissionRepository.findAll().stream()
                .filter(permission -> permission.getParentCode() == null)
                .toList();
    }
}
