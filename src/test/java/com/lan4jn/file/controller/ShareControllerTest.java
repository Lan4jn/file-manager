package com.lan4jn.file.controller;

import com.lan4jn.file.common.GlobalExceptionHandler;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.model.ShareAccessInfo;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.service.FileService;
import com.lan4jn.file.service.PermissionAssertService;
import com.lan4jn.file.service.ShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShareControllerTest {

    private MockMvc mockMvc;
    private ShareService shareService;

    @BeforeEach
    void setUp() {
        shareService = mock(ShareService.class);
        FileService fileService = mock(FileService.class);
        PermissionAssertService permissionAssertService = mock(PermissionAssertService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ShareController(shareService, fileService, permissionAssertService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnShareAccessInfo() throws Exception {
        ShareAccessInfo info = new ShareAccessInfo();
        info.setShareCode("SHARE001");
        info.setFileName("docs");
        info.setIsFolder(true);
        info.setExpireTime(LocalDateTime.now().plusHours(1));

        when(shareService.getShareAccessInfo("SHARE001")).thenReturn(info);

        mockMvc.perform(get("/api/share/access-info").param("shareCode", "SHARE001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shareCode").value("SHARE001"))
                .andExpect(jsonPath("$.data.fileName").value("docs"));
    }

    @Test
    void shouldReturnSharedItems() throws Exception {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectName("docs/readme.txt");
        fileInfo.setFileName("readme.txt");
        fileInfo.setIsFolder(false);

        when(shareService.listSharedItems("SHARE001", null, null)).thenReturn(List.of(fileInfo));

        mockMvc.perform(get("/api/share/items").param("shareCode", "SHARE001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fileName").value("readme.txt"));
    }

    @Test
    void shouldReturnBadRequestWhenShareCodeIsBlank() throws Exception {
        mockMvc.perform(get("/api/share/access-info").param("shareCode", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("shareCode不能为空"));
    }

    @Test
    void shouldNotIncrementViewCountWhenPrepareShareArchiveFails() throws Exception {
        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode("SHARE001");
        shareLink.setIsFolder(true);

        when(shareService.peekShareInfo("SHARE001", null)).thenReturn(shareLink);
        when(shareService.prepareShareArchive("SHARE001", null)).thenThrow(new RuntimeException("分享下载失败"));

        mockMvc.perform(get("/api/share/download-all").param("shareCode", "SHARE001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("分享下载失败"));

        verify(shareService, never()).incrementViewCount("SHARE001");
    }
}
