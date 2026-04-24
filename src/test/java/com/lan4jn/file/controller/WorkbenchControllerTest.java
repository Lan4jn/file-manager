package com.lan4jn.file.controller;

import com.lan4jn.file.common.GlobalExceptionHandler;
import com.lan4jn.file.dto.WorkbenchOverviewResponse;
import com.lan4jn.file.service.WorkbenchService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkbenchControllerTest {

    @Test
    void shouldReturnOverviewForCurrentUser() throws Exception {
        WorkbenchService workbenchService = mock(WorkbenchService.class);
        WorkbenchController controller = new WorkbenchController(workbenchService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        WorkbenchOverviewResponse overview = new WorkbenchOverviewResponse();
        overview.setDefaultWorkbench(true);
        WorkbenchOverviewResponse.DirectoryItem directoryItem = new WorkbenchOverviewResponse.DirectoryItem();
        directoryItem.setBucketName("docs");
        directoryItem.setDirectoryPath("reports/");
        overview.setRecentDirectories(java.util.List.of(directoryItem));
        when(workbenchService.getOverview("alice")).thenReturn(overview);

        mockMvc.perform(get("/api/workbench/overview")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.defaultWorkbench").value(true))
                .andExpect(jsonPath("$.data.recentDirectories[0].bucketName").value("docs"));

        verify(workbenchService).getOverview("alice");
    }

    @Test
    void shouldUpdateWorkbenchPreference() throws Exception {
        WorkbenchService workbenchService = mock(WorkbenchService.class);
        WorkbenchController controller = new WorkbenchController(workbenchService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(put("/api/workbench/preference")
                        .contentType(APPLICATION_JSON)
                        .content("{\"defaultWorkbench\":true}")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(workbenchService).updatePreference("alice", true);
    }

    @Test
    void shouldRejectMissingPreferenceBody() throws Exception {
        WorkbenchService workbenchService = mock(WorkbenchService.class);
        WorkbenchController controller = new WorkbenchController(workbenchService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(put("/api/workbench/preference")
                        .contentType(APPLICATION_JSON)
                        .content("{}")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("defaultWorkbench不能为空"));
    }

    @Test
    void shouldAddFavoriteDirectory() throws Exception {
        WorkbenchService workbenchService = mock(WorkbenchService.class);
        WorkbenchController controller = new WorkbenchController(workbenchService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/workbench/favorites")
                        .contentType(APPLICATION_JSON)
                        .content("{\"bucketName\":\"docs\",\"directoryPath\":\"reports/\",\"directoryName\":\"reports\"}")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(workbenchService).addFavorite(eq("alice"), eq("docs"), eq("reports/"), eq("reports"));
    }

    @Test
    void shouldDeleteFavoriteDirectory() throws Exception {
        WorkbenchService workbenchService = mock(WorkbenchService.class);
        WorkbenchController controller = new WorkbenchController(workbenchService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(delete("/api/workbench/favorites")
                        .param("bucketName", "docs")
                        .param("directoryPath", "reports/")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(workbenchService).deleteFavorite("alice", "docs", "reports/");
    }

    @Test
    void shouldRejectInvalidFavoritePayload() throws Exception {
        WorkbenchService workbenchService = mock(WorkbenchService.class);
        WorkbenchController controller = new WorkbenchController(workbenchService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/api/workbench/favorites")
                        .contentType(APPLICATION_JSON)
                        .content("{\"bucketName\":\"\",\"directoryPath\":\"   \"}")
                        .principal(new UsernamePasswordAuthenticationToken("alice", "N/A")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }
}
