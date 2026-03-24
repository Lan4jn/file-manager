package com.lan4jn.file.service;

import com.lan4jn.file.dto.BucketPermissionDTO;
import com.lan4jn.file.dto.CurrentPermissionInfoDTO;
import com.lan4jn.file.entity.Permission;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.entity.RoleBucketPermission;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.repository.RoleBucketPermissionRepository;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.rbac.PermissionCodes;
import com.lan4jn.file.rbac.PermissionType;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PermissionResolverService {

    private final UserRepository userRepository;
    private final RoleBucketPermissionRepository roleBucketPermissionRepository;

    public PermissionResolverService(UserRepository userRepository,
                                     RoleBucketPermissionRepository roleBucketPermissionRepository) {
        this.userRepository = userRepository;
        this.roleBucketPermissionRepository = roleBucketPermissionRepository;
    }

    public PermissionSnapshot resolveByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        Set<String> roleCodes = user.getRoles() == null
                ? new LinkedHashSet<>()
                : user.getRoles().stream().map(Role::getCode).collect(Collectors.toCollection(LinkedHashSet::new));
        boolean superAdmin = roleCodes.contains("ADMIN") && "admin".equalsIgnoreCase(user.getUsername());

        Set<String> menuPermissions = new LinkedHashSet<>();
        Set<String> apiPermissions = new LinkedHashSet<>();

        if (user.getRoles() != null) {
            for (Role role : user.getRoles()) {
                if (role.getPermissions() == null) {
                    continue;
                }
                for (Permission permission : role.getPermissions()) {
                    if (permission == null || permission.getCode() == null) {
                        continue;
                    }
                    if (PermissionType.MENU.equalsIgnoreCase(permission.getType())) {
                        menuPermissions.add(permission.getCode());
                    } else {
                        apiPermissions.add(permission.getCode());
                    }
                }
            }
        }

        if (superAdmin) {
            menuPermissions.addAll(PermissionCodes.ALL_MENU_CODES);
            apiPermissions.addAll(PermissionCodes.ALL_API_CODES);
            menuPermissions.add(PermissionCodes.MENU_CONFIG);
            apiPermissions.add(PermissionCodes.SYSTEM_CONFIG_ACCESS);
        }

        Map<String, Set<String>> bucketPermissions = new LinkedHashMap<>();
        if (!superAdmin && user.getRoles() != null && !user.getRoles().isEmpty()) {
            List<Long> roleIds = user.getRoles().stream().map(Role::getId).toList();
            for (RoleBucketPermission relation : roleBucketPermissionRepository.findByRoleIdIn(roleIds)) {
                bucketPermissions
                        .computeIfAbsent(relation.getBucketName(), ignored -> new LinkedHashSet<>())
                        .add(relation.getAction());
            }
        }

        return new PermissionSnapshot(
                user.getId(),
                user.getUsername(),
                superAdmin,
                roleCodes,
                menuPermissions,
                apiPermissions,
                bucketPermissions
        );
    }

    public CurrentPermissionInfoDTO toDto(PermissionSnapshot snapshot) {
        CurrentPermissionInfoDTO dto = new CurrentPermissionInfoDTO();
        dto.setUserId(snapshot.userId());
        dto.setUsername(snapshot.username());
        dto.setSuperAdmin(snapshot.superAdmin());
        dto.setRoles(snapshot.roles().toArray(String[]::new));
        dto.setMenuPermissions(snapshot.menuPermissions().toArray(String[]::new));
        dto.setApiPermissions(snapshot.apiPermissions().toArray(String[]::new));

        List<BucketPermissionDTO> bucketPermissionList = new ArrayList<>();
        snapshot.bucketPermissions().forEach((bucketName, actions) -> {
            BucketPermissionDTO item = new BucketPermissionDTO();
            item.setBucketName(bucketName);
            item.setActions(new ArrayList<>(actions));
            bucketPermissionList.add(item);
        });
        bucketPermissionList.sort(Comparator.comparing(BucketPermissionDTO::getBucketName));
        dto.setBucketPermissions(bucketPermissionList);
        return dto;
    }
}
