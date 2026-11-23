package com.lan4jn.file.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class ClientCertificateTlsIntegrationTest {
    private static final String STORE_PASSWORD = "ephemeral-test";
    private static final String BINDINGS = "/api/account/certificates";
    private static final String CERT_LOGIN = "/api/auth/certificate-login";
    private static final String PASSWORD = "isolated-account-password";
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir static Path temp;
    @LocalServerPort int port;
    @MockBean MinioClient minio; // Otherwise an unrelated startup bean would contact the real bucket.

    @DynamicPropertySource
    static void isolateServer(DynamicPropertyRegistry r) throws Exception {
        generateCa("authority");
        generateCa("other-authority");
        issue("server", "authority", "serverAuth");
        issue("device", "authority", "clientAuth");
        issue("other-device", "other-authority", "clientAuth");
        keytool("-importcert", "trust", "-alias", "authority", "-file", "authority.cer", "-noprompt");

        r.add("server.address", () -> "127.0.0.1");
        r.add("server.port", () -> 0);
        r.add("server.servlet.context-path", () -> "/");
        r.add("app.security.http-port", () -> -1);
        r.add("server.ssl.enabled", () -> true);
        r.add("server.ssl.client-auth", () -> "want");
        r.add("server.ssl.key-store", () -> temp.resolve("server.p12").toUri().toString());
        r.add("server.ssl.key-store-type", () -> "PKCS12");
        r.add("server.ssl.key-store-password", () -> STORE_PASSWORD);
        r.add("server.ssl.key-alias", () -> "server");
        r.add("server.ssl.trust-store", () -> temp.resolve("trust.p12").toUri().toString());
        r.add("server.ssl.trust-store-type", () -> "PKCS12");
        r.add("server.ssl.trust-store-password", () -> STORE_PASSWORD);
        r.add("app.security.client-certificate.enabled", () -> true);
        r.add("spring.datasource.url", () -> "jdbc:sqlite:" + temp.resolve("users.db"));
        r.add("spring.datasource.driver-class-name", () -> "org.sqlite.JDBC");
        r.add("spring.jpa.database-platform", () -> "org.hibernate.community.dialect.SQLiteDialect");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        r.add("app.datasource-mirror.enabled", () -> false);
        r.add("jwt.secret", () -> "test-only-signing-secret-not-used-outside-this-context-0123456789");
        r.add("jwt.expiration", () -> 600000);
    }

    @Test
    void noCertificateStillAllowsPasswordLogin() throws Exception {
        HttpClient anonymous = client(null);
        JsonNode account = createAccountAndLogin(anonymous, "password-only");
        assertThat(account.path("token").asText()).isNotBlank();
        JsonNode denied = request(anonymous, "POST", CERT_LOGIN, null, null);
        assertThat(denied.path("code").asInt()).isEqualTo(401);
        assertThat(denied.at("/data/token").isMissingNode()).isTrue();
    }

    @Test
    void trustedDeviceLogsIntoBoundAccountUntilRevocation() throws Exception {
        HttpClient anonymous = client(null);
        HttpClient device = client("device");
        JsonNode account = createAccountAndLogin(anonymous, "existing-user");
        String passwordToken = account.path("token").asText();
        JsonNode binding = success(request(device, "POST", BINDINGS, passwordToken, Map.of("password", PASSWORD)));
        long id = binding.path("id").asLong();
        assertThat(id).isPositive();
        JsonNode list = success(request(anonymous, "GET", BINDINGS, passwordToken, null));
        assertThat(list.isArray()).isTrue();
        assertThat(list.size()).isEqualTo(1);
        assertThat(list.get(0).path("id").asLong()).isEqualTo(id);

        JsonNode signedIn = success(request(device, "POST", CERT_LOGIN, null, null));
        String certToken = signedIn.path("token").asText();
        assertThat(certToken).isNotBlank();
        JsonNode info = success(request(device, "GET", "/api/auth/info", certToken, null));
        assertThat(info.path("username").asText()).isEqualTo("existing-user");
        assertThat(info.path("userId")).isEqualTo(account.path("userId"));

        success(request(anonymous, "DELETE", BINDINGS + "/" + id, passwordToken, null));
        JsonNode denied = request(device, "POST", CERT_LOGIN, null, null);
        assertThat(denied.path("code").asInt()).isEqualTo(401);
        assertThat(denied.at("/data/token").isMissingNode()).isTrue();
        HttpResponse<String> revoked = exchange(device, "GET", BINDINGS, certToken, null);
        assertThat(revoked.statusCode()).as("revoked device JWT").isIn(401, 403);
        assertThat(success(request(anonymous, "GET", BINDINGS, passwordToken, null)).size()).isZero();
    }

    @Test
    void untrustedDeviceIsRejectedDuringTlsHandshake() throws Exception {
        // TLS 1.2 delivers the server's verdict inside startHandshake(), before HTTP.
        handshake("device"); // Positive control for protocol, server identity and offered ciphers.
        SSLHandshakeException failure = assertThrows(SSLHandshakeException.class, () -> handshake("other-device"));
        assertThat(failure.getMessage()).containsPattern("fatal alert: (certificate_unknown|bad_certificate|unknown_ca)");
    }

    private void handshake(String identity) throws Exception {
        try (SSLSocket socket = (SSLSocket) context(identity).getSocketFactory().createSocket("127.0.0.1", port)) {
            socket.setSoTimeout(10000);
            SSLParameters p = socket.getSSLParameters();
            p.setProtocols(new String[]{"TLSv1.2"});
            p.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(p);
            socket.startHandshake();
        }
    }

    private JsonNode createAccountAndLogin(HttpClient client, String name) throws Exception {
        Map<String, String> credentials = Map.of("username", name, "password", PASSWORD);
        success(request(client, "POST", "/api/auth/register", null, credentials));
        JsonNode account = success(request(client, "POST", "/api/auth/login", null, credentials));
        assertThat(account.path("username").asText()).isEqualTo(name);
        assertThat(account.path("userId").asLong()).isPositive();
        return account;
    }

    private static JsonNode success(JsonNode response) {
        assertThat(response.path("code").asInt()).as("Result: %s", response.path("message")).isEqualTo(200);
        return response.path("data");
    }

    private JsonNode request(HttpClient client, String method, String path, String token, Map<String, String> json) throws Exception {
        HttpResponse<String> response = exchange(client, method, path, token, json);
        assertThat(response.statusCode()).as("%s %s", method, path).isEqualTo(200);
        return JSON.readTree(response.body());
    }

    private HttpResponse<String> exchange(HttpClient client, String method, String path, String token, Map<String, String> json) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
        if (token != null) req.header("Authorization", "Bearer " + token);
        req.method(method, json == null ? HttpRequest.BodyPublishers.noBody() :
                HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(json)));
        return client.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpClient client(String identity) throws Exception {
        return HttpClient.newBuilder().sslContext(context(identity)).connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_1_1).build();
    }

    private static SSLContext context(String identity) throws Exception {
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(store("trust"));
        KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        KeyStore empty = KeyStore.getInstance("PKCS12");
        empty.load(null, null);
        keys.init(identity == null ? empty : store(identity), STORE_PASSWORD.toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
        return context;
    }

    private static KeyStore store(String name) throws Exception {
        return KeyStore.getInstance(temp.resolve(name + ".p12").toFile(), STORE_PASSWORD.toCharArray());
    }

    private static void generateCa(String name) throws Exception {
        // Same issuer DN, different keys: JSSE offers the unrelated chain instead of silently omitting it.
        keytool("-genkeypair", name, "-alias", name, "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=Ephemeral Test CA",
                "-ext", "BC:critical=ca:true", "-ext", "KU:critical=keyCertSign", "-validity", "2");
        keytool("-exportcert", name, "-alias", name, "-file", name + ".cer");
    }

    private static void issue(String name, String ca, String usage) throws Exception {
        keytool("-genkeypair", name, "-alias", name, "-keyalg", "RSA", "-keysize", "2048", "-dname", "CN=" + name);
        keytool("-certreq", name, "-alias", name, "-file", name + ".csr");
        keytool("-gencert", ca, "-alias", ca, "-infile", name + ".csr", "-outfile", name + ".cer", "-validity", "2",
                "-ext", "EKU=" + usage, "-ext", "KU=digitalSignature", "-ext", "SAN=ip:127.0.0.1");
        keytool("-importcert", name, "-alias", ca, "-file", ca + ".cer", "-noprompt");
        keytool("-importcert", name, "-alias", name, "-file", name + ".cer", "-noprompt");
    }

    private static void keytool(String operation, String store, String... arguments) throws Exception {
        String exe = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows") ? "keytool.exe" : "keytool";
        List<String> args = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", exe).toString(), operation,
                "-keystore", store + ".p12", "-storetype", "PKCS12", "-storepass", STORE_PASSWORD));
        args.addAll(List.of(arguments));
        Process process = new ProcessBuilder(args).directory(temp.toFile()).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        assertThat(process.waitFor()).as("keytool %s: %s", operation, new String(output, StandardCharsets.UTF_8)).isZero();
    }
}
