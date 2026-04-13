package com.lan4jn.file.controller;

import com.lan4jn.file.common.GlobalExceptionHandler;
import com.lan4jn.file.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConfigControllerTest {

    private MockMvc mockMvc;
    private ConfigService configService;

    @BeforeEach
    void setUp() {
        configService = mock(ConfigService.class);
        ConfigController controller = new ConfigController(configService);
        ReflectionTestUtils.setField(controller, "uiMode", "limited");
        ReflectionTestUtils.setField(controller, "serverPort", 9001);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(configService.getUploadTransportMode()).thenReturn("DIRECT");
        when(configService.getDownloadTransportMode()).thenReturn("DIRECT");
        when(configService.getFileRowHiddenActions()).thenReturn(List.of("DELETE", "MOVE", "COPY"));
    }

    @Test
    void shouldExposeTransferSettingsToFrontendUiConfig() throws Exception {
        mockMvc.perform(get("/api/config/ui"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("limited"))
                .andExpect(jsonPath("$.data.port").value(9001))
                .andExpect(jsonPath("$.data.uploadTransportMode").value("DIRECT"))
                .andExpect(jsonPath("$.data.downloadTransportMode").value("DIRECT"))
                .andExpect(jsonPath("$.data.fileRowHiddenActions[0]").value("DELETE"))
                .andExpect(jsonPath("$.data.fileRowHiddenActions[1]").value("MOVE"))
                .andExpect(jsonPath("$.data.fileRowHiddenActions[2]").value("COPY"));
    }
}
