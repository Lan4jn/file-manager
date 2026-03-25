package com.lan4jn.file.controller;

import com.lan4jn.file.dto.CurrentPermissionInfoDTO;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.service.AppInfoService;
import com.lan4jn.file.service.PermissionResolverService;
import com.lan4jn.file.service.PermissionSnapshot;
import com.lan4jn.file.service.UserService;
import com.lan4jn.file.utils.JwtTokenUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;
    private PermissionResolverService permissionResolverService;

    @BeforeEach
    void setUp() {
        permissionResolverService = mock(PermissionResolverService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(
                mock(JwtTokenUtil.class),
                mock(UserRepository.class),
                mock(PasswordEncoder.class),
                mock(AppInfoService.class),
                permissionResolverService,
                mock(UserService.class)
        )).build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnCurrentPermissionSnapshot() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", null));

        PermissionSnapshot snapshot = new PermissionSnapshot(
                2L,
                "alice",
                false,
                new LinkedHashSet<>(java.util.List.of("USER")),
                new LinkedHashSet<>(java.util.List.of("MENU_FILES")),
                new LinkedHashSet<>(java.util.List.of("API_FILE_BROWSE")),
                new LinkedHashMap<>()
        );
        CurrentPermissionInfoDTO dto = new CurrentPermissionInfoDTO();
        dto.setUserId(2L);
        dto.setUsername("alice");
        dto.setRoles(new String[]{"USER"});
        dto.setMenuPermissions(new String[]{"MENU_FILES"});
        dto.setApiPermissions(new String[]{"API_FILE_BROWSE"});

        when(permissionResolverService.resolveByUsername("alice")).thenReturn(snapshot);
        when(permissionResolverService.toDto(snapshot)).thenReturn(dto);

        mockMvc.perform(get("/api/auth/current-permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.menuPermissions[0]").value("MENU_FILES"))
                .andExpect(jsonPath("$.data.apiPermissions[0]").value("API_FILE_BROWSE"));
    }
}
