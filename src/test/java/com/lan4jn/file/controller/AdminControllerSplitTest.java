package com.lan4jn.file.controller;

import com.lan4jn.file.common.GlobalExceptionHandler;
import com.lan4jn.file.dto.UserDTO;
import com.lan4jn.file.entity.Permission;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.service.AuthorizationService;
import com.lan4jn.file.service.ConfigService;
import com.lan4jn.file.service.PermissionService;
import com.lan4jn.file.service.RoleService;
import com.lan4jn.file.service.UserService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminControllerSplitTest {

    private MockMvc mockMvc;
    private UserService userService;
    private RoleService roleService;
    private PermissionService permissionService;
    private ConfigService configService;
    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        roleService = mock(RoleService.class);
        permissionService = mock(PermissionService.class);
        configService = mock(ConfigService.class);
        authorizationService = mock(AuthorizationService.class);

        mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminUserController(userService),
                new AdminRoleController(roleService, authorizationService),
                new AdminPermissionController(permissionService, authorizationService),
                new AdminConfigController(configService),
                new AdminInitController(roleService, permissionService)
        ).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void shouldExposeRoutesFromSplitAdminControllers() throws Exception {
        UserDTO user = new UserDTO();
        user.setId(1L);
        user.setUsername("admin");

        Permission permission = new Permission();
        permission.setCode("user:manage");

        when(userService.getAllUsers()).thenReturn(List.of(user));
        when(authorizationService.getPermissionTree()).thenReturn(List.of(permission));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].username").value("admin"));

        mockMvc.perform(get("/api/admin/permissions/tree"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("user:manage"));
    }

    @Test
    void shouldExposeInitAndConfigRoutes() throws Exception {
        when(configService.getAllowedExtensions()).thenReturn(Set.of("txt"));
        when(configService.getMaxFileSize()).thenReturn(1024L);

        Role adminRole = new Role();
        adminRole.setCode("ADMIN");
        when(roleService.getRoleByCode("ADMIN")).thenReturn(adminRole);
        when(roleService.getRoleByCode("USER")).thenReturn(adminRole);

        mockMvc.perform(get("/api/admin/file-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxFileSize").value(1024));

        mockMvc.perform(post("/api/admin/init"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void shouldMarkSplitAdminControllersWithPreAuthorize() {
        Assertions.assertTrue(AdminUserController.class.isAnnotationPresent(PreAuthorize.class));
        Assertions.assertTrue(AdminRoleController.class.isAnnotationPresent(PreAuthorize.class));
        Assertions.assertTrue(AdminPermissionController.class.isAnnotationPresent(PreAuthorize.class));
        Assertions.assertTrue(AdminConfigController.class.isAnnotationPresent(PreAuthorize.class));
        Assertions.assertTrue(AdminInitController.class.isAnnotationPresent(PreAuthorize.class));
    }

    @Test
    void shouldReturnBadRequestForBlankRoleCode() throws Exception {
        mockMvc.perform(post("/api/admin/roles")
                        .contentType("application/json")
                        .content("{\"code\":\" \",\"name\":\"管理员\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("code不能为空"));
    }
}
