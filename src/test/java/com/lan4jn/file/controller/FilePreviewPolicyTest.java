package com.lan4jn.file.controller;

import com.lan4jn.file.common.GlobalExceptionHandler;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.service.ConfigService;
import com.lan4jn.file.service.FileService;
import com.lan4jn.file.service.OperationLogService;
import com.lan4jn.file.service.PermissionAssertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FilePreviewPolicyTest {

    private MockMvc mockMvc;
    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = mock(FileService.class);
        ConfigService configService = mock(ConfigService.class);
        PermissionAssertService permissionAssertService = mock(PermissionAssertService.class);
        OperationLogService operationLogService = mock(OperationLogService.class);

        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileName("tool.exe");
        fileInfo.setContentType("application/octet-stream");
        fileInfo.setSize(1L);
        when(fileService.getFileInfo("docs/tool.exe", "bucket-a")).thenReturn(fileInfo);

        FileInfo yamlInfo = new FileInfo();
        yamlInfo.setFileName("app.yml");
        yamlInfo.setContentType("text/plain");
        yamlInfo.setSize(16L);
        when(fileService.getFileInfo("docs/app.yml", "bucket-a")).thenReturn(yamlInfo);
        when(fileService.getFileStream("docs/app.yml", "bucket-a"))
                .thenReturn(new ByteArrayInputStream("server:\n  port: 8080".getBytes()));

        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileService, configService, permissionAssertService, operationLogService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldRejectPreviewWhenExtensionIsOutsideWhitelist() throws Exception {
        mockMvc.perform(get("/api/file/preview")
                        .param("objectName", "docs/tool.exe")
                        .param("bucket", "bucket-a"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    String body = result.getResponse().getContentAsString();
                    boolean rejected = status == 400 || status == 403
                            || body.matches("(?s).*\\\"code\\\"\\s*:\\s*(400|403)(\\D.*|$)")
                            || body.contains("不支持在线预览");
                    org.junit.jupiter.api.Assertions.assertTrue(rejected, "预览接口未拒绝不在白名单内的扩展名");
                });

        verify(fileService).getFileInfo("docs/tool.exe", "bucket-a");
    }

    @Test
    void shouldAllowPreviewWhenExtensionIsWhitelisted() throws Exception {
        mockMvc.perform(get("/api/file/preview")
                        .param("objectName", "docs/app.yml")
                        .param("bucket", "bucket-a"))
                .andExpect(status().isOk());

        verify(fileService).getFileStream("docs/app.yml", "bucket-a");
    }
}
