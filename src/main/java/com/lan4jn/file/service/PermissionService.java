package com.lan4jn.file.service;

import com.lan4jn.file.entity.Permission;
import com.lan4jn.file.repository.PermissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PermissionService {

    @Autowired
    private PermissionRepository permissionRepository;

    public List<Permission> getAllPermissions() {
        return permissionRepository.findAll();
    }

    public Permission getPermissionById(Long id) {
        return permissionRepository.findById(id).orElse(null);
    }

    public Permission getPermissionByCode(String code) {
        return permissionRepository.findByCode(code).orElse(null);
    }

    @Transactional
    public Permission createPermission(Permission permission) {
        if (permissionRepository.existsByCode(permission.getCode())) {
            throw new RuntimeException("鏉冮檺缂栫爜宸插瓨鍦?");
        }
        return permissionRepository.save(permission);
    }

    @Transactional
    public Permission updatePermission(Long id, Permission permissionData) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("鏉冮檺涓嶅瓨鍦?"));

        if (permissionData.getName() != null) {
            permission.setName(permissionData.getName());
        }
        if (permissionData.getDescription() != null) {
            permission.setDescription(permissionData.getDescription());
        }
        if (permissionData.getType() != null) {
            permission.setType(permissionData.getType());
        }
        if (permissionData.getUrl() != null) {
            permission.setUrl(permissionData.getUrl());
        }
        if (permissionData.getParentCode() != null) {
            permission.setParentCode(permissionData.getParentCode());
        }

        return permissionRepository.save(permission);
    }

    @Transactional
    public void deletePermission(Long id) {
        permissionRepository.deleteById(id);
    }

    public List<Permission> getPermissionsTree() {
        List<Permission> all = permissionRepository.findAll();
        return buildTree(all, null);
    }

    private List<Permission> buildTree(List<Permission> all, String parentCode) {
        return all.stream()
                .filter(p -> (parentCode == null && p.getParentCode() == null) ||
                        (parentCode != null && parentCode.equals(p.getParentCode())))
                .collect(Collectors.toList());
    }
}

