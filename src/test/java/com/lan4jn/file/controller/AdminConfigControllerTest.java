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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

    @Test
    void shouldSaveTransferSettingsInAdminConfig() throws Exception {
        String payload = "{\"uploadTransportMode\":\"DIRECT\",\"downloadTransportMode\":\"DIRECT\","
                + "\"uploadProxyThresholdMB\":256,\"downloadProxyThresholdMB\":128,"
                + "\"archiveTaskRetentionMinutes\":120,\"archiveTaskMaxConcurrent\":2}";

        when(configService.isAllowedTransportMode("DIRECT")).thenReturn(true);

        mockMvc.perform(post("/api/admin/file-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(configService).setUploadTransportMode("DIRECT");
        verify(configService).setDownloadTransportMode("DIRECT");
        verify(configService).setUploadProxyThresholdBytes(256L * 1024L * 1024L);
        verify(configService).setDownloadProxyThresholdBytes(128L * 1024L * 1024L);
        verify(configService).setArchiveTaskRetentionMinutes(120);
        verify(configService).setArchiveTaskMaxConcurrent(2);
    }

    @Test
    void shouldIgnoreInvalidTransferSettings() throws Exception {
        String payload = "{\"uploadTransportMode\":\"INVALID\",\"downloadTransportMode\":\"\","
                + "\"uploadProxyThresholdMB\":0,\"downloadProxyThresholdMB\":-1,"
                + "\"archiveTaskRetentionMinutes\":0,\"archiveTaskMaxConcurrent\":0}";

        mockMvc.perform(post("/api/admin/file-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(configService, never()).setUploadTransportMode(any());
        verify(configService, never()).setDownloadTransportMode(any());
        verify(configService, never()).setUploadProxyThresholdBytes(any());
        verify(configService, never()).setDownloadProxyThresholdBytes(any());
        verify(configService, never()).setArchiveTaskRetentionMinutes(any());
        verify(configService, never()).setArchiveTaskMaxConcurrent(any());
    }

    @Test
    void shouldIgnoreNullTransferSettings() throws Exception {
        String payload = "{\"uploadTransportMode\":null,\"downloadTransportMode\":null,"
                + "\"uploadProxyThresholdMB\":null,\"downloadProxyThresholdMB\":null,"
                + "\"archiveTaskRetentionMinutes\":null,\"archiveTaskMaxConcurrent\":null}";

        mockMvc.perform(post("/api/admin/file-config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(configService, never()).setUploadTransportMode(any());
        verify(configService, never()).setDownloadTransportMode(any());
        verify(configService, never()).setUploadProxyThresholdBytes(any());
        verify(configService, never()).setDownloadProxyThresholdBytes(any());
        verify(configService, never()).setArchiveTaskRetentionMinutes(any());
        verify(configService, never()).setArchiveTaskMaxConcurrent(any());
    }

    @Test
    void shouldExposeTransferSettingsInAdminConfig() throws Exception {
        when(configService.getAllowedExtensions()).thenReturn(Set.of("txt"));
        when(configService.getMaxFileSize()).thenReturn(1024L);
        when(configService.getFrontendDownloadThresholdBytes()).thenReturn(104857600L);
        when(configService.getUploadTransportMode()).thenReturn("DIRECT");
        when(configService.getDownloadTransportMode()).thenReturn("DIRECT");
        when(configService.getUploadProxyThresholdBytes()).thenReturn(268435456L);
        when(configService.getDownloadProxyThresholdBytes()).thenReturn(134217728L);
        when(configService.getArchiveTaskRetentionMinutes()).thenReturn(120);
        when(configService.getArchiveTaskMaxConcurrent()).thenReturn(2);

        mockMvc.perform(get("/api/admin/file-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadTransportMode").value("DIRECT"))
                .andExpect(jsonPath("$.data.downloadTransportMode").value("DIRECT"))
                .andExpect(jsonPath("$.data.uploadProxyThresholdMB").value(256))
                .andExpect(jsonPath("$.data.downloadProxyThresholdMB").value(128))
                .andExpect(jsonPath("$.data.archiveTaskRetentionMinutes").value(120))
                .andExpect(jsonPath("$.data.archiveTaskMaxConcurrent").value(2));
    }
}
