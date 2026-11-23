package com.lan4jn.file.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

/** Enabled only for the supported direct Tomcat connector, with explicit client CA trust. */
@Component
@ConditionalOnProperty(name = "app.security.client-certificate.enabled", havingValue = "true")
public class ClientCertificateTlsPolicy {
    public ClientCertificateTlsPolicy(Environment env, ResourceLoader resources) {
        if (!Boolean.TRUE.equals(env.getProperty("server.ssl.enabled", Boolean.class))
                || !"want".equalsIgnoreCase(env.getProperty("server.ssl.client-auth"))
                || env.containsProperty("server.ssl.bundle")) {
            throw new IllegalStateException("证书登录要求显式启用直连 HTTPS，并配置 server.ssl.client-auth=want 与专用 trust-store");
        }
        String path = env.getProperty("server.ssl.trust-store", "").trim();
        String password = env.getProperty("server.ssl.trust-store-password", "");
        if (path.isEmpty() || password.isBlank()) throw new IllegalStateException("须显式配置客户端 CA trust-store 和口令");
        Resource store = path.startsWith("file:") || path.startsWith("classpath:") ? resources.getResource(path) : new FileSystemResource(path);
        String name = store.getFilename() == null ? "" : store.getFilename().toLowerCase(Locale.ROOT);
        if (name.equals("cacerts") || name.equals("jssecacerts")) throw new IllegalStateException("请使用专用客户端 CA 库，而非 JVM 信任库");
        char[] secret = password.toCharArray();
        try (InputStream input = store.getInputStream()) {
            KeyStore keys = KeyStore.getInstance(env.getProperty("server.ssl.trust-store-type", KeyStore.getDefaultType()));
            keys.load(input, secret);
            if (keys.size() == 0) throw new IllegalStateException("客户端 CA 库为空");
            for (String alias : Collections.list(keys.aliases())) {
                if (!keys.isCertificateEntry(alias) || !(keys.getCertificate(alias) instanceof X509Certificate ca)
                        || ca.getBasicConstraints() < 0) throw new IllegalStateException("客户端 CA 库仅可包含受信任的 CA 公钥证书");
                ca.checkValidity();
            }
        } catch (java.io.IOException | java.security.GeneralSecurityException invalid) {
            throw new IllegalStateException("无法校验客户端 CA 库；请核对路径、类型、口令和证书有效期", invalid);
        } finally {
            Arrays.fill(secret, '\0');
        }
    }
}
