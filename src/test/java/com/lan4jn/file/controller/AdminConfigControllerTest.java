package com.lan4jn.file.controller;

import com.lan4jn.file.common.GlobalExceptionHandler;
import com.lan4jn.file.service.ConfigService;
import com.lan4jn.file.service.PermissionAssertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminConfigControllerTest {

    private MockMvc mockMvc;
    private ConfigService configService;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        PermissionAssertService permissionAssertService = mock(PermissionAssertService.class);
        when(permissionAssertService.isSuperAdmin()).thenReturn(true);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminConfigController(configService, permissionAssertService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldExposeFrontendDownloadThresholdInFileConfig() throws Exception {
        when(configService.getAllowedExtensions()).thenReturn(Set.of("txt"));
        when(configService.getMaxFileSize()).thenReturn(104857600L);
        when(configService.getFrontendDownloadThresholdBytes()).thenReturn(104857600L);

        mockMvc.perform(get("/api/admin/file-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.frontendDownloadThresholdMB").value(100));
    }

    @Test
    void shouldSaveFrontendDownloadThreshold() throws Exception {
        mockMvc.perform(post("/api/admin/file-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"allowedExtensions\":[\"txt\"],\"maxFileSize\":104857600,\"frontendDownloadThresholdMB\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(configService).setFrontendDownloadThresholdBytes(104857600L);
    }
}
