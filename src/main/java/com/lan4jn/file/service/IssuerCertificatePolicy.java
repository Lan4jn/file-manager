package com.lan4jn.file.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/** Adds issuer-side authorization to a leaf that Tomcat has already authenticated. */
@Service
public class IssuerCertificatePolicy {
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");
    private static final long TTL = TimeUnit.SECONDS.toNanos(30);
    private static final int CAPACITY = 1024;
    private final IssuerBridgeSettings settings;
    private final IssuerBridgeClient client;
    private final Clock clock;
    private final LongSupplier ticker;
    private record Lease(long checked, long expiresAt) {}
    private final Map<String, Lease> leases = new LinkedHashMap<>(16, 0.75f, true);
    // In-flight lookups cannot repopulate a permit after a revoke has invalidated the cache.
    private long epoch;

    @Autowired
    public IssuerCertificatePolicy(IssuerBridgeSettings settings, IssuerBridgeClient client) {
        this(settings, client, Clock.systemUTC(), System::nanoTime);
    }

    IssuerCertificatePolicy(IssuerBridgeSettings settings, IssuerBridgeClient client, Clock clock, LongSupplier ticker) {
        this.settings = settings;
        this.client = client;
        this.clock = clock;
        this.ticker = ticker;
    }

    public boolean isEnabled() { return settings.isEnabled(); }

    public boolean isManaged(X509Certificate leaf) {
        List<PublicKey> keys = settings.getIssuerKeys();
        if (leaf == null || keys.isEmpty()) throw new IllegalStateException("无法判断证书签发方");
        for (PublicKey key : keys) {
            try {
                leaf.verify(key);
                return true;
            } catch (SignatureException | InvalidKeyException mismatch) {
                // Another issuer key, potentially using a different signature algorithm.
            } catch (GeneralSecurityException error) {
                throw new IllegalStateException("无法判断证书签发方", error);
            }
        }
        return false;
    }

    public boolean isValid(String fingerprint) {
        if (!isEnabled() || fingerprint == null || !FINGERPRINT.matcher(fingerprint).matches()) return false;
        final long start = ticker.getAsLong();
        final long generation;
        synchronized (leases) {
            Lease cached = leases.get(fingerprint);
            if (cached != null && fresh(cached)) return true;
            leases.remove(fingerprint);
            generation = epoch;
        }
        try {
            JsonNode page = client.list(fingerprint, 0, 50);
            JsonNode items = page.path("items");
            if (page.path("total").asLong(-1) != 1 || !items.isArray() || items.size() != 1) return false;
            JsonNode item = items.get(0);
            if (!fingerprint.equalsIgnoreCase(item.path("fingerprint").asText())
                    || !settings.getProjectId().equals(item.path("projectId").asText())
                    || !"VALID".equals(item.path("status").asText()) || !item.path("expiresAt").canConvertToLong()) return false;
            Lease verified = new Lease(start, item.get("expiresAt").longValue());
            synchronized (leases) {
                if (epoch != generation || !fresh(verified)) return false;
                leases.put(fingerprint, verified);
                if (leases.size() > CAPACITY) leases.remove(leases.keySet().iterator().next());
            }
            return true;
        } catch (RuntimeException unavailable) {
            return false; // Never extend the last known VALID when the issuer is unavailable or malformed.
        }
    }

    private boolean fresh(Lease lease) {
        return ticker.getAsLong() - lease.checked() < TTL && clock.millis() < lease.expiresAt();
    }

    public JsonNode revoke(String id) {
        try {
            return client.revoke(id);
        } finally {
            synchronized (leases) {
                ++epoch;
                leases.clear(); // Even a lost HTTP response can mean the remote revoke succeeded.
            }
        }
    }
}
