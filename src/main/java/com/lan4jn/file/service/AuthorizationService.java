package com.lan4jn.file.service;

import com.lan4jn.file.dto.BucketPermissionDTO;
import com.lan4jn.file.dto.RolePermissionDetailDTO;
import com.lan4jn.file.dto.RolePermissionSaveRequest;
import com.lan4jn.file.entity.Permission;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.entity.RoleBucketPermission;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.repository.PermissionRepository;
import com.lan4jn.file.repository.RoleBucketPermissionRepository;
import com.lan4jn.file.repository.RoleRepository;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.rbac.PermissionType;
import com.lan4jn.file.utils.RequestValidationUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class AuthorizationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleBucketPermissionRepository roleBucketPermissionRepository;

    public AuthorizationService(UserRepository userRepository,
                                RoleRepository roleRepository,
                                PermissionRepository permissionRepository,
                                RoleBucketPermissionRepository roleBucketPermissionRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.roleBucketPermissionRepository = roleBucketPermissionRepository;
    }

    public Set<Role> resolveRoles(String[] roleCodes) {
        Set<Role> roles = new LinkedHashSet<>();
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
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setRoles(resolveRoles(roleCodes));
        return userRepository.save(user);
    }

    @Transactional
    public Role assignPermissionsToRole(Long roleId, List<Long> permissionIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));

        Set<Permission> permissions = new LinkedHashSet<>();
        if (permissionIds != null) {
            for (Long permissionId : permissionIds) {
                permissionRepository.findById(permissionId).ifPresent(permissions::add);
            }
        }

        role.setPermissions(permissions);
        return roleRepository.save(role);
    }

    @Transactional
    public RolePermissionDetailDTO saveRolePermissions(Long roleId, RolePermissionSaveRequest request) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));

        Set<Permission> merged = new LinkedHashSet<>();
        addPermissionsByCode(merged, request.getMenuPermissions(), PermissionType.MENU);
        addPermissionsByCode(merged, request.getApiPermissions(), null);
        role.setPermissions(merged);
        roleRepository.save(role);

        roleBucketPermissionRepository.deleteByRoleId(roleId);
        if (request.getBucketPermissions() != null) {
            Map<String, Set<String>> mergedBucketPermissions = new LinkedHashMap<>();
            for (BucketPermissionDTO bucketPermission : request.getBucketPermissions()) {
                String bucketName = RequestValidationUtils.requireText(bucketPermission.getBucketName(), "bucketName");
                List<String> actions = RequestValidationUtils.requireTextList(bucketPermission.getActions(), "actions");
                mergedBucketPermissions
                        .computeIfAbsent(bucketName, ignored -> new LinkedHashSet<>())
                        .addAll(new LinkedHashSet<>(actions));
            }

            for (Map.Entry<String, Set<String>> entry : mergedBucketPermissions.entrySet()) {
                for (String action : entry.getValue()) {
                    RoleBucketPermission relation = new RoleBucketPermission();
                    relation.setRole(role);
                    relation.setBucketName(entry.getKey());
                    relation.setAction(action);
                    roleBucketPermissionRepository.save(relation);
                }
            }
        }

        return getRolePermissionDetail(roleId);
    }

    public RolePermissionDetailDTO getRolePermissionDetail(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在"));

        RolePermissionDetailDTO dto = new RolePermissionDetailDTO();
        dto.setRoleId(role.getId());
        dto.setRoleCode(role.getCode());

        if (role.getPermissions() != null) {
            dto.setMenuPermissions(role.getPermissions().stream()
                    .filter(permission -> PermissionType.MENU.equalsIgnoreCase(permission.getType()))
                    .map(Permission::getCode)
                    .sorted()
                    .toList());
            dto.setApiPermissions(role.getPermissions().stream()
                    .filter(permission -> !PermissionType.MENU.equalsIgnoreCase(permission.getType()))
                    .map(Permission::getCode)
                    .sorted()
                    .toList());
        }

        Map<String, BucketPermissionDTO> bucketMap = new LinkedHashMap<>();
        for (RoleBucketPermission relation : roleBucketPermissionRepository.findByRoleId(roleId)) {
            BucketPermissionDTO item = bucketMap.computeIfAbsent(relation.getBucketName(), ignored -> {
                BucketPermissionDTO dtoItem = new BucketPermissionDTO();
                dtoItem.setBucketName(relation.getBucketName());
                dtoItem.setActions(new ArrayList<>());
                return dtoItem;
            });
            item.getActions().add(relation.getAction());
        }
        dto.setBucketPermissions(new ArrayList<>(bucketMap.values()));
        return dto;
    }

    public List<Permission> getPermissionTree() {
        return permissionRepository.findAll().stream()
                .filter(permission -> permission.getParentCode() == null)
                .toList();
    }

    private void addPermissionsByCode(Set<Permission> target, List<String> codes, String expectedType) {
        if (codes == null) {
            return;
        }
        for (String code : new LinkedHashSet<>(codes)) {
            permissionRepository.findByCode(code).ifPresent(permission -> {
                if (expectedType == null || expectedType.equalsIgnoreCase(permission.getType())) {
                    target.add(permission);
                }
            });
        }
    }
}
