package com.lan4jn.file.service;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.controller.CertificateAuthController;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.entity.UserCertificate;
import com.lan4jn.file.repository.UserCertificateRepository;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.utils.JwtTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class IssuerCertificateAuthServiceTest {

    // Fixture EC leaf used by IssuerCertificatePolicyTest
    private static final byte[] EC_LEAF_DER = Base64.getDecoder().decode(
            "MIIBOzCB4qADAgECAgkAx9C2YUtIMbIwCgYIKoZIzj0EAwIwEjEQMA4GA1UEAxMHRml4dHVyZTAeFw0yNjExMjAwNjM0NTNaFw0yNjEyMjAwNjM0NTNaMBIxEDAOBgNVBAMTB0ZpeHR1cmUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAStYWYjxkZcZ9Z1okB+6Kx9Nu3MB5qYzJ/icbAQdj6IITLVLdge+O1SLx9MTlW4AHP3tKlss00R7YBkusM46vU1oyEwHzAdBgNVHQ4EFgQU1AmsRjqkRw6BIWl+VdjmXdiheTAwCgYIKoZIzj0EAwIDSAAwRQIgSDWI3BEr/chSFpQ3Tcp24yEDM22TmarybnOtkCrriy0CIQDo6PCeYyH/I+OxjkB/wkrsgtcnkTjV7hZC6MpvHzp29A=="
    );

    // Fixture RSA leaf
    private static final String RSA_PEM =
            "-----BEGIN CERTIFICATE-----\n" +
            "MIICyzCCAbOgAwIBAgIIXlggkIufLfMwDQYJKoZIhvcNAQELBQAwFDESMBAGA1UE\n" +
            "AxMJVGVzdCBDZXJ0MB4XDTI2MTEyMDA2MzYwM1oXDTI3MTEyMDA2MzYwM1owFDES\n" +
            "MBAGA1UEAxMJVGVzdCBDZXJ0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKC\n" +
            "AQEA39gkekDMyiBUmCr3Dkr+FhpEOyVdtPQAOIzW/1b0eGFwKNL6s5IPsJW+GHg3\n" +
            "cjTxU5t71hAOBH7pNEfofb3OTTdGuSKdL9M6g7CYLECepkR5gR8tzH4SMTepBQrg\n" +
            "de+pWaed1IfLLFHvqxy+OVvgcc3Gs1YDeQv7sTwZTPwid1uUJZ9AKs7uuSDT5ToF\n" +
            "hib/Uz3wIrksONHFx/GJsNu3wyJI8mnUV/cdYjjlNpW0N1PBlspRd6D4u5FVxlAf\n" +
            "/CG/aJ/mMhrS/iockMEUHl9qeq/bvPXnLq1MnDqHWJeUp7027avJ9ytnH/D7Mwxc\n" +
            "lW7Nd9scSvZdyZhZtSCncUk3bwIDAQABoyEwHzAdBgNVHQ4EFgQUNT2iwpw5qzMI\n" +
            "ztBIRf7xeu5KbgowDQYJKoZIhvcNAQELBQADggEBALo6litXssFzCCwTt0QMceWk\n" +
            "o6S+yTWDL2tIM2Wnzcz+V4oe2XkCmZ3tg4KHkGEVbXwIzSHmhyelyXsI5kWt5Dj8\n" +
            "EkKcSBrMDb94jIJhFDrGBgaGvaFCoDGKqmYf/ESAt5apcQPvljFrUcbv4FA5sbXD\n" +
            "b9GU3enlH3XQSJasO40Fv+bsG0arHb6oSy2twWi7NUt/UuhjZ0B7FoXGS7BvkyIj\n" +
            "adcVnO6yuEilsfMg0X5YZkg/W9lXZ+DoLOU2dnWLQsBDoxWLfE2eTKP1u/YEjuPf\n" +
            "5iZuGOs62R3y75u5OshYU+ilpeh8594v9voYzUYfBjsmzHN9oTIMkKyRye9v+YQ=\n" +
            "-----END CERTIFICATE-----";

    private X509Certificate ecCert;
    private X509Certificate rsaCert;
    private String ecFp;
    private String rsaFp;

    private UserCertificateRepository certRepo;
    private UserRepository userRepo;
    private IssuerCertificatePolicy policy;
    private CertificateAuthService authService;
    private JwtTokenUtil jwtUtil;
    private LoginProtectionService protection;
    private UserDetailsService userDetailsService;
    private BearerIdentityService bearerService;
    private CertificateAuthController authController;

    private User sampleUser;

    @BeforeEach
    void setUp() throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        ecCert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(EC_LEAF_DER));
        rsaCert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(RSA_PEM.getBytes(StandardCharsets.UTF_8)));

        ecFp = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(ecCert.getEncoded()));
        rsaFp = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rsaCert.getEncoded()));

        certRepo = mock(UserCertificateRepository.class);
        userRepo = mock(UserRepository.class);
        policy = mock(IssuerCertificatePolicy.class);
        protection = new LoginProtectionService();
        userDetailsService = mock(UserDetailsService.class);

        jwtUtil = new JwtTokenUtil();
        org.springframework.test.util.ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-that-is-at-least-256-bits-long-for-hmac-sha-algorithm");
        org.springframework.test.util.ReflectionTestUtils.setField(jwtUtil, "expiration", 3600000L);

        sampleUser = new User();
        sampleUser.setId(100L);
        sampleUser.setUsername("testuser");
        sampleUser.setEnabled(true);
        Role role = new Role();
        role.setCode("ROLE_USER");
        sampleUser.setRoles(Collections.singleton(role));

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username("testuser")
                .password("password")
                .authorities("ROLE_USER")
                .build();
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(userDetails);
        when(userRepo.findById(100L)).thenReturn(Optional.of(sampleUser));
        when(userRepo.findByUsername("testuser")).thenReturn(Optional.of(sampleUser));

        authService = new CertificateAuthService(certRepo, policy);
        authController = new CertificateAuthController(authService, userRepo, jwtUtil, protection);
        bearerService = new BearerIdentityService(jwtUtil, userDetailsService, authService, userRepo, protection, true);
    }

    // 1. 禁用桥时新绑定必须保留 null，且存 leaf DER
    @Test
    void bindWhenBridgeDisabled_storesNullIssuerManagedAndPreservesDer() {
        when(policy.isEnabled()).thenReturn(false);
        when(certRepo.findByFingerprint(ecFp)).thenReturn(Optional.empty());
        when(certRepo.saveAndFlush(any(UserCertificate.class))).thenAnswer(i -> i.getArgument(0));

        PeerCertificate peer = new PeerCertificate(ecFp, ecCert.getNotAfter().getTime(), ecCert);
        UserCertificate cert = authService.bind(100L, peer);

        assertThat(cert.getIssuerManaged()).isNull();
        assertThat(cert.getCertificateDer()).isEqualTo(EC_LEAF_DER);
        verify(policy, never()).isManaged(any());
        verify(policy, never()).isValid(any());
    }

    // 2. 桥启用时新绑定：managed 证书存 true 且验有效性
    @Test
    void bindWhenBridgeEnabledAndManaged_storesTrueIssuerManagedAndValidatesFingerprint() {
        when(policy.isEnabled()).thenReturn(true);
        when(policy.isManaged(ecCert)).thenReturn(true);
        when(policy.isValid(ecFp)).thenReturn(true);
        when(certRepo.findByFingerprint(ecFp)).thenReturn(Optional.empty());
        when(certRepo.saveAndFlush(any(UserCertificate.class))).thenAnswer(i -> i.getArgument(0));

        PeerCertificate peer = new PeerCertificate(ecFp, ecCert.getNotAfter().getTime(), ecCert);
        UserCertificate cert = authService.bind(100L, peer);

        assertThat(cert.getIssuerManaged()).isTrue();
        assertThat(cert.getCertificateDer()).isEqualTo(EC_LEAF_DER);
        verify(policy).isValid(ecFp);
    }

    // 3. 桥启用时新绑定：managed 证书如果已被吊销或无效，拒绝绑定
    @Test
    void bindWhenBridgeEnabledAndRevoked_rejectsBinding() {
        when(policy.isEnabled()).thenReturn(true);
        when(policy.isManaged(ecCert)).thenReturn(true);
        when(policy.isValid(ecFp)).thenReturn(false);
        when(certRepo.findByFingerprint(ecFp)).thenReturn(Optional.empty());

        PeerCertificate peer = new PeerCertificate(ecFp, ecCert.getNotAfter().getTime(), ecCert);
        assertThatThrownBy(() -> authService.bind(100L, peer))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("证书无效或已吊销");
    }

    // 4. 桥启用时新绑定：非联动证书不持久化 false，存 leaf DER
    @Test
    void bindWhenBridgeEnabledAndUnmanaged_doesNotStoreFalseAndStoresDer() {
        when(policy.isEnabled()).thenReturn(true);
        when(policy.isManaged(rsaCert)).thenReturn(false);
        when(certRepo.findByFingerprint(rsaFp)).thenReturn(Optional.empty());
        when(certRepo.saveAndFlush(any(UserCertificate.class))).thenAnswer(i -> i.getArgument(0));

        PeerCertificate peer = new PeerCertificate(rsaFp, rsaCert.getNotAfter().getTime(), rsaCert);
        UserCertificate cert = authService.bind(100L, peer);

        assertThat(cert.getIssuerManaged()).isNull();
        assertThat(cert.getCertificateDer()).isNotNull();
        verify(policy, never()).isValid(any());
    }

    // 5. 桥启用时新绑定：policy.isManaged 异常拒绝绑定
    @Test
    void bindWhenBridgeEnabledAndPolicyThrows_rejectsBinding() {
        when(policy.isEnabled()).thenReturn(true);
        when(policy.isManaged(ecCert)).thenThrow(new IllegalStateException("无法判断证书签发方"));
        when(certRepo.findByFingerprint(ecFp)).thenReturn(Optional.empty());

        PeerCertificate peer = new PeerCertificate(ecFp, ecCert.getNotAfter().getTime(), ecCert);
        assertThatThrownBy(() -> authService.bind(100L, peer))
                .isInstanceOf(IllegalStateException.class);
    }

    // 6. 已标 true 即使关桥也拒绝登录
    @Test
    void loginWhenMarkedTrueAndBridgeDisabled_rejectsLogin() {
        when(policy.isEnabled()).thenReturn(false);

        UserCertificate cert = new UserCertificate(100L, ecFp, ecCert.getNotAfter().getTime());
        cert.setIssuerManaged(true);
        cert.setCertificateDer(EC_LEAF_DER);
        when(certRepo.findByFingerprint(ecFp)).thenReturn(Optional.of(cert));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        request.setAttribute("jakarta.servlet.request.X509Certificate", new X509Certificate[]{ecCert});

        Result<?> result = authController.certificateLogin(request);
        assertThat(result.getCode()).isEqualTo(401);
    }

    // 7. 已标 true 且开桥时，需 policy.isValid 为 true 才允许登录
    @Test
    void loginWhenMarkedTrueAndBridgeEnabled_validatesFingerprint() {
        when(policy.isEnabled()).thenReturn(true);
        when(policy.isValid(ecFp)).thenReturn(true);

        UserCertificate cert = new UserCertificate(100L, ecFp, ecCert.getNotAfter().getTime());
        cert.setIssuerManaged(true);
        cert.setCertificateDer(EC_LEAF_DER);
        when(certRepo.findByFingerprint(ecFp)).thenReturn(Optional.of(cert));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        request.setAttribute("jakarta.servlet.request.X509Certificate", new X509Certificate[]{ecCert});

        Result<?> okResult = authController.certificateLogin(request);
        assertThat(okResult.getCode()).isEqualTo(200);

        when(policy.isValid(ecFp)).thenReturn(false);
        Result<?> revokedResult = authController.certificateLogin(request);
        assertThat(revokedResult.getCode()).isEqualTo(401);
    }

    // 8. 无标历史证书在关桥时保持独立部署原样允许登录
    @Test
    void loginWhenHistoricalNullAndBridgeDisabled_allowsLogin() {
        when(policy.isEnabled()).thenReturn(false);

        UserCertificate cert = new UserCertificate(100L, ecFp, ecCert.getNotAfter().getTime());
        cert.setIssuerManaged(null);
        when(certRepo.findByFingerprint(ecFp)).thenReturn(Optional.of(cert));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        request.setAttribute("jakarta.servlet.request.X509Certificate", new X509Certificate[]{ecCert});

        Result<?> result = authController.certificateLogin(request);
        assertThat(result.getCode()).isEqualTo(200);
    }

    // 9. 无标历史证书在开桥时持证登录：managed 验签分类为 true 且检验 isValid
    @Test
    void loginWhenHistoricalNullAndBridgeEnabledAndManaged_classifiesAsTrueAndValidatesFingerprint() {
        when(policy.isEnabled()).thenReturn(true);
        when(policy.isManaged(ecCert)).thenReturn(true);
        when(policy.isValid(ecFp)).thenReturn(true);

        UserCertificate cert = new UserCertificate(100L, ecFp, ecCert.getNotAfter().getTime());
        cert.setIssuerManaged(null);
        cert.setCertificateDer(null);
        when(certRepo.findByFingerprint(ecFp)).thenReturn(Optional.of(cert));
        when(certRepo.saveAndFlush(any(UserCertificate.class))).thenAnswer(i -> i.getArgument(0));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        request.setAttribute("jakarta.servlet.request.X509Certificate", new X509Certificate[]{ecCert});

        Result<?> okResult = authController.certificateLogin(request);
        assertThat(okResult.getCode()).isEqualTo(200);
        assertThat(cert.getIssuerManaged()).isTrue();
        assertThat(cert.getCertificateDer()).isEqualTo(EC_LEAF_DER);

        // 若已被吊销
        when(policy.isValid(ecFp)).thenReturn(false);
        Result<?> failResult = authController.certificateLogin(request);
        assertThat(failResult.getCode()).isEqualTo(401);
    }

    // 10. 无标历史证书在开桥时持证登录：非联动验签分类为非联动恢复，存 leaf DER
    @Test
    void loginWhenHistoricalNullAndBridgeEnabledAndUnmanaged_recoversAsUnmanagedAndStoresDer() {
        when(policy.isEnabled()).thenReturn(true);
        when(policy.isManaged(rsaCert)).thenReturn(false);

        UserCertificate cert = new UserCertificate(100L, rsaFp, rsaCert.getNotAfter().getTime());
        cert.setIssuerManaged(null);
        cert.setCertificateDer(null);
        when(certRepo.findByFingerprint(rsaFp)).thenReturn(Optional.of(cert));
        when(certRepo.saveAndFlush(any(UserCertificate.class))).thenAnswer(i -> i.getArgument(0));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        request.setAttribute("jakarta.servlet.request.X509Certificate", new X509Certificate[]{rsaCert});

        Result<?> result = authController.certificateLogin(request);
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(cert.getIssuerManaged()).isNull();
        assertThat(cert.getCertificateDer()).isNotNull();
    }

    // 11. 已标 true 即使关桥也拒绝 JWT resolve
    @Test
    void resolveJwtWhenMarkedTrueAndBridgeDisabled_returnsNull() {
        when(policy.isEnabled()).thenReturn(false);

        UserCertificate cert = new UserCertificate(100L, ecFp, ecCert.getNotAfter().getTime());
        cert.setId(1L);
        cert.setIssuerManaged(true);
        when(certRepo.findById(1L)).thenReturn(Optional.of(cert));

        String token = jwtUtil.generateToken("testuser", 100L, "ROLE_USER", "1:" + cert.getGeneration());
        UserDetails resolved = bearerService.resolve(token);
        assertThat(resolved).isNull();
    }

    // 12. 已标 true 且开桥时，isValid 为 true 允许 JWT resolve
    @Test
    void resolveJwtWhenMarkedTrueAndBridgeEnabledAndValid_returnsUserDetails() {
        when(policy.isEnabled()).thenReturn(true);
        when(policy.isValid(ecFp)).thenReturn(true);

        UserCertificate cert = new UserCertificate(100L, ecFp, ecCert.getNotAfter().getTime());
        cert.setId(1L);
        cert.setIssuerManaged(true);
        when(certRepo.findById(1L)).thenReturn(Optional.of(cert));

        String token = jwtUtil.generateToken("testuser", 100L, "ROLE_USER", "1:" + cert.getGeneration());
        UserDetails resolved = bearerService.resolve(token);
        assertThat(resolved).isNotNull();
        assertThat(resolved.getUsername()).isEqualTo("testuser");
    }

    // 13. 已标 true 且开桥时，isValid 为 false 拒绝 JWT resolve
    @Test
    void resolveJwtWhenMarkedTrueAndBridgeEnabledAndRevoked_returnsNull() {
        when(policy.isEnabled()).thenReturn(true);
        when(policy.isValid(ecFp)).thenReturn(false);

        UserCertificate cert = new UserCertificate(100L, ecFp, ecCert.getNotAfter().getTime());
        cert.setId(1L);
        cert.setIssuerManaged(true);
        when(certRepo.findById(1L)).thenReturn(Optional.of(cert));

        String token = jwtUtil.generateToken("testuser", 100L, "ROLE_USER", "1:" + cert.getGeneration());
        UserDetails resolved = bearerService.resolve(token);
        assertThat(resolved).isNull();
    }

    // 14. 无标历史证书且无 DER：开桥时旧 JWT 拒绝直到持证登录
    @Test
    void resolveJwtWhenHistoricalNullWithoutDerAndBridgeEnabled_rejectsUntilLogin() {
        when(policy.isEnabled()).thenReturn(true);

        UserCertificate cert = new UserCertificate(100L, ecFp, ecCert.getNotAfter().getTime());
        cert.setId(1L);
        cert.setIssuerManaged(null);
        cert.setCertificateDer(null); // 历史无标老数据
        when(certRepo.findById(1L)).thenReturn(Optional.of(cert));

        String token = jwtUtil.generateToken("testuser", 100L, "ROLE_USER", "1:" + cert.getGeneration());
        UserDetails resolved = bearerService.resolve(token);
        assertThat(resolved).isNull(); // 必须拒绝
    }

    // 15. 无标证书存有 DER：开桥时旧 JWT 解析 leaf 重新分类为非联动则放行
    @Test
    void resolveJwtWhenHistoricalNullWithDerAndBridgeEnabledAndUnmanaged_allowsAccessWithoutBypassing() throws Exception {
        when(policy.isEnabled()).thenReturn(true);
        when(policy.isManaged(any(X509Certificate.class))).thenReturn(false);

        UserCertificate cert = new UserCertificate(100L, rsaFp, rsaCert.getNotAfter().getTime());
        cert.setId(1L);
        cert.setIssuerManaged(null);
        cert.setCertificateDer(rsaCert.getEncoded());
        when(certRepo.findById(1L)).thenReturn(Optional.of(cert));

        String token = jwtUtil.generateToken("testuser", 100L, "ROLE_USER", "1:" + cert.getGeneration());
        UserDetails resolved = bearerService.resolve(token);
        assertThat(resolved).isNotNull();
    }

    // 16. 关键防绕过场景：非联动未持久化 false，当未来换 CA 或扩容使得该证书被 managed 覆盖时，JWT resolve 实时验签并执行 isValid 校验
    @Test
    void resolveJwtWhenUnmanagedLaterBecomesManagedAfterCaChange_detectsManagedAndValidates() throws Exception {
        when(policy.isEnabled()).thenReturn(true);
        // 原本非联动，现在 CA 扩容覆盖了该证书
        when(policy.isManaged(any(X509Certificate.class))).thenReturn(true);
        when(policy.isValid(rsaFp)).thenReturn(false); // 吊销了

        UserCertificate cert = new UserCertificate(100L, rsaFp, rsaCert.getNotAfter().getTime());
        cert.setId(1L);
        cert.setIssuerManaged(null); // 未持久化 false
        cert.setCertificateDer(rsaCert.getEncoded());
        when(certRepo.findById(1L)).thenReturn(Optional.of(cert));

        String token = jwtUtil.generateToken("testuser", 100L, "ROLE_USER", "1:" + cert.getGeneration());
        UserDetails resolved = bearerService.resolve(token);
        assertThat(resolved).isNull(); // 成功识别出 managed 且因 isValid=false 拒绝，杜绝绕过！
    }

    // 17. 异常状态时拒绝且 JWT resolve 尤其不要 5xx
    @Test
    void resolveJwtWhenPolicyThrows_returnsNullWithout5xx() {
        when(policy.isEnabled()).thenReturn(true);
        when(policy.isManaged(any(X509Certificate.class))).thenThrow(new IllegalStateException("无法判断证书签发方"));

        UserCertificate cert = new UserCertificate(100L, ecFp, ecCert.getNotAfter().getTime());
        cert.setId(1L);
        cert.setIssuerManaged(null);
        cert.setCertificateDer(EC_LEAF_DER);
        when(certRepo.findById(1L)).thenReturn(Optional.of(cert));

        String token = jwtUtil.generateToken("testuser", 100L, "ROLE_USER", "1:" + cert.getGeneration());
        UserDetails resolved = bearerService.resolve(token);
        assertThat(resolved).isNull(); // 不抛出异常，优雅返回 null
    }

    // 18. 解绑注销使 JWT 立即失效逻辑保留
    @Test
    void unbindInvalidatesExistingJwtImmediately() {
        UserCertificate cert = new UserCertificate(100L, ecFp, ecCert.getNotAfter().getTime());
        cert.setId(1L);
        when(certRepo.findById(1L)).thenReturn(Optional.of(cert));

        String token = jwtUtil.generateToken("testuser", 100L, "ROLE_USER", "1:" + cert.getGeneration());

        // 解绑后数据库中不再存在
        when(certRepo.findById(1L)).thenReturn(Optional.empty());

        UserDetails resolved = bearerService.resolve(token);
        assertThat(resolved).isNull();
    }

    // 19. Boolean.FALSE 外部历史导入必须当未知，开桥时绝不能跳过验签
    @Test
    void resolveJwtWhenHistoricalFalse_treatedAsUnknownAndDoesNotSkipVerification() {
        when(policy.isEnabled()).thenReturn(true);

        UserCertificate cert = new UserCertificate(100L, ecFp, ecCert.getNotAfter().getTime());
        cert.setId(1L);
        cert.setIssuerManaged(Boolean.FALSE); // 外部历史导入的 FALSE 必须当未知！
        cert.setCertificateDer(null); // 无 DER
        when(certRepo.findById(1L)).thenReturn(Optional.of(cert));

        String token = jwtUtil.generateToken("testuser", 100L, "ROLE_USER", "1:" + cert.getGeneration());
        UserDetails resolved = bearerService.resolve(token);
        assertThat(resolved).isNull(); // 必须当未知拒绝，绝不能因为是 false 就跳过验签！
    }

    // 20. JWT 从 DER 重分类受管时保持 sticky true（即使后来关桥也拒绝）
    @Test
    void resolveJwtWhenReclassifiedManaged_becomesStickyTrueEvenAfterBridgeDisabledLater() throws Exception {
        when(policy.isEnabled()).thenReturn(true);
        when(policy.isManaged(any(X509Certificate.class))).thenReturn(true);
        when(policy.isValid(ecFp)).thenReturn(true);

        UserCertificate cert = new UserCertificate(100L, ecFp, ecCert.getNotAfter().getTime());
        cert.setId(1L);
        cert.setIssuerManaged(null);
        cert.setCertificateDer(EC_LEAF_DER);
        when(certRepo.findById(1L)).thenReturn(Optional.of(cert));

        String token = jwtUtil.generateToken("testuser", 100L, "ROLE_USER", "1:" + cert.getGeneration());
        UserDetails resolved = bearerService.resolve(token);
        assertThat(resolved).isNotNull();
        // 验证被写回为 sticky true
        verify(certRepo, atLeastOnce()).saveAndFlush(cert);
        assertThat(cert.getIssuerManaged()).isTrue();

        // 模拟后续关桥
        when(policy.isEnabled()).thenReturn(false);
        UserDetails resolvedAfterBridgeClosed = bearerService.resolve(token);
        // 曾受管的 sticky true 证书在关桥后必须被拒绝！
        assertThat(resolvedAfterBridgeClosed).isNull();
    }

    // 21. 确认 DER 的 SHA-256 == 绑定指纹；缺损或被篡改即拒绝！
    @Test
    void resolveJwtWithCorruptedDer_rejectsDueToFingerprintMismatch() throws Exception {
        when(policy.isEnabled()).thenReturn(true);

        UserCertificate cert = new UserCertificate(100L, ecFp, ecCert.getNotAfter().getTime());
        cert.setId(1L);
        cert.setIssuerManaged(null);
        byte[] corruptedDer = EC_LEAF_DER.clone();
        corruptedDer[corruptedDer.length - 1] ^= 0xFF; // 篡改最后一个字节
        cert.setCertificateDer(corruptedDer);
        when(certRepo.findById(1L)).thenReturn(Optional.of(cert));

        String token = jwtUtil.generateToken("testuser", 100L, "ROLE_USER", "1:" + cert.getGeneration());
        UserDetails resolved = bearerService.resolve(token);
        assertThat(resolved).isNull(); // SHA-256 不匹配，立即拒绝
        verify(policy, never()).isManaged(any());
    }

    // 22. WebSocket 出站保护：仅对受管证书会话出站复核，吊销或缺 token 绝不发送
    @Test
    void websocketSendToUserRejectsAndClosesWhenCertRevoked() {
        when(policy.isEnabled()).thenReturn(true);
        when(policy.isValid(ecFp)).thenReturn(false); // 证书已被吊销

        UserCertificate cert = new UserCertificate(100L, ecFp, ecCert.getNotAfter().getTime());
        cert.setId(1L);
        cert.setIssuerManaged(true);
        when(certRepo.findById(1L)).thenReturn(Optional.of(cert));

        String token = jwtUtil.generateToken("testuser", 100L, "ROLE_USER", "1:" + cert.getGeneration());

        com.lan4jn.file.config.MessageWebSocketAuthService wsAuth = new com.lan4jn.file.config.MessageWebSocketAuthService(bearerService);
        MessageSocketSessionService sessionService = new MessageSocketSessionService(wsAuth);

        org.springframework.web.socket.WebSocketSession session = mock(org.springframework.web.socket.WebSocketSession.class);
        when(session.getId()).thenReturn("sess-1");
        when(session.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("username", "testuser");
        attributes.put("CERT_MANAGED_TOKEN", token); // 受管证书会话标识
        when(session.getAttributes()).thenReturn(attributes);

        sessionService.register("testuser", session);
        sessionService.sendToUser("testuser", "hello");

        // 验证载荷未发送且会话以 POLICY_VIOLATION 被关闭
        try {
            verify(session, never()).sendMessage(any());
            verify(session).close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION);
        } catch (Exception e) {
            org.junit.jupiter.api.Assertions.fail(e);
        }
    }

    // 23. WebSocket 独立部署/密码会话不带 CERT_MANAGED_TOKEN，保持原样推送不受影响
    @Test
    void websocketSendToUserPreservesRegularPasswordSessions() throws Exception {
        MessageSocketSessionService sessionService = new MessageSocketSessionService(mock(com.lan4jn.file.config.MessageWebSocketAuthService.class));

        org.springframework.web.socket.WebSocketSession session = mock(org.springframework.web.socket.WebSocketSession.class);
        when(session.getId()).thenReturn("sess-2");
        when(session.isOpen()).thenReturn(true);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("username", "testuser"); // 普通密码会话无 CERT_MANAGED_TOKEN
        when(session.getAttributes()).thenReturn(attributes);

        sessionService.register("testuser", session);
        sessionService.sendToUser("testuser", "hello");

        verify(session).sendMessage(any(org.springframework.web.socket.TextMessage.class));
    }

    // 24. isManagedCertificateSession 针对普通密码 token 准确返回 false
    @Test
    void isManagedCertificateSession_whenRegularPasswordToken_returnsFalse() {
        String token = jwtUtil.generateToken("testuser", 100L, "ROLE_USER", null); // 无 bindingId
        boolean managed = bearerService.isManagedCertificateSession(token);
        assertThat(managed).isFalse();
        verify(certRepo, never()).findById(any()); // 绝不查库
    }

    // 25. isManagedCertificateSession 针对证书会话只看 claims 中 bindingId，无需查库直接返回 true
    @Test
    void isManagedCertificateSession_whenCertificateBindingToken_returnsTrueWithoutQueryingDb() {
        String token = jwtUtil.generateToken("testuser", 100L, "ROLE_USER", "1:gen-uuid");
        boolean managed = bearerService.isManagedCertificateSession(token);
        assertThat(managed).isTrue();
        verify(certRepo, never()).findById(any()); // 绝不查库
    }

    // 26. fail-safe 防护：isManagedCertificateSession 遇到解析异常强制返回 true，绝不 fail-open
    @Test
    void isManagedCertificateSession_whenParsingThrowsException_returnsTrueFailSafe() {
        JwtTokenUtil faultyJwt = mock(JwtTokenUtil.class);
        when(faultyJwt.getClaimsFromToken(anyString())).thenThrow(new io.jsonwebtoken.JwtException("Malformed or DB down"));
        BearerIdentityService serviceWithFaultyJwt = new BearerIdentityService(
                faultyJwt, userDetailsService, authService, userRepo, protection, policy, true);

        boolean managed = serviceWithFaultyJwt.isManagedCertificateSession("faulty-token");
        // 异常必须返回 true 强制后续复核（fail-safe），绝不能返回 false 造成 fail-open！
        assertThat(managed).isTrue();
    }
}
