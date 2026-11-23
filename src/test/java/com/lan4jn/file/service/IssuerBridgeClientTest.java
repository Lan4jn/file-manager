package com.lan4jn.file.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssuerBridgeClientTest {

    @TempDir
    static Path tempDir;

    private static HttpsServer httpsServer;
    private static int serverPort;
    private static String caPemPath;
    private static SSLContext testClientSslContext;
    private static final String PROJECT_ID = "proj-mock-77";
    private static final String API_KEY = "test-issuer-token-secret-xyz";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final AtomicReference<HttpHandler> dynamicHandler = new AtomicReference<>();

    @BeforeAll
    static void startHttpsServer() throws Exception {
        Path p12 = tempDir.resolve("server-and-ca.p12");
        Path pem = tempDir.resolve("ca.pem");

        String exe = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows") ? "keytool.exe" : "keytool";
        String keytoolBin = Path.of(System.getProperty("java.home"), "bin", exe).toString();

        // 生成测试 CA
        runCommand(tempDir, keytoolBin, "-genkeypair", "-alias", "testca", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=Test CA,O=Lan4jn,C=CN", "-validity", "1", "-keystore", p12.toString(),
                "-storetype", "PKCS12", "-storepass", "changeit", "-ext", "BC:critical=ca:true", "-ext", "KU:critical=keyCertSign");

        // 导出 CA PEM
        runCommand(tempDir, keytoolBin, "-exportcert", "-rfc", "-alias", "testca", "-file", pem.toString(),
                "-keystore", p12.toString(), "-storetype", "PKCS12", "-storepass", "changeit");
        caPemPath = pem.toAbsolutePath().toString();

        // 生成服务器证书（带 SAN: 127.0.0.1）
        runCommand(tempDir, keytoolBin, "-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=127.0.0.1,O=Lan4jn,C=CN", "-validity", "1", "-keystore", p12.toString(),
                "-storetype", "PKCS12", "-storepass", "changeit");

        Path serverCsr = tempDir.resolve("server.csr");
        runCommand(tempDir, keytoolBin, "-certreq", "-alias", "server", "-file", serverCsr.toString(),
                "-keystore", p12.toString(), "-storetype", "PKCS12", "-storepass", "changeit");

        Path serverCer = tempDir.resolve("server.cer");
        runCommand(tempDir, keytoolBin, "-gencert", "-alias", "testca", "-infile", serverCsr.toString(),
                "-outfile", serverCer.toString(), "-validity", "1", "-keystore", p12.toString(),
                "-storetype", "PKCS12", "-storepass", "changeit", "-ext", "SAN=ip:127.0.0.1,dns:localhost");

        runCommand(tempDir, keytoolBin, "-importcert", "-alias", "server", "-file", serverCer.toString(),
                "-keystore", p12.toString(), "-storetype", "PKCS12", "-storepass", "changeit", "-noprompt");

        // 配置 HttpsServer
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream is = java.nio.file.Files.newInputStream(p12)) {
            ks.load(is, "changeit".toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "changeit".toCharArray());

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        SSLContext serverSslContext = SSLContext.getInstance("TLS");
        serverSslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        // 测试客户端信任临时自签名服务
        testClientSslContext = SSLContext.getInstance("TLS");
        testClientSslContext.init(null, tmf.getTrustManagers(), null);

        httpsServer = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(serverSslContext) {
            @Override
            public void configure(HttpsParameters params) {
                params.setSSLParameters(serverSslContext.getDefaultSSLParameters());
            }
        });

        httpsServer.createContext("/", exchange -> {
            HttpHandler handler = dynamicHandler.get();
            if (handler != null) {
                handler.handle(exchange);
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        });

        httpsServer.start();
        serverPort = httpsServer.getAddress().getPort();
    }

    @AfterAll
    static void stopHttpsServer() {
        if (httpsServer != null) {
            httpsServer.stop(0);
        }
    }

    private static void runCommand(Path workDir, String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).directory(workDir.toFile()).redirectErrorStream(true).start();
        byte[] out = p.getInputStream().readAllBytes();
        int exit = p.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("Command failed [" + exit + "]: " + new String(out, StandardCharsets.UTF_8));
        }
    }

    private IssuerBridgeSettings createValidSettings() {
        IssuerBridgeSettings settings = new IssuerBridgeSettings(
                true,
                "https://127.0.0.1:" + serverPort,
                PROJECT_ID,
                API_KEY,
                caPemPath
        );
        settings.init();
        return settings;
    }

    private IssuerBridgeClient createTestClient(IssuerBridgeSettings settings) {
        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(testClientSslContext)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        return new IssuerBridgeClient(settings, MAPPER, httpClient);
    }

    @Test
    void fractionalTotalCannotBecomeASingleExactMatch() {
        IssuerBridgeClient client = createTestClient(createValidSettings());
        dynamicHandler.set(exchange -> {
            byte[] data = "{\"total\":1.5,\"items\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream out = exchange.getResponseBody()) { out.write(data); }
        });
        assertThatThrownBy(() -> client.list("", 0, 50)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testDisabledThrowsIllegalStateException() {
        IssuerBridgeSettings settings = new IssuerBridgeSettings(false, "", "", "", "");
        IssuerBridgeClient client = new IssuerBridgeClient(settings, MAPPER);

        assertThatThrownBy(() -> client.list("fp", 1, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");

        assertThatThrownBy(() -> client.issue("req-1", "csr", "dev-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");

        assertThatThrownBy(() -> client.get("id-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");

        assertThatThrownBy(() -> client.revoke("id-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void testListSuccessWith10FieldsCleanObjectAndProjectIdCheck() {
        IssuerBridgeSettings settings = createValidSettings();
        IssuerBridgeClient client = createTestClient(settings);

        dynamicHandler.set(exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer " + API_KEY);
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/integration/v1/projects/" + PROJECT_ID + "/standard-client-certificates");
            assertThat(exchange.getRequestURI().getQuery()).contains("page=1").contains("size=10").contains("fingerprint=abcdef123456");

            String responseBody = """
                    {
                      "total": 1,
                      "items": [
                        {
                          "id": "cert-item-1",
                          "requestId": "req-001",
                          "projectId": "%s",
                          "fingerprint": "abcdef123456",
                          "serial": "1001",
                          "certificatePem": "-----BEGIN CERTIFICATE-----\\nMIIB...\\n-----END CERTIFICATE-----",
                          "chainPem": "-----BEGIN CERTIFICATE-----\\nCA...\\n-----END CERTIFICATE-----",
                          "deviceLabel": "lab-device",
                          "status": "VALID",
                          "expiresAt": 1800000000000,
                          "maliciousHiddenField": "should-be-filtered-out"
                        }
                      ]
                    }
                    """.formatted(PROJECT_ID);

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        JsonNode result = client.list("abcdef123456", 1, 10);
        assertThat(result.path("total").asInt()).isEqualTo(1);
        JsonNode items = result.path("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isEqualTo(1);

        JsonNode item = items.get(0);
        assertThat(item.path("id").asText()).isEqualTo("cert-item-1");
        assertThat(item.path("requestId").asText()).isEqualTo("req-001");
        assertThat(item.path("projectId").asText()).isEqualTo(PROJECT_ID);
        assertThat(item.path("fingerprint").asText()).isEqualTo("abcdef123456");
        assertThat(item.path("serial").asText()).isEqualTo("1001");
        assertThat(item.path("certificatePem").asText()).contains("BEGIN CERTIFICATE");
        assertThat(item.path("chainPem").asText()).contains("CA");
        assertThat(item.path("deviceLabel").asText()).isEqualTo("lab-device");
        assertThat(item.path("status").asText()).isEqualTo("VALID");
        assertThat(item.path("expiresAt").asLong()).isEqualTo(1800000000000L);
        // 严格白名单过滤：未授权字段不得存在
        assertThat(item.has("maliciousHiddenField")).isFalse();
    }

    @Test
    void testListRejectsProjectIdMismatch() {
        IssuerBridgeSettings settings = createValidSettings();
        IssuerBridgeClient client = createTestClient(settings);

        dynamicHandler.set(exchange -> {
            String responseBody = """
                    {
                      "total": 1,
                      "items": [
                        {
                          "id": "cert-item-1",
                          "requestId": "req-1",
                          "projectId": "other-attacker-project",
                          "fingerprint": "abcdef123456",
                          "serial": "1",
                          "certificatePem": "cert",
                          "chainPem": "chain",
                          "deviceLabel": "",
                          "status": "VALID",
                          "expiresAt": 1700000000000
                        }
                      ]
                    }
                    """;
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        assertThatThrownBy(() -> client.list("abcdef123456", 1, 10))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("projectId");
    }

    @Test
    void testListRejectsInvalidSizeOrPage() {
        IssuerBridgeSettings settings = createValidSettings();
        IssuerBridgeClient client = createTestClient(settings);

        assertThatThrownBy(() -> client.list("fp", -1, 10))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> client.list("fp", 0, 0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> client.list("fp", 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testListRejectsNegativeTotal() {
        IssuerBridgeSettings settings = createValidSettings();
        IssuerBridgeClient client = createTestClient(settings);

        dynamicHandler.set(exchange -> {
            String responseBody = "{\"total\": -1, \"items\": []}";
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        assertThatThrownBy(() -> client.list(null, 0, 10))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testIssueSuccessAndValidation() {
        IssuerBridgeSettings settings = createValidSettings();
        IssuerBridgeClient client = createTestClient(settings);

        dynamicHandler.set(exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer " + API_KEY);
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).contains("application/json");
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/integration/v1/projects/" + PROJECT_ID + "/standard-client-certificates");

            try {
                JsonNode reqJson = MAPPER.readTree(exchange.getRequestBody());
                assertThat(reqJson.path("requestId").asText()).isEqualTo("req-999");
                assertThat(reqJson.path("csrPem").asText()).contains("BEGIN CERTIFICATE REQUEST");
                assertThat(reqJson.path("deviceLabel").asText()).isEqualTo("my-laptop");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            String responseBody = """
                    {
                      "id": "new-cert-101",
                      "requestId": "req-999",
                      "projectId": "%s",
                      "fingerprint": "11223344",
                      "serial": "1002",
                      "certificatePem": "-----BEGIN CERTIFICATE-----\\nMIIB...\\n-----END CERTIFICATE-----",
                      "chainPem": "-----BEGIN CERTIFICATE-----\\nCA...\\n-----END CERTIFICATE-----",
                      "deviceLabel": "my-laptop",
                      "status": "VALID",
                      "expiresAt": 1900000000000,
                      "unexpectedPrivilege": "admin-root"
                    }
                    """.formatted(PROJECT_ID);

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        JsonNode result = client.issue("req-999", "-----BEGIN CERTIFICATE REQUEST-----\nMIIB...\n-----END CERTIFICATE REQUEST-----", "my-laptop");
        assertThat(result.path("id").asText()).isEqualTo("new-cert-101");
        assertThat(result.path("requestId").asText()).isEqualTo("req-999");
        assertThat(result.path("projectId").asText()).isEqualTo(PROJECT_ID);
        assertThat(result.path("fingerprint").asText()).isEqualTo("11223344");
        assertThat(result.path("serial").asText()).isEqualTo("1002");
        assertThat(result.path("certificatePem").asText()).contains("BEGIN CERTIFICATE");
        assertThat(result.path("chainPem").asText()).contains("CA");
        assertThat(result.path("status").asText()).isEqualTo("VALID");
        assertThat(result.path("deviceLabel").asText()).isEqualTo("my-laptop");
        assertThat(result.path("expiresAt").asLong()).isEqualTo(1900000000000L);
        assertThat(result.has("unexpectedPrivilege")).isFalse();
    }

    @Test
    void testIssueRejectsRequestIdMismatch() {
        IssuerBridgeSettings settings = createValidSettings();
        IssuerBridgeClient client = createTestClient(settings);

        dynamicHandler.set(exchange -> {
            String responseBody = """
                    {
                      "id": "new-cert-101",
                      "requestId": "different-req-id",
                      "projectId": "%s",
                      "fingerprint": "1122",
                      "serial": "1",
                      "certificatePem": "cert",
                      "chainPem": "chain",
                      "deviceLabel": "",
                      "status": "VALID",
                      "expiresAt": 1900000000000
                    }
                    """.formatted(PROJECT_ID);

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        assertThatThrownBy(() -> client.issue("req-999", "csr", "device"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("requestId mismatch");
    }

    @Test
    void testIssueRejectsDeviceLabelMismatch() {
        IssuerBridgeSettings settings = createValidSettings();
        IssuerBridgeClient client = createTestClient(settings);

        dynamicHandler.set(exchange -> {
            String responseBody = """
                    {
                      "id": "new-cert-101",
                      "requestId": "req-999",
                      "projectId": "%s",
                      "fingerprint": "1122",
                      "serial": "1",
                      "certificatePem": "cert",
                      "chainPem": "chain",
                      "deviceLabel": "different-device",
                      "status": "VALID",
                      "expiresAt": 1900000000000
                    }
                    """.formatted(PROJECT_ID);

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        assertThatThrownBy(() -> client.issue("req-999", "csr", "my-laptop"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("deviceLabel mismatch");
    }

    @Test
    void testGetSuccessAndPathIdCheck() {
        IssuerBridgeSettings settings = createValidSettings();
        IssuerBridgeClient client = createTestClient(settings);

        dynamicHandler.set(exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/integration/v1/projects/" + PROJECT_ID + "/standard-client-certificates/cert-777");

            String responseBody = """
                    {
                      "id": "cert-777",
                      "requestId": "req-777",
                      "projectId": "%s",
                      "fingerprint": "aabbccdd",
                      "serial": "1003",
                      "certificatePem": "cert",
                      "chainPem": "chain",
                      "deviceLabel": "dev",
                      "status": "VALID",
                      "expiresAt": 1800000000000,
                      "internalSecretField": "should-be-omitted"
                    }
                    """.formatted(PROJECT_ID);

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        JsonNode result = client.get("cert-777");
        assertThat(result.path("id").asText()).isEqualTo("cert-777");
        assertThat(result.path("projectId").asText()).isEqualTo(PROJECT_ID);
        assertThat(result.path("serial").asText()).isEqualTo("1003");
        assertThat(result.path("chainPem").asText()).isEqualTo("chain");
        assertThat(result.has("internalSecretField")).isFalse();
    }

    @Test
    void testGetRejectsPathIdMismatch() {
        IssuerBridgeSettings settings = createValidSettings();
        IssuerBridgeClient client = createTestClient(settings);

        dynamicHandler.set(exchange -> {
            String responseBody = """
                    {
                      "id": "cert-wrong-id",
                      "requestId": "req-1",
                      "projectId": "%s",
                      "fingerprint": "fp",
                      "serial": "1",
                      "certificatePem": "cert",
                      "chainPem": "chain",
                      "deviceLabel": "",
                      "status": "VALID",
                      "expiresAt": 1800000000000
                    }
                    """.formatted(PROJECT_ID);

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        assertThatThrownBy(() -> client.get("cert-777"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("id mismatch");
    }

    @Test
    void testGetRejectsDotOrDoubleDot() {
        IssuerBridgeSettings settings = createValidSettings();
        IssuerBridgeClient client = createTestClient(settings);

        assertThatThrownBy(() -> client.get("."))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> client.get(".."))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> client.get("../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testRevokeSuccessAndValidation() {
        IssuerBridgeSettings settings = createValidSettings();
        IssuerBridgeClient client = createTestClient(settings);

        dynamicHandler.set(exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST");
            assertThat(exchange.getRequestURI().getPath()).isEqualTo("/integration/v1/projects/" + PROJECT_ID + "/standard-client-certificates/cert-888/revoke");

            String responseBody = """
                    {
                      "id": "cert-888",
                      "requestId": "req-888",
                      "projectId": "%s",
                      "fingerprint": "fp-888",
                      "serial": "1008",
                      "certificatePem": "cert",
                      "chainPem": "chain",
                      "deviceLabel": "dev",
                      "status": "REVOKED",
                      "expiresAt": 1800000000000
                    }
                    """.formatted(PROJECT_ID);

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        JsonNode result = client.revoke("cert-888");
        assertThat(result.path("id").asText()).isEqualTo("cert-888");
        assertThat(result.path("projectId").asText()).isEqualTo(PROJECT_ID);
        assertThat(result.path("status").asText()).isEqualTo("REVOKED");
    }

    @Test
    void testRevokeRejectsInvalidStatusNotRevoked() {
        IssuerBridgeSettings settings = createValidSettings();
        IssuerBridgeClient client = createTestClient(settings);

        dynamicHandler.set(exchange -> {
            String responseBody = """
                    {
                      "id": "cert-888",
                      "requestId": "req-888",
                      "projectId": "%s",
                      "fingerprint": "fp",
                      "serial": "1",
                      "certificatePem": "cert",
                      "chainPem": "chain",
                      "deviceLabel": "",
                      "status": "VALID",
                      "expiresAt": 1800000000000
                    }
                    """.formatted(PROJECT_ID);

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        assertThatThrownBy(() -> client.revoke("cert-888"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("REVOKED");
    }

    @Test
    void testRevokeRejectsIdMismatch() {
        IssuerBridgeSettings settings = createValidSettings();
        IssuerBridgeClient client = createTestClient(settings);

        dynamicHandler.set(exchange -> {
            String responseBody = """
                    {
                      "id": "other-id",
                      "requestId": "req-888",
                      "projectId": "%s",
                      "fingerprint": "fp",
                      "serial": "1",
                      "certificatePem": "cert",
                      "chainPem": "chain",
                      "deviceLabel": "",
                      "status": "REVOKED",
                      "expiresAt": 1800000000000
                    }
                    """.formatted(PROJECT_ID);

            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        assertThatThrownBy(() -> client.revoke("cert-888"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("id mismatch");
    }

    @Test
    void testRedirectIsForbidden() {
        IssuerBridgeSettings settings = createValidSettings();
        IssuerBridgeClient client = createTestClient(settings);

        dynamicHandler.set(exchange -> {
            exchange.getResponseHeaders().set("Location", "https://attacker.evil.com/redirected");
            exchange.sendResponseHeaders(302, -1);
        });

        assertThatThrownBy(() -> client.get("cert-777"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Issuer bridge redirect is forbidden");
    }

    @Test
    void testHttpErrorThrowsExceptionWithoutLeakingBody() {
        IssuerBridgeSettings settings = createValidSettings();
        IssuerBridgeClient client = createTestClient(settings);

        dynamicHandler.set(exchange -> {
            String sensitiveInternalError = "{\"error\": \"database internal password leaked\", \"secret\": 12345}";
            byte[] bytes = sensitiveInternalError.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        assertThatThrownBy(() -> client.get("cert-500"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Issuer bridge request failed with HTTP 500")
                .hasMessageNotContaining("password");
    }

    @Test
    void testUnknownOrInvalidResponseRejected() {
        IssuerBridgeSettings settings = createValidSettings();
        IssuerBridgeClient client = createTestClient(settings);

        dynamicHandler.set(exchange -> {
            String html = "<html><body>502 Bad Gateway</body></html>";
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        assertThatThrownBy(() -> client.get("cert-777"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid response content type");
    }
}
