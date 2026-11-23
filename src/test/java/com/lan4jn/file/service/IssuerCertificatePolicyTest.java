package com.lan4jn.file.service;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.security.KeyPairGenerator;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IssuerCertificatePolicyTest {
    static final String FP = "ad".repeat(32);
    static final long NOW = 2_000_000_000_000L;
    final AtomicLong ticks = new AtomicLong();
    final Clock clock = mock(Clock.class);
    final IssuerBridgeSettings settings = mock(IssuerBridgeSettings.class);
    final IssuerBridgeClient client = mock(IssuerBridgeClient.class);
    IssuerCertificatePolicy policy;

    @BeforeEach void setup() {
        when(settings.isEnabled()).thenReturn(true);
        when(settings.getProjectId()).thenReturn("space");
        when(clock.millis()).thenReturn(NOW);
        policy = new IssuerCertificatePolicy(settings, client, clock, ticks::get);
    }
    ObjectNode page(String fp, String status, long expiresAt) {
        ObjectNode result = JsonNodeFactory.instance.objectNode().put("total", 1);
        result.putArray("items").addObject().put("id", "ref").put("projectId", "space")
                .put("requestId", "req").put("deviceLabel", "demo").put("fingerprint", fp)
                .put("serial", "42").put("expiresAt", expiresAt).put("status", status)
                .put("certificatePem", "leaf").put("chainPem", "chain");
        return result;
    }
    @Test void verifiedLeaseCannotSurviveItsDeadlineOrAnUnavailableIssuer() {
        when(client.list(FP, 0, 50)).thenReturn(page(FP, "VALID", NOW + 3_600_000)).thenThrow(new IllegalStateException());
        assertThat(policy.isValid(FP)).isTrue();
        assertThat(policy.isValid(FP)).isTrue();
        ticks.addAndGet(TimeUnit.SECONDS.toNanos(30));
        assertThat(policy.isValid(FP)).isFalse();
    }
    @Test void upstreamExpiryClipsCachedLeaseEvenBeforeTtl() {
        when(client.list(FP, 0, 50)).thenReturn(page(FP, "VALID", NOW + 1));
        assertThat(policy.isValid(FP)).isTrue();
        when(clock.millis()).thenReturn(NOW + 1);
        assertThat(policy.isValid(FP)).isFalse();
    }
    @Test void rejectsWrongFingerprintProjectStatusAndAmbiguousPage() {
        ObjectNode otherProject = page(FP, "VALID", NOW + 1000);
        ((ObjectNode)otherProject.at("/items/0")).put("projectId", "other");
        when(client.list(FP, 0, 50)).thenReturn(page("bb".repeat(32), "VALID", NOW + 1000), otherProject,
                page(FP, "REVOKED", NOW + 1000), page(FP, "EXPIRED", NOW + 1000), page(FP, "VALID", NOW + 1000).put("total", 2));
        for (int i=0; i<5; i++) assertThat(policy.isValid(FP)).isFalse();
    }
    @Test void localRevokeDropsCachedPermitsEvenOnAmbiguousTransportFailure() {
        when(client.list(FP, 0, 50)).thenReturn(page(FP,"VALID",NOW + 60_000)).thenThrow(new IllegalStateException());
        when(client.revoke("ref")).thenThrow(new IllegalStateException());
        assertThat(policy.isValid(FP)).isTrue();
        assertThatThrownBy(() -> policy.revoke("ref")).isInstanceOf(IllegalStateException.class);
        assertThat(policy.isValid(FP)).isFalse();
    }
    @Test void oldPermitEvictedWhenCacheIsFull() {
        when(client.list(anyString(), eq(0), eq(50))).thenAnswer(call -> page(call.getArgument(0), "VALID", NOW + 60_000));
        assertThat(policy.isValid(FP)).isTrue();
        for (int i = 0; i < 1024; i++) assertThat(policy.isValid(String.format("%064x", i))).isTrue();
        doThrow(new IllegalStateException()).when(client).list(FP, 0, 50);
        assertThat(policy.isValid(FP)).isFalse();
    }
    @Test void revokeDuringLookupPreventsStaleReadFromRepopulatingCache() {
        when(client.list(FP, 0, 50)).thenAnswer(call -> {
            policy.revoke("ref"); // The successful read was issued before local revocation finished.
            return page(FP, "VALID", NOW + 60_000);
        });
        assertThat(policy.isValid(FP)).isFalse();
    }
    @Test void issuerIsDeterminedBySignatureNotSubjectText() throws Exception {
        // Public-only fixture, self-signed for a direct signature check; no private key in the repo.
        byte[] der = Base64.getDecoder().decode("MIIBOzCB4qADAgECAgkAx9C2YUtIMbIwCgYIKoZIzj0EAwIwEjEQMA4GA1UEAxMHRml4dHVyZTAeFw0yNjExMjAwNjM0NTNaFw0yNjEyMjAwNjM0NTNaMBIxEDAOBgNVBAMTB0ZpeHR1cmUwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAStYWYjxkZcZ9Z1okB+6Kx9Nu3MB5qYzJ/icbAQdj6IITLVLdge+O1SLx9MTlW4AHP3tKlss00R7YBkusM46vU1oyEwHzAdBgNVHQ4EFgQU1AmsRjqkRw6BIWl+VdjmXdiheTAwCgYIKoZIzj0EAwIDSAAwRQIgSDWI3BEr/chSFpQ3Tcp24yEDM22TmarybnOtkCrriy0CIQDo6PCeYyH/I+OxjkB/wkrsgtcnkTjV7hZC6MpvHzp29A==");
        X509Certificate leaf = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
        when(settings.getIssuerKeys()).thenReturn(List.of(leaf.getPublicKey()));
        assertThat(policy.isManaged(leaf)).isTrue();
        when(settings.getIssuerKeys()).thenReturn(List.of(KeyPairGenerator.getInstance("EC").generateKeyPair().getPublic()));
        assertThat(policy.isManaged(leaf)).isFalse();
    }
}
