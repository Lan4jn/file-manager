package com.lan4jn.file.service;

import com.lan4jn.file.entity.Permission;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.repository.PermissionRepository;
import com.lan4jn.file.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoleService {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

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
            throw new RuntimeException("瑙掕壊缂栫爜宸插瓨鍦?");
        }

        role.setCreateTime(System.currentTimeMillis());
        return roleRepository.save(role);
    }

    @Transactional
    public Role updateRole(Long id, Role roleData) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("瑙掕壊涓嶅瓨鍦?"));

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
        roleRepository.deleteById(id);
    }

    @Transactional
    public Role assignPermissions(Long roleId, List<Long> permissionIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("瑙掕壊涓嶅瓨鍦?"));

        Set<Permission> permissions = new HashSet<>();
        for (Long permissionId : permissionIds) {
            permissionRepository.findById(permissionId).ifPresent(permissions::add);
        }

        role.setPermissions(permissions);
        return roleRepository.save(role);
    }
}

