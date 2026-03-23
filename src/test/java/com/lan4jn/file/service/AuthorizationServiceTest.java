package com.lan4jn.file.service;

import com.lan4jn.file.entity.Permission;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.repository.PermissionRepository;
import com.lan4jn.file.repository.RoleRepository;
import com.lan4jn.file.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorizationServiceTest {

    @Test
    void shouldResolveRolesByRoleCode() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        PermissionRepository permissionRepository = mock(PermissionRepository.class);

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        Role userRole = new Role();
        userRole.setCode("USER");

        when(roleRepository.findByCode("ADMIN")).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByCode("USER")).thenReturn(Optional.of(userRole));

        AuthorizationService authorizationService = new AuthorizationService(
                userRepository, roleRepository, permissionRepository);

        Set<Role> roles = authorizationService.resolveRoles(new String[]{"ADMIN", "USER"});

        assertEquals(Set.of(adminRole, userRole), roles);
    }

    @Test
    void shouldAssignPermissionsToRole() {
        UserRepository userRepository = mock(UserRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        PermissionRepository permissionRepository = mock(PermissionRepository.class);

        Role role = new Role();
        role.setId(1L);
        Permission permission = new Permission();
        permission.setId(10L);
        permission.setCode("file:download");

        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));
        when(permissionRepository.findById(10L)).thenReturn(Optional.of(permission));
        when(roleRepository.save(any(Role.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthorizationService authorizationService = new AuthorizationService(
                userRepository, roleRepository, permissionRepository);

        Role saved = authorizationService.assignPermissionsToRole(1L, List.of(10L));

        assertEquals(Set.of(permission), saved.getPermissions());
    }
}
