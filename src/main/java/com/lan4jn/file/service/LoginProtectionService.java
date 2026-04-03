package com.lan4jn.file.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginProtectionService {

    private final Map<String, LoginAttemptRecord> attempts = new ConcurrentHashMap<>();

    @Value("${app.security.login.max-failures:5}")
    private int maxFailures;

    @Value("${app.security.login.lock-minutes:15}")
    private long lockMinutes;

    public void assertAllowed(String username, HttpServletRequest request) {
        LoginAttemptRecord record = attempts.get(buildKey(username, request));
        if (record == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (record.lockUntil > now) {
            long seconds = Math.max(1, Duration.ofMillis(record.lockUntil - now).getSeconds());
            throw new IllegalStateException("登录失败次数过多，请在 " + seconds + " 秒后再试");
        }
        if (record.lockUntil > 0 && record.lockUntil <= now) {
            attempts.remove(buildKey(username, request));
        }
    }

    public String recordFailure(String username, HttpServletRequest request) {
        String key = buildKey(username, request);
        long now = System.currentTimeMillis();
        LoginAttemptRecord record = attempts.computeIfAbsent(key, ignored -> new LoginAttemptRecord());
        synchronized (record) {
            if (record.lockUntil > now) {
                long seconds = Math.max(1, Duration.ofMillis(record.lockUntil - now).getSeconds());
                return "登录失败次数过多，请在 " + seconds + " 秒后再试";
            }
            record.failures += 1;
            if (record.failures >= Math.max(1, maxFailures)) {
                record.lockUntil = now + Duration.ofMinutes(Math.max(1, lockMinutes)).toMillis();
                long seconds = Duration.ofMinutes(Math.max(1, lockMinutes)).getSeconds();
                return "登录失败次数过多，请在 " + seconds + " 秒后再试";
            }
            int remainingAttempts = Math.max(0, Math.max(1, maxFailures) - record.failures);
            return "用户名不存在或密码错误，剩余 " + remainingAttempts + " 次重试机会";
        }
    }

    public void reset(String username, HttpServletRequest request) {
        attempts.remove(buildKey(username, request));
    }

    public LockStatus getStatus(String username) {
        String key = buildKey(username, null);
        LoginAttemptRecord record = attempts.get(key);
        if (record == null) {
            return new LockStatus(false, null);
        }
        long now = System.currentTimeMillis();
        synchronized (record) {
            if (record.lockUntil > 0 && record.lockUntil <= now) {
                attempts.remove(key);
                return new LockStatus(false, null);
            }
            if (record.lockUntil > now) {
                return new LockStatus(true, record.lockUntil);
            }
            return new LockStatus(false, null);
        }
    }

    public void reset(String username) {
        attempts.remove(buildKey(username, null));
    }

    private String buildKey(String username, HttpServletRequest request) {
        return normalize(username);
    }

    private String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static final class LoginAttemptRecord {
        private int failures;
        private long lockUntil;
    }

    public static final class LockStatus {
        private final boolean locked;
        private final Long unlockTime;

        public LockStatus(boolean locked, Long unlockTime) {
            this.locked = locked;
            this.unlockTime = unlockTime;
        }

        public boolean locked() {
            return locked;
        }

        public Long unlockTime() {
            return unlockTime;
        }
    }
}
