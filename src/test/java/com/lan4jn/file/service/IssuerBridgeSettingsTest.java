package com.lan4jn.file.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IssuerBridgeSettingsTest {

    @TempDir
    static Path tempDir;

    private static String validCaPemPath;
    private static String nonCaLeafPemPath;

    @BeforeAll
    static void setupCa() throws Exception {
        Path p12 = tempDir.resolve("test-ca.p12");
        Path pem = tempDir.resolve("test-ca.pem");

        String exe = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows") ? "keytool.exe" : "keytool";
        String keytoolBin = Path.of(System.getProperty("java.home"), "bin", exe).toString();

        // 1. 生成自签名 CA（带 BC:critical=ca:true 和 KU:critical=keyCertSign）
        runCommand(tempDir, keytoolBin, "-genkeypair", "-alias", "testca", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=Test CA,O=Lan4jn,C=CN", "-validity", "1", "-keystore", p12.toString(),
                "-storetype", "PKCS12", "-storepass", "changeit", "-ext", "BC:critical=ca:true", "-ext", "KU:critical=keyCertSign");

        // 2. 导出 RFC (PEM) 格式
        runCommand(tempDir, keytoolBin, "-exportcert", "-rfc", "-alias", "testca", "-file", pem.toString(),
                "-keystore", p12.toString(), "-storetype", "PKCS12", "-storepass", "changeit");

        validCaPemPath = pem.toAbsolutePath().toString();

        // 3. 生成一个非 CA 叶证书（用于验证拒绝非 CA 叶）
        Path leafP12 = tempDir.resolve("leaf.p12");
        Path leafPem = tempDir.resolve("leaf.pem");
        runCommand(tempDir, keytoolBin, "-genkeypair", "-alias", "leaf", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=leaf.example.com,O=Lan4jn,C=CN", "-validity", "1", "-keystore", leafP12.toString(),
                "-storetype", "PKCS12", "-storepass", "changeit", "-ext", "BC:critical=ca:false", "-ext", "KU=digitalSignature");

        runCommand(tempDir, keytoolBin, "-exportcert", "-rfc", "-alias", "leaf", "-file", leafPem.toString(),
                "-keystore", leafP12.toString(), "-storetype", "PKCS12", "-storepass", "changeit");
        nonCaLeafPemPath = leafPem.toAbsolutePath().toString();
    }

    private static void runCommand(Path workDir, String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).directory(workDir.toFile()).redirectErrorStream(true).start();
        byte[] out = p.getInputStream().readAllBytes();
        int exit = p.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("Command failed [" + exit + "]: " + new String(out, StandardCharsets.UTF_8));
        }
    }

    @Test
    void testDisabledByDefault() {
        IssuerBridgeSettings settings = new IssuerBridgeSettings(false, "", "", "", "");
        assertThat(settings.isEnabled()).isFalse();
        assertThat(settings.getIssuerKeys()).isEmpty();
    }

    @Test
    void testEnabledSuccessLoadsKeysAndProjectId() {
        IssuerBridgeSettings settings = new IssuerBridgeSettings(
                true,
                "https://issuer.internal.lan4jn.com:8443",
                "project-alpha",
                "secret-bearer-token-12345",
                validCaPemPath
        );
        settings.init();

        assertThat(settings.isEnabled()).isTrue();
        assertThat(settings.getProjectId()).isEqualTo("project-alpha");
        assertThat(settings.getBaseUrl()).isEqualTo("https://issuer.internal.lan4jn.com:8443");
        assertThat(settings.getApiKey()).isEqualTo("secret-bearer-token-12345");

        List<PublicKey> keys = settings.getIssuerKeys();
        assertThat(keys).isNotEmpty();
        assertThat(keys.get(0).getAlgorithm()).isEqualTo("RSA");

        // 验证返回的列表不可变
        assertThatThrownBy(() -> keys.clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testEnabledFailsWhenBaseUrlNotHttps() {
        IssuerBridgeSettings settings = new IssuerBridgeSettings(
                true,
                "http://insecure.internal.lan4jn.com:8080",
                "project-alpha",
                "secret-bearer-token-12345",
                validCaPemPath
        );
        assertThatThrownBy(settings::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void testEnabledFailsWhenBaseUrlContainsUserInfoOrQueryOrPath() {
        // 含 userInfo
        assertThatThrownBy(() -> new IssuerBridgeSettings(
                true, "https://user:pass@issuer.lan4jn.com", "proj", "key", validCaPemPath).init())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("pass");

        // 含 query
        assertThatThrownBy(() -> new IssuerBridgeSettings(
                true, "https://issuer.lan4jn.com?hack=1", "proj", "key", validCaPemPath).init())
                .isInstanceOf(IllegalStateException.class);

        // 含 fragment
        assertThatThrownBy(() -> new IssuerBridgeSettings(
                true, "https://issuer.lan4jn.com#anchor", "proj", "key", validCaPemPath).init())
                .isInstanceOf(IllegalStateException.class);

        // 含非根路径
        assertThatThrownBy(() -> new IssuerBridgeSettings(
                true, "https://issuer.lan4jn.com/some/path", "proj", "key", validCaPemPath).init())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testEnabledFailsWhenBaseUrlMissing() {
        IssuerBridgeSettings settings = new IssuerBridgeSettings(
                true,
                "",
                "project-alpha",
                "secret-bearer-token-12345",
                validCaPemPath
        );
        assertThatThrownBy(settings::init)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testEnabledFailsWhenProjectIdMissingOrDot() {
        assertThatThrownBy(() -> new IssuerBridgeSettings(
                true, "https://issuer.lan4jn.com", "", "key", validCaPemPath).init())
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new IssuerBridgeSettings(
                true, "https://issuer.lan4jn.com", ".", "key", validCaPemPath).init())
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new IssuerBridgeSettings(
                true, "https://issuer.lan4jn.com", "..", "key", validCaPemPath).init())
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new IssuerBridgeSettings(
                true, "https://issuer.lan4jn.com", "../bad-project", "key", validCaPemPath).init())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testEnabledFailsWhenApiKeyMissingOrContainsWhitespace() {
        assertThatThrownBy(() -> new IssuerBridgeSettings(
                true, "https://issuer.lan4jn.com", "project-alpha", "   ", validCaPemPath).init())
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new IssuerBridgeSettings(
                true, "https://issuer.lan4jn.com", "project-alpha", "key with space", validCaPemPath).init())
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new IssuerBridgeSettings(
                true, "https://issuer.lan4jn.com", "project-alpha", "key\nwith-newline", validCaPemPath).init())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testEnabledFailsWhenCaFileMissingOrNotFound() {
        IssuerBridgeSettings settings = new IssuerBridgeSettings(
                true,
                "https://issuer.internal.lan4jn.com",
                "project-alpha",
                "secret-bearer-token-12345",
                tempDir.resolve("non-existent-ca.pem").toString()
        );
        assertThatThrownBy(settings::init)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testEnabledFailsWhenCaFileIsNonCaLeaf() {
        IssuerBridgeSettings settings = new IssuerBridgeSettings(
                true,
                "https://issuer.internal.lan4jn.com",
                "project-alpha",
                "secret-bearer-token-12345",
                nonCaLeafPemPath
        );
        assertThatThrownBy(settings::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a CA");
    }

    @Test
    void testEnabledFailsWhenCaFileContainsNoValidCert() throws Exception {
        Path invalidFile = tempDir.resolve("invalid-ca.pem");
        Files.writeString(invalidFile, "NOT A CERTIFICATE CONTENT");

        IssuerBridgeSettings settings = new IssuerBridgeSettings(
                true,
                "https://issuer.internal.lan4jn.com",
                "project-alpha",
                "secret-bearer-token-12345",
                invalidFile.toString()
        );
        assertThatThrownBy(settings::init)
                .isInstanceOf(IllegalStateException.class);
    }
}
