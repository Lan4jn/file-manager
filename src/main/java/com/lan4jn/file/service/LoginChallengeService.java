package com.lan4jn.file.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginChallengeService {

    private static final long EXPIRE_MILLIS = 2 * 60 * 1000L;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, ChallengeRecord> challenges = new ConcurrentHashMap<>();

    public LoginChallenge issue(String username) {
        cleanupExpired();
        byte[] nonceBytes = new byte[24];
        secureRandom.nextBytes(nonceBytes);
        String challengeId = UUID.randomUUID().toString();
        String nonce = Base64.getEncoder().encodeToString(nonceBytes);
        long expireAt = System.currentTimeMillis() + EXPIRE_MILLIS;
        challenges.put(challengeId, new ChallengeRecord(normalize(username), nonce, expireAt));
        return new LoginChallenge(challengeId, nonce, EXPIRE_MILLIS / 1000);
    }

    public String consume(String username, String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            return null;
        }
        ChallengeRecord record = challenges.remove(challengeId);
        if (record == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (record.expireAt < now) {
            return null;
        }
        if (!record.username.equals(normalize(username))) {
            return null;
        }
        return record.nonce;
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        challenges.entrySet().removeIf(entry -> entry.getValue().expireAt < now);
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    public record LoginChallenge(String challengeId, String nonce, long expiresInSeconds) {
    }

    private record ChallengeRecord(String username, String nonce, long expireAt) {
    }
}
