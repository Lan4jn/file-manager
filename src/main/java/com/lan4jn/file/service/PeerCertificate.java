package com.lan4jn.file.service;

import jakarta.servlet.http.HttpServletRequest;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.Optional;

/** Tomcat's verified TLS peer chain. Never accept a request header or body as identity. */
public record PeerCertificate(String fingerprint, long expiresAt, X509Certificate leaf) {
    public PeerCertificate(String fingerprint, long expiresAt) {
        this(fingerprint, expiresAt, null);
    }

    public static Optional<PeerCertificate> from(HttpServletRequest request) {
        if (!request.isSecure() || !(request.getAttribute("jakarta.servlet.request.X509Certificate") instanceof X509Certificate[] chain)
                || chain.length == 0 || chain[0] == null) return Optional.empty();
        try {
            X509Certificate leaf = chain[0];
            leaf.checkValidity();
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(leaf.getEncoded()));
            return Optional.of(new PeerCertificate(fingerprint, leaf.getNotAfter().getTime(), leaf));
        } catch (GeneralSecurityException invalid) {
            return Optional.empty();
        }
    }
}
