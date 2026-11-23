package com.lan4jn.file.controller;

import com.lan4jn.file.dto.CurrentPermissionInfoDTO;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.service.AppInfoService;
import com.lan4jn.file.service.ConfigService;
import com.lan4jn.file.service.LoginChallengeService;
import com.lan4jn.file.service.LoginProtectionService;
import com.lan4jn.file.service.PermissionResolverService;
import com.lan4jn.file.service.PermissionSnapshot;
import com.lan4jn.file.service.UserService;
import com.lan4jn.file.utils.JwtTokenUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private MockMvc mockMvc;
    private PermissionResolverService permissionResolverService;
    private UserRepository userRepository;
    private LoginProtectionService loginProtectionService;
    private JwtTokenUtil jwtTokenUtil;

    @BeforeEach
    void setUp() {
        permissionResolverService = mock(PermissionResolverService.class);
        userRepository = mock(UserRepository.class);
        loginProtectionService = mock(LoginProtectionService.class);
        jwtTokenUtil = mock(JwtTokenUtil.class);
        doNothing().when(loginProtectionService).assertAllowed(any(), any());
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(
                jwtTokenUtil,
                userRepository,
                mock(PasswordEncoder.class),
                mock(AppInfoService.class),
                permissionResolverService,
                mock(UserService.class),
                loginProtectionService,
                new LoginChallengeService(),
                mock(ConfigService.class)
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

    @Test
    void shouldLoginWithChallengeForBcryptUser() throws Exception {
        User user = new User();
        user.setId(2L);
        user.setUsername("bcrypt-user");
        user.setNickname("Bcrypt User");
        user.setEnabled(true);
        user.setPassword(BCrypt.hashpw("secret123", BCrypt.gensalt()));
        user.setRoles(new LinkedHashSet<>());

        when(userRepository.findByUsername("bcrypt-user")).thenReturn(Optional.of(user));
        when(jwtTokenUtil.generateToken(eq("bcrypt-user"), eq(2L), eq(""))).thenReturn("token");

        MvcResult challengeResult = mockMvc.perform(get("/api/auth/login-challenge")
                        .param("username", "bcrypt-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordScheme").value("BCRYPT"))
                .andExpect(jsonPath("$.data.passwordVerifier").value(user.getPassword()))
                .andReturn();

        com.fasterxml.jackson.databind.JsonNode challengeJson = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(challengeResult.getResponse().getContentAsString());
        String challengeId = challengeJson.path("data").path("challengeId").asText();
        String nonce = challengeJson.path("data").path("nonce").asText();
        String derivedSecret = BCrypt.hashpw("secret123", user.getPassword());
        String proof = sha512(derivedSecret + ":" + nonce);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(("{\"username\":\"bcrypt-user\",\"challengeId\":\"" + challengeId + "\",\"passwordProof\":\"" + proof + "\"}")
                                .getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("token"));

        verify(loginProtectionService).reset(eq("bcrypt-user"), any());
    }

    @Test
    void shouldReturnRemainingAttemptsWhenUserDoesNotExist() throws Exception {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(loginProtectionService.recordFailure(eq("ghost"), any()))
                .thenReturn("用户名不存在或密码错误，剩余 4 次重试机会");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ghost\",\"password\":\"bad\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("用户名不存在或密码错误，剩余 4 次重试机会"));

        verify(loginProtectionService).recordFailure(eq("ghost"), any());
    }

    @Test
    void shouldReturnLockMessageFromProtectionService() throws Exception {
        doThrow(new IllegalStateException("登录失败次数过多，请在 120 秒后再试"))
                .when(loginProtectionService).assertAllowed(eq("locked-user"), any());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"locked-user\",\"password\":\"bad\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("登录失败次数过多，请在 120 秒后再试"));
    }

    @Test
    void shouldReturnChallengeExpiredMessageWithoutMaskingIt() throws Exception {
        LoginProtectionService realLoginProtectionService = new LoginProtectionService();
        ReflectionTestUtils.setField(realLoginProtectionService, "maxFailures", 5);
        ReflectionTestUtils.setField(realLoginProtectionService, "lockMinutes", 15L);
        LoginChallengeService realLoginChallengeService = new LoginChallengeService();
        User user = new User();
        user.setId(3L);
        user.setUsername("challenge-user");
        user.setNickname("Challenge User");
        user.setEnabled(true);
        user.setPassword(BCrypt.hashpw("secret123", BCrypt.gensalt()));
        user.setRoles(new LinkedHashSet<>());

        UserRepository realRepository = mock(UserRepository.class);
        when(realRepository.findByUsername("challenge-user")).thenReturn(Optional.of(user));

        MockMvc localMockMvc = MockMvcBuilders.standaloneSetup(new AuthController(
                jwtTokenUtil,
                realRepository,
                mock(PasswordEncoder.class),
                mock(AppInfoService.class),
                permissionResolverService,
                mock(UserService.class),
                realLoginProtectionService,
                realLoginChallengeService,
                mock(ConfigService.class)
        )).build();

        localMockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"challenge-user\",\"challengeId\":\"expired-id\",\"passwordProof\":\"proof\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("登录挑战已失效，请刷新后重试"));
    }

    @Test
    void shouldExposeCertificateIssuanceEnabledInRuntimeConfig() throws Exception {
        mockMvc.perform(get("/api/auth/runtime-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certificateIssuanceEnabled").value(false));
    }

    @Test
    void shouldExposeCertificateIssuanceEnabledTrueWhenSettingsEnabled() throws Exception {
        com.lan4jn.file.service.IssuerBridgeSettings settings = mock(com.lan4jn.file.service.IssuerBridgeSettings.class);
        when(settings.isEnabled()).thenReturn(true);
        AuthController controller = new AuthController(
                jwtTokenUtil,
                userRepository,
                mock(org.springframework.security.crypto.password.PasswordEncoder.class),
                mock(AppInfoService.class),
                permissionResolverService,
                mock(UserService.class),
                loginProtectionService,
                new LoginChallengeService(),
                mock(ConfigService.class)
        );
        controller.setIssuerBridgeSettings(settings);
        MockMvc localMvc = MockMvcBuilders.standaloneSetup(controller).build();

        localMvc.perform(get("/api/auth/runtime-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certificateIssuanceEnabled").value(true));
    }

    private String sha512(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-512");
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder builder = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            String hex = Integer.toHexString(b & 0xff);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }
}
