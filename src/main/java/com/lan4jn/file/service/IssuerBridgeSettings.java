package com.lan4jn.file.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 上游证书中心桥接配置
 * 统一 5 个明确属性：
 * issuer-bridge.enabled
 * issuer-bridge.base-url
 * issuer-bridge.project-id
 * issuer-bridge.api-key
 * issuer-ca-file
 */
@Component
public class IssuerBridgeSettings {

    private final boolean enabled;
    private final String baseUrl;
    private final String projectId;
    private final String apiKey;
    private final String issuerCaFile;

    private List<PublicKey> issuerKeys = Collections.emptyList();

    @Autowired
    public IssuerBridgeSettings(
            @Value("${issuer-bridge.enabled:false}") boolean enabled,
            @Value("${issuer-bridge.base-url:}") String baseUrl,
            @Value("${issuer-bridge.project-id:}") String projectId,
            @Value("${issuer-bridge.api-key:}") String apiKey,
            @Value("${issuer-bridge.issuer-ca-file:}") String issuerCaFile) {
        this.enabled = enabled;
        this.baseUrl = baseUrl != null ? baseUrl.trim() : "";
        this.projectId = projectId != null ? projectId.trim() : "";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.issuerCaFile = issuerCaFile != null ? issuerCaFile.trim() : "";
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            this.issuerKeys = Collections.emptyList();
            return;
        }

        // 1. baseUrl 严格校验：必须 HTTPS，禁止 userInfo/query/fragment/非根路径
        if (baseUrl.isEmpty()) {
            throw new IllegalStateException("issuer-bridge.base-url is required when enabled=true");
        }
        try {
            URI uri = URI.create(baseUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalStateException("issuer-bridge.base-url must use HTTPS protocol");
            }
            if (uri.getHost() == null || uri.getHost().isEmpty()) {
                throw new IllegalStateException("issuer-bridge.base-url host cannot be empty");
            }
            if (uri.getUserInfo() != null) {
                throw new IllegalStateException("issuer-bridge.base-url cannot contain userInfo");
            }
            if (uri.getQuery() != null) {
                throw new IllegalStateException("issuer-bridge.base-url cannot contain query parameters");
            }
            if (uri.getFragment() != null) {
                throw new IllegalStateException("issuer-bridge.base-url cannot contain fragment");
            }
            String path = uri.getPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) {
                throw new IllegalStateException("issuer-bridge.base-url must not contain path segments");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("issuer-bridge.base-url is not a valid URI", e);
        }

        // 2. projectId 校验：非空，拒绝 . 与 .. 以及路径遍历字符
        if (projectId.isEmpty()) {
            throw new IllegalStateException("issuer-bridge.project-id is required when enabled=true");
        }
        if (".".equals(projectId) || "..".equals(projectId) || !projectId.matches("^[a-zA-Z0-9_.-]+$")
                || projectId.contains("/") || projectId.contains("\\")) {
            throw new IllegalStateException("issuer-bridge.project-id contains invalid characters");
        }

        // 3. apiKey 校验：非空，严禁包含空白与换行
        if (apiKey.isEmpty() || apiKey.contains(" ") || apiKey.contains("\t") || apiKey.contains("\r") || apiKey.contains("\n")) {
            throw new IllegalStateException("issuer-bridge.api-key is missing or contains invalid whitespace/newlines");
        }

        // 4. issuerCaFile 校验：必须存在且只能是包含合法直接签发CA（basicConstraints>=0 且带有 keyCertSign）的PEM包
        if (issuerCaFile.isEmpty()) {
            throw new IllegalStateException("issuer-bridge.issuer-ca-file is required when enabled=true");
        }
        Path caPath = Path.of(issuerCaFile);
        if (!Files.exists(caPath) || !Files.isRegularFile(caPath)) {
            throw new IllegalStateException("issuer-bridge.issuer-ca-file not found or not a regular file");
        }

        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            List<PublicKey> keys = new ArrayList<>();
            try (InputStream is = new BufferedInputStream(new FileInputStream(caPath.toFile()))) {
                Collection<? extends Certificate> certs = cf.generateCertificates(is);
                for (Certificate cert : certs) {
                    if (cert instanceof X509Certificate x509) {
                        // 校验 basicConstraints >= 0
                        if (x509.getBasicConstraints() < 0) {
                            throw new IllegalStateException("Certificate in issuer-ca-file is not a CA (basicConstraints < 0)");
                        }
                        // 校验 keyUsage 中的 keyCertSign (位 5)
                        boolean[] ku = x509.getKeyUsage();
                        if (ku != null && (ku.length <= 5 || !ku[5])) {
                            throw new IllegalStateException("Certificate in issuer-ca-file lacks keyCertSign usage");
                        }
                        keys.add(x509.getPublicKey());
                    }
                }
            }

            if (keys.isEmpty()) {
                throw new IllegalStateException("No valid CA certificates found in issuer-ca-file");
            }

            this.issuerKeys = Collections.unmodifiableList(keys);
        } catch (Exception e) {
            if (e instanceof IllegalStateException ise) {
                throw ise;
            }
            throw new IllegalStateException("Failed to parse issuer-ca-file", e);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getProjectId() {
        return projectId;
    }

    public List<PublicKey> getIssuerKeys() {
        return issuerKeys;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getIssuerCaFile() {
        return issuerCaFile;
    }
}
