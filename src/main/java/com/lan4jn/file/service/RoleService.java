package com.lan4jn.file.service;

import com.lan4jn.file.entity.Permission;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.repository.PermissionRepository;
import com.lan4jn.file.repository.RoleBucketPermissionRepository;
import com.lan4jn.file.repository.RoleRepository;
import com.lan4jn.file.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final RoleBucketPermissionRepository roleBucketPermissionRepository;

    public RoleService(RoleRepository roleRepository,
                       PermissionRepository permissionRepository,
                       UserRepository userRepository,
                       RoleBucketPermissionRepository roleBucketPermissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.roleBucketPermissionRepository = roleBucketPermissionRepository;
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    public Role getRoleById(Long id) {
        return roleRepository.findById(id).orElse(null);
    }

    public Role getRoleByCode(String code) {
        return roleRepository.findByCode(code).orElse(null);
    }

    @Transactional
    public Role createRole(Role role) {
        if (roleRepository.existsByCode(role.getCode())) {
            throw new IllegalStateException("角色编码已存在");
        }
        role.setCreateTime(System.currentTimeMillis());
        return roleRepository.save(role);
    }

    @Transactional
    public Role updateRole(Long id, Role roleData) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));

        if (roleData.getCode() != null && !roleData.getCode().equals(role.getCode())) {
            if (roleRepository.existsByCode(roleData.getCode())) {
                throw new IllegalStateException("角色编码已存在");
            }
            role.setCode(roleData.getCode());
        }
        if (roleData.getName() != null) {
            role.setName(roleData.getName());
        }
        if (roleData.getDescription() != null) {
            role.setDescription(roleData.getDescription());
        }
        if (roleData.getPermissions() != null) {
            role.setPermissions(roleData.getPermissions());
        }
        return roleRepository.save(role);
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));
        if ("ADMIN".equals(role.getCode())) {
            throw new IllegalStateException("管理员角色不可删除");
        }
        if (userRepository.existsByRoles_Id(id)) {
            throw new IllegalStateException("角色已被用户使用，无法删除");
        }
        roleBucketPermissionRepository.deleteByRoleId(id);
        roleRepository.deleteById(id);
    }

    @Transactional
    public Role assignPermissions(Long roleId, List<Long> permissionIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));

        Set<Permission> permissions = new HashSet<>();
        for (Long permissionId : permissionIds) {
            permissionRepository.findById(permissionId).ifPresent(permissions::add);
        }

        role.setPermissions(permissions);
        return roleRepository.save(role);
    }
}
