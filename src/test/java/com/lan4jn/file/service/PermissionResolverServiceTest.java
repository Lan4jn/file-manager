package com.lan4jn.file.service;

import com.lan4jn.file.entity.Permission;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.entity.RoleBucketPermission;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.repository.RoleBucketPermissionRepository;
import com.lan4jn.file.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionResolverServiceTest {

    @Test
    void shouldGrantAllMenuAndApiPermissionsToSuperAdmin() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleBucketPermissionRepository bucketPermissionRepository = mock(RoleBucketPermissionRepository.class);
        PermissionResolverService service = new PermissionResolverService(userRepository, bucketPermissionRepository);

        Role adminRole = new Role();
        adminRole.setId(1L);
        adminRole.setCode("ADMIN");
        adminRole.setPermissions(new LinkedHashSet<>());

        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setRoles(new LinkedHashSet<>(List.of(adminRole)));

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));

        PermissionSnapshot snapshot = service.resolveByUsername("admin");

        assertTrue(snapshot.superAdmin());
        assertTrue(snapshot.menuPermissions().contains("MENU_CONFIG"));
        assertTrue(snapshot.apiPermissions().contains("API_ROLE_MANAGE"));
        assertTrue(snapshot.apiPermissions().contains("SYSTEM_CONFIG_ACCESS"));
    }

    @Test
    void shouldAggregateRoleAndBucketPermissionsForNormalUser() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleBucketPermissionRepository bucketPermissionRepository = mock(RoleBucketPermissionRepository.class);
        PermissionResolverService service = new PermissionResolverService(userRepository, bucketPermissionRepository);

        Permission menuPermission = new Permission();
        menuPermission.setCode("MENU_FILES");
        menuPermission.setType("MENU");

        Permission apiPermission = new Permission();
        apiPermission.setCode("API_FILE_OPERATE");
        apiPermission.setType("API");

        Role userRole = new Role();
        userRole.setId(2L);
        userRole.setCode("USER");
        userRole.setPermissions(new LinkedHashSet<>(List.of(menuPermission, apiPermission)));

        User user = new User();
        user.setId(2L);
        user.setUsername("alice");
        user.setRoles(new LinkedHashSet<>(List.of(userRole)));

        RoleBucketPermission upload = new RoleBucketPermission();
        upload.setBucketName("bucket-a");
        upload.setAction("UPLOAD");
        RoleBucketPermission read = new RoleBucketPermission();
        read.setBucketName("bucket-a");
        read.setAction("READ");

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(bucketPermissionRepository.findByRoleIdIn(List.of(2L))).thenReturn(List.of(upload, read));

        PermissionSnapshot snapshot = service.resolveByUsername("alice");

        assertEquals("alice", snapshot.username());
        assertTrue(snapshot.menuPermissions().contains("MENU_FILES"));
        assertTrue(snapshot.apiPermissions().contains("API_FILE_OPERATE"));
        assertEquals(2, snapshot.bucketPermissions().get("bucket-a").size());
    }
}
