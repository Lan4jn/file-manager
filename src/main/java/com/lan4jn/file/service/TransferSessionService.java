package com.lan4jn.file.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class TransferSessionService {

    private static final long DEFAULT_DOWNLOAD_TOKEN_TTL_MS = 60 * 60 * 1000L;
    private static final long DEFAULT_UPLOAD_TOKEN_TTL_MS = 10 * 60 * 1000L;
    private static final String DEFAULT_TRANSFER_JWT_SECRET = "transfer-download-session-default-secret-32bytes";

    @Value("${jwt.secret:" + DEFAULT_TRANSFER_JWT_SECRET + "}")
    private String jwtSecret = DEFAULT_TRANSFER_JWT_SECRET;

    @Value("${transfer.download-token-ttl-ms:" + DEFAULT_DOWNLOAD_TOKEN_TTL_MS + "}")
    private long downloadTokenTtlMs = DEFAULT_DOWNLOAD_TOKEN_TTL_MS;

    @Value("${transfer.upload-token-ttl-ms:" + DEFAULT_UPLOAD_TOKEN_TTL_MS + "}")
    private long uploadTokenTtlMs = DEFAULT_UPLOAD_TOKEN_TTL_MS;

    public String issueFileDownloadToken(String ownerUsername, String bucket, String objectName, String fileName) {
        return issueDownloadToken("FILE", ownerUsername, bucket, objectName, fileName, downloadTokenTtlMs);
    }

    public String issueDownloadToken(String scene,
                                     String ownerUsername,
                                     String bucket,
                                     String objectName,
                                     String fileName) {
        return issueDownloadToken(scene, ownerUsername, bucket, objectName, fileName, downloadTokenTtlMs);
    }

    public String issueDownloadToken(String scene,
                                     String ownerUsername,
                                     String bucket,
                                     String objectName,
                                     String fileName,
                                     long ttlMs) {
        String normalizedOwner = requireText(ownerUsername, "ownerUsername");
        String normalizedScene = requireText(scene, "scene");
        String normalizedBucket = requireText(bucket, "bucket");
        String normalizedObjectName = requireText(objectName, "objectName");
        String normalizedFileName = requireText(fileName, "fileName");

        long now = System.currentTimeMillis();
        long expireAt = now + ttlMs;
        Map<String, Object> claims = new HashMap<>();
        claims.put("ownerUsername", normalizedOwner);
        claims.put("scene", normalizedScene);
        claims.put("bucket", normalizedBucket);
        claims.put("objectName", normalizedObjectName);
        claims.put("fileName", normalizedFileName);
        claims.put("expireAt", expireAt);

        return Jwts.builder()
                .claims(claims)
                .subject(normalizedOwner)
                .issuedAt(new Date(now))
                .expiration(new Date(expireAt))
                .signWith(signingKey())
                .compact();
    }

    public DownloadTicket requireDownloadTicket(String token, String operatorUsername) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("下载 token 不能为空");
        }
        String normalizedOperator = requireText(operatorUsername, "operatorUsername");
        DownloadTicket ticket = requireDownloadTicket(token);
        if (!normalizedOperator.equals(ticket.getOwnerUsername())) {
            throw new SecurityException("下载会话与当前用户不匹配");
        }
        return ticket;
    }

    public DownloadTicket requireDownloadTicket(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("下载 token 不能为空");
        }
        Claims claims = parseClaims(token);

        String ownerUsername = claims.get("ownerUsername", String.class);
        String scene = claims.get("scene", String.class);
        String bucket = claims.get("bucket", String.class);
        String objectName = claims.get("objectName", String.class);
        String fileName = claims.get("fileName", String.class);
        Long expireAt = toLong(claims.get("expireAt"));
        if (expireAt == null || expireAt <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("下载会话不存在或已过期");
        }
        return new DownloadTicket(ownerUsername, scene, bucket, objectName, fileName, expireAt);
    }

    public String issueUploadToken(String ownerUsername,
                                   String bucket,
                                   String objectName,
                                   String fileName,
                                   String contentType,
                                   Long size) {
        return issueUploadToken(ownerUsername, bucket, objectName, fileName, contentType, size, null);
    }

    public String issueUploadToken(String ownerUsername,
                                   String bucket,
                                   String objectName,
                                   String fileName,
                                   String contentType,
                                   Long size,
                                   String uploadTraceId) {
        String normalizedOwner = requireText(ownerUsername, "ownerUsername");
        String normalizedBucket = requireText(bucket, "bucket");
        String normalizedObjectName = requireText(objectName, "objectName");
        String normalizedFileName = requireText(fileName, "fileName");
        long now = System.currentTimeMillis();
        long expireAt = now + uploadTokenTtlMs;
        Map<String, Object> claims = new HashMap<>();
        claims.put("ownerUsername", normalizedOwner);
        claims.put("bucket", normalizedBucket);
        claims.put("objectName", normalizedObjectName);
        claims.put("fileName", normalizedFileName);
        claims.put("contentType", nullableText(contentType));
        claims.put("size", size);
        claims.put("uploadTraceId", nullableText(uploadTraceId));
        claims.put("expireAt", expireAt);

        return Jwts.builder()
                .claims(claims)
                .subject(normalizedOwner)
                .issuedAt(new Date(now))
                .expiration(new Date(expireAt))
                .signWith(signingKey())
                .compact();
    }

    public UploadTicket requireUploadTicket(String token, String operatorUsername) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("上传 token 不能为空");
        }
        String normalizedOperator = requireText(operatorUsername, "operatorUsername");
        UploadTicket ticket = requireUploadTicket(token);
        if (!normalizedOperator.equals(ticket.getOwnerUsername())) {
            throw new SecurityException("上传会话与当前用户不匹配");
        }
        return ticket;
    }

    public UploadTicket requireUploadTicket(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalArgumentException("上传 token 不能为空");
        }
        Claims claims = parseClaims(token);
        String ownerUsername = claims.get("ownerUsername", String.class);
        String bucket = claims.get("bucket", String.class);
        String objectName = claims.get("objectName", String.class);
        String fileName = claims.get("fileName", String.class);
        String contentType = claims.get("contentType", String.class);
        String uploadTraceId = claims.get("uploadTraceId", String.class);
        Long size = toLong(claims.get("size"));
        Long expireAt = toLong(claims.get("expireAt"));
        if (expireAt == null || expireAt <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("上传会话不存在或已过期");
        }
        return new UploadTicket(ownerUsername, bucket, objectName, fileName, contentType, size, uploadTraceId, expireAt);
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException ex) {
            throw new IllegalArgumentException("下载会话不存在或已过期");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new IllegalArgumentException("下载 token 无效");
        }
    }

    private SecretKey signingKey() {
        byte[] raw = jwtSecret == null ? new byte[0] : jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (raw.length >= 32) {
            return Keys.hmacShaKeyFor(raw);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Keys.hmacShaKeyFor(digest.digest(raw));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JWT key 初始化失败");
        }
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private String nullableText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private Long toLong(Object value) {
        if (value instanceof Long v) {
            return v;
        }
        if (value instanceof Integer v) {
            return v.longValue();
        }
        if (value instanceof Number v) {
            return v.longValue();
        }
        if (value instanceof String v && !v.trim().isEmpty()) {
            try {
                return Long.parseLong(v.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static class DownloadTicket {
        private final String ownerUsername;
        private final String scene;
        private final String bucket;
        private final String objectName;
        private final String fileName;
        private final long expireAt;

        public DownloadTicket(String ownerUsername,
                              String scene,
                              String bucket,
                              String objectName,
                              String fileName,
                              long expireAt) {
            this.ownerUsername = ownerUsername;
            this.scene = scene;
            this.bucket = bucket;
            this.objectName = objectName;
            this.fileName = fileName;
            this.expireAt = expireAt;
        }

        public String getOwnerUsername() {
            return ownerUsername;
        }

        public String getScene() {
            return scene;
        }

        public String getBucket() {
            return bucket;
        }

        public String getObjectName() {
            return objectName;
        }

        public String getFileName() {
            return fileName;
        }

        public long getExpireAt() {
            return expireAt;
        }
    }

    public static class UploadTicket {
        private final String ownerUsername;
        private final String bucket;
        private final String objectName;
        private final String fileName;
        private final String contentType;
        private final Long size;
        private final String uploadTraceId;
        private final long expireAt;

        public UploadTicket(String ownerUsername,
                            String bucket,
                            String objectName,
                            String fileName,
                            String contentType,
                            Long size,
                            String uploadTraceId,
                            long expireAt) {
            this.ownerUsername = ownerUsername;
            this.bucket = bucket;
            this.objectName = objectName;
            this.fileName = fileName;
            this.contentType = contentType;
            this.size = size;
            this.uploadTraceId = uploadTraceId;
            this.expireAt = expireAt;
        }

        public String getOwnerUsername() {
            return ownerUsername;
        }

        public String getBucket() {
            return bucket;
        }

        public String getObjectName() {
            return objectName;
        }

        public String getFileName() {
            return fileName;
        }

        public String getContentType() {
            return contentType;
        }

        public Long getSize() {
            return size;
        }

        public String getUploadTraceId() {
            return uploadTraceId;
        }

        public long getExpireAt() {
            return expireAt;
        }
    }
}
