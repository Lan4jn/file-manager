package com.lan4jn.file.controller;

import com.lan4jn.file.common.GlobalExceptionHandler;
import com.lan4jn.file.service.ConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

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
        when(configService.getUploadTransportMode()).thenReturn("APPLICATION");
        when(configService.getDownloadTransportMode()).thenReturn("APPLICATION");
    }

    @Test
    void shouldExposeTransferSettingsToFrontendUiConfig() throws Exception {
        mockMvc.perform(get("/api/config/ui"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("limited"))
                .andExpect(jsonPath("$.data.port").value(9001))
                .andExpect(jsonPath("$.data.uploadTransportMode").value("APPLICATION"))
                .andExpect(jsonPath("$.data.downloadTransportMode").value("APPLICATION"));
    }
}
