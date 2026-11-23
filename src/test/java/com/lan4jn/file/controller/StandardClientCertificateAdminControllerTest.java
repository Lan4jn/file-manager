package com.lan4jn.file.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lan4jn.file.common.GlobalExceptionHandler;
import com.lan4jn.file.entity.UserCertificate;
import com.lan4jn.file.repository.UserCertificateRepository;
import com.lan4jn.file.service.IssuerBridgeClient;
import com.lan4jn.file.service.IssuerBridgeSettings;
import com.lan4jn.file.service.IssuerCertificatePolicy;
import com.lan4jn.file.service.PermissionAssertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StandardClientCertificateAdminControllerTest {

    private MockMvc mockMvc;
    private IssuerBridgeClient bridgeClient;
    private IssuerBridgeSettings bridgeSettings;
    private IssuerCertificatePolicy certificatePolicy;
    private PermissionAssertService permissionAssertService;
    private UserCertificateRepository userCertificateRepository;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        bridgeClient = mock(IssuerBridgeClient.class);
        bridgeSettings = mock(IssuerBridgeSettings.class);
        certificatePolicy = mock(IssuerCertificatePolicy.class);
        permissionAssertService = mock(PermissionAssertService.class);
        userCertificateRepository = mock(UserCertificateRepository.class);
        objectMapper = new ObjectMapper();

        // 默认超管且已启用
        when(permissionAssertService.isSuperAdmin()).thenReturn(true);
        when(bridgeSettings.isEnabled()).thenReturn(true);

        StandardClientCertificateAdminController controller = new StandardClientCertificateAdminController(
                bridgeClient,
                bridgeSettings,
                certificatePolicy,
                permissionAssertService,
                userCertificateRepository
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("未启用时访问列表接口应返回400错误且不调用上游Client")
    void shouldRejectWhenBridgeDisabled() throws Exception {
        when(bridgeSettings.isEnabled()).thenReturn(false);

        mockMvc.perform(get("/api/admin/standard-client-certificates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("标准证书中心未启用"));

        verify(bridgeClient, never()).list(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("非超级管理员访问应抛出权限异常并返回403")
    void shouldRejectNonSuperAdmin() throws Exception {
        when(permissionAssertService.isSuperAdmin()).thenReturn(false);

        mockMvc.perform(get("/api/admin/standard-client-certificates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("仅超级管理员可访问"));

        verify(bridgeClient, never()).list(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("列表查询成功并依据本地UserCertificateRepository附加bound状态")
    void shouldListCertificatesAndEnrichBoundStatus() throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("total", 2);
        ArrayNode items = root.putArray("items");

        ObjectNode item1 = items.addObject();
        item1.put("id", "cert-1");
        item1.put("fingerprint", "fp-bound-1");
        item1.put("status", "VALID");

        ObjectNode item2 = items.addObject();
        item2.put("id", "cert-2");
        item2.put("fingerprint", "fp-unbound-2");
        item2.put("status", "VALID");

        when(bridgeClient.list("fp", 0, 50)).thenReturn(root);
        when(userCertificateRepository.findByFingerprint("fp-bound-1"))
                .thenReturn(Optional.of(new UserCertificate(100L, "fp-bound-1")));
        when(userCertificateRepository.findByFingerprint("fp-unbound-2"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/admin/standard-client-certificates")
                        .param("fingerprint", "fp")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].id").value("cert-1"))
                .andExpect(jsonPath("$.data.items[0].bound").value(true))
                .andExpect(jsonPath("$.data.items[1].id").value("cert-2"))
                .andExpect(jsonPath("$.data.items[1].bound").value(false));

        verify(bridgeClient).list("fp", 0, 50);
    }

    @Test
    @DisplayName("签发证书请求成功应调用Client.issue并返回单条记录")
    void shouldIssueCertificateSuccessfully() throws Exception {
        ObjectNode cert = objectMapper.createObjectNode();
        cert.put("id", "cert-new");
        cert.put("requestId", "req-123");
        cert.put("deviceLabel", "device-test");
        cert.put("fingerprint", "fp-new");
        cert.put("status", "VALID");

        when(bridgeClient.issue(eq("req-123"), eq("CSR_CONTENT"), eq("device-test"))).thenReturn(cert);
        when(userCertificateRepository.findByFingerprint("fp-new")).thenReturn(Optional.empty());

        String requestBody = "{\"requestId\":\"req-123\",\"csrPem\":\"CSR_CONTENT\",\"deviceLabel\":\"device-test\"}";

        mockMvc.perform(post("/api/admin/standard-client-certificates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("cert-new"))
                .andExpect(jsonPath("$.data.fingerprint").value("fp-new"))
                .andExpect(jsonPath("$.data.bound").value(false));

        verify(bridgeClient).issue("req-123", "CSR_CONTENT", "device-test");
    }

    @Test
    @DisplayName("签发证书缺失csrPem时应返回400错误")
    void shouldValidateIssueRequest() throws Exception {
        String invalidBody = "{\"requestId\":\"req-123\",\"csrPem\":\"\",\"deviceLabel\":\"device-test\"}";

        mockMvc.perform(post("/api/admin/standard-client-certificates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("CSR内容不能为空"));

        verify(bridgeClient, never()).issue(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("获取证书详情成功并附加bound状态")
    void shouldGetCertificateByIdAndEnrichBound() throws Exception {
        ObjectNode cert = objectMapper.createObjectNode();
        cert.put("id", "cert-detail");
        cert.put("fingerprint", "fp-bound-detail");
        cert.put("status", "VALID");

        when(bridgeClient.get("cert-detail")).thenReturn(cert);
        when(userCertificateRepository.findByFingerprint("fp-bound-detail"))
                .thenReturn(Optional.of(new UserCertificate(100L, "fp-bound-detail")));

        mockMvc.perform(get("/api/admin/standard-client-certificates/cert-detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("cert-detail"))
                .andExpect(jsonPath("$.data.bound").value(true));

        verify(bridgeClient).get("cert-detail");
    }

    @Test
    @DisplayName("吊销证书必须通过IssuerCertificatePolicy.revoke执行且不调用Client.revoke")
    void shouldRevokeCertificateThroughPolicy() throws Exception {
        ObjectNode cert = objectMapper.createObjectNode();
        cert.put("id", "cert-revoke");
        cert.put("fingerprint", "fp-revoke");
        cert.put("status", "REVOKED");

        when(certificatePolicy.revoke("cert-revoke")).thenReturn(cert);
        when(userCertificateRepository.findByFingerprint("fp-revoke")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/admin/standard-client-certificates/cert-revoke/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("cert-revoke"))
                .andExpect(jsonPath("$.data.status").value("REVOKED"))
                .andExpect(jsonPath("$.data.bound").value(false));

        // 必须调用 policy.revoke
        verify(certificatePolicy).revoke("cert-revoke");
        // 绝不直接调用 client.revoke
        verify(bridgeClient, never()).revoke(anyString());
    }
}
