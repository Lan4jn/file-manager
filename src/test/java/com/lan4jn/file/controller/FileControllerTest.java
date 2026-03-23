package com.lan4jn.file.controller;

import com.lan4jn.file.common.GlobalExceptionHandler;
import com.lan4jn.file.dto.UploadResponse;
import com.lan4jn.file.model.BucketInfo;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.service.ConfigService;
import com.lan4jn.file.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileControllerTest {

    private MockMvc mockMvc;
    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = mock(FileService.class);
        ConfigService configService = mock(ConfigService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileService, configService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnBucketList() throws Exception {
        BucketInfo bucketInfo = new BucketInfo();
        bucketInfo.setName("bucket-a");
        bucketInfo.setCreationDate(LocalDateTime.now());

        when(fileService.listBuckets("name", "asc")).thenReturn(List.of(bucketInfo));

        mockMvc.perform(get("/api/file/buckets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("bucket-a"));
    }

    @Test
    void shouldReturnFileListForNonPagedRequest() throws Exception {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setObjectName("docs/readme.txt");
        fileInfo.setFileName("readme.txt");
        fileInfo.setIsFolder(false);

        when(fileService.listFiles("", "", "name", "asc")).thenReturn(List.of(fileInfo));

        mockMvc.perform(get("/api/file/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fileName").value("readme.txt"));
    }

    @Test
    void shouldReturnBadRequestWhenObjectNameIsBlank() throws Exception {
        mockMvc.perform(get("/api/file/info").param("objectName", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("objectName不能为空"));
    }
}
