package com.lan4jn.file.service;

import com.lan4jn.file.dto.UserDTO;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SuperAdminGuardServiceTest {

    private final SuperAdminGuardService service = new SuperAdminGuardService(mock(UserRepository.class));

    @Test
    void shouldRejectDeletingSuperAdmin() {
        assertThrows(IllegalStateException.class, () -> service.assertCanDelete(createSuperAdmin()));
    }

    @Test
    void shouldRejectDowngradingSuperAdmin() {
        UserDTO dto = new UserDTO();
        dto.setRoles(new String[]{"USER"});
        assertThrows(IllegalStateException.class, () -> service.assertCanUpdate(createSuperAdmin(), dto));
    }

    @Test
    void shouldAllowUpdatingNormalUser() {
        UserDTO dto = new UserDTO();
        dto.setEnabled(false);
        assertDoesNotThrow(() -> service.assertCanUpdate(createNormalUser(), dto));
    }

    private User createSuperAdmin() {
        Role adminRole = new Role();
        adminRole.setCode("ADMIN");

        User user = new User();
        user.setUsername("admin");
        user.setRoles(new LinkedHashSet<>(List.of(adminRole)));
        return user;
    }

    private User createNormalUser() {
        Role userRole = new Role();
        userRole.setCode("USER");

        User user = new User();
        user.setUsername("alice");
        user.setRoles(new LinkedHashSet<>(List.of(userRole)));
        return user;
    }
}
