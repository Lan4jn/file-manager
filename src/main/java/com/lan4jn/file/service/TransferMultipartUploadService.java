package com.lan4jn.file.service;

import com.lan4jn.file.config.MinioMultipartClient;
import com.lan4jn.file.dto.MultipartUploadCompleteRequest;
import com.lan4jn.file.dto.MultipartUploadInitRequest;
import com.lan4jn.file.dto.MultipartUploadInitResponse;
import com.lan4jn.file.dto.MultipartUploadInitResponse.UploadedPart;
import com.lan4jn.file.dto.UploadResponse;
import com.lan4jn.file.entity.TransferUploadSession;
import com.lan4jn.file.rbac.BucketAction;
import com.lan4jn.file.rbac.PermissionCodes;
import com.lan4jn.file.repository.TransferUploadSessionRepository;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class TransferMultipartUploadService {
    private static final Logger log = LoggerFactory.getLogger(TransferMultipartUploadService.class);
    private static final String UPLOADING = "UPLOADING";
    private static final String COMPLETED = "COMPLETED";
    private static final String ABORTED = "ABORTED";
    private static final String EXPIRED = "EXPIRED";
    private static final long MIN_PART_SIZE = 5L * 1024 * 1024;
    private static final int MAX_PARTS = 9000;

    private final MinioMultipartClient minio;
    private final TransferUploadSessionRepository repository;
    private final FileService fileService;
    private final PermissionAssertService permissions;
    private final long defaultPartSizeBytes;
    private final long sessionTtlMs;
    private final Semaphore partPermits;

    public TransferMultipartUploadService(MinioMultipartClient minio,
                                          TransferUploadSessionRepository repository,
                                          FileService fileService,
                                          PermissionAssertService permissions,
                                          @Value("${transfer.multipart.default-part-size-bytes:10485760}") long defaultPartSizeBytes,
                                          @Value("${transfer.multipart.max-concurrent-parts:32}") int maxConcurrentParts,
                                          @Value("${transfer.multipart.session-ttl-ms:86400000}") long sessionTtlMs) {
        this.minio = minio;
        this.repository = repository;
        this.fileService = fileService;
        this.permissions = permissions;
        this.defaultPartSizeBytes = defaultPartSizeBytes;
        this.sessionTtlMs = sessionTtlMs;
        this.partPermits = new Semaphore(maxConcurrentParts);
    }

    public MultipartUploadInitResponse initSession(MultipartUploadInitRequest request) throws Exception {
        permissions.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        String bucket = optionalText(request.getBucket());
        if (bucket == null) {
            bucket = fileService.getDefaultBucket();
        }
        permissions.assertBucketPermission(bucket, BucketAction.UPLOAD);
        String owner = currentUsername();
        String targetFileName = optionalText(request.getTargetFileName());
        var target = fileService.prepareDirectUpload(targetFileName == null ? request.getFileName() : targetFileName,
                request.getSize(), optionalText(request.getFolder()), bucket);
        long totalSize = target.getSize();
        if (totalSize <= 0) {
            throw new IllegalArgumentException("分片上传的文件大小必须大于 0");
        }

        String fingerprint = optionalText(request.getFileFingerprint());
        if (fingerprint != null) {
            if (fingerprint.length() > 255) {
                throw new IllegalArgumentException("文件指纹不能超过 255 个字符");
            }
            var existing = repository.findFirstByOwnerUsernameAndFileFingerprintAndBucketNameAndObjectNameAndTotalSizeAndStatusAndExpireTimeGreaterThanOrderByUpdateTimeDesc(
                    owner, fingerprint, target.getBucket(), target.getObjectName(), totalSize, UPLOADING,
                    System.currentTimeMillis());
            if (existing.isPresent()) {
                TransferUploadSession session = existing.get();
                try {
                    List<UploadedPart> uploadedParts = listParts(session).stream()
                            .map(part -> new UploadedPart(part.partNumber(), part.etag()))
                            .toList();
                    return response(session, true, uploadedParts);
                } catch (ErrorResponseException e) {
                    if (!isMissingUpload(e)) {
                        throw e;
                    }
                    closeUploadingSession(session, EXPIRED);
                }
            }
        }

        long requestedPartSize = request.getPartSize() == null ? defaultPartSizeBytes : request.getPartSize();
        long partSize = Math.max(Math.max(MIN_PART_SIZE, requestedPartSize), ceilDiv(totalSize, MAX_PARTS));
        String contentType = optionalText(request.getContentType());
        String uploadId = minio.createMultipartUpload(target.getBucket(), target.getObjectName(), contentType);
        long now = System.currentTimeMillis();
        TransferUploadSession session = new TransferUploadSession();
        session.setId(UUID.randomUUID().toString());
        session.setOwnerUsername(owner);
        session.setBucketName(target.getBucket());
        session.setObjectName(target.getObjectName());
        session.setFileName(target.getFileName());
        session.setContentType(contentType);
        session.setTotalSize(totalSize);
        session.setPartSize(partSize);
        session.setTotalParts((int) ceilDiv(totalSize, partSize));
        session.setMinioUploadId(uploadId);
        session.setFileFingerprint(fingerprint == null ? "" : fingerprint);
        session.setStatus(UPLOADING);
        session.setCreateTime(now);
        session.setUpdateTime(now);
        session.setExpireTime(now + sessionTtlMs);
        try {
            repository.save(session);
        } catch (RuntimeException e) {
            abortMinioQuietly(session);
            throw e;
        }
        return response(session, false, List.of());
    }

    public Map<String, Object> uploadPart(String sessionId, int partNumber, InputStream bodyStream, long contentLength)
            throws Exception {
        TransferUploadSession session = requireOwnedSession(sessionId);
        requireActive(session);
        if (partNumber < 1 || partNumber > session.getTotalParts()) {
            throw new IllegalArgumentException("分片编号超出范围");
        }
        long partSize = session.getPartSize();
        long remaining = session.getTotalSize() - (partNumber - 1L) * partSize;
        long expectedLength = Math.min(partSize, remaining);
        if (contentLength <= 0 || contentLength != expectedLength) {
            throw new IllegalArgumentException("分片大小与会话不一致");
        }
        if (!partPermits.tryAcquire(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("分片上传繁忙，请稍后重试");
        }
        try {
            requireActive(session);
            String etag = minio.uploadPartStream(session.getBucketName(), session.getObjectName(),
                    session.getMinioUploadId(), partNumber, bodyStream, contentLength);
            repository.touchUploadingSession(sessionId, System.currentTimeMillis());
            return Map.of("partNumber", partNumber, "eTag", etag);
        } finally {
            partPermits.release();
        }
    }

    public UploadResponse completeSession(String sessionId, MultipartUploadCompleteRequest request) throws Exception {
        TransferUploadSession session = requireOwnedSession(sessionId);
        requireActive(session);
        var requestedParts = request == null ? null : request.getParts();
        List<Part> parts = (requestedParts == null || requestedParts.isEmpty()
                ? listParts(session).stream()
                : requestedParts.stream().map(item -> {
                    if (item == null || optionalText(item.getETag()) == null) {
                        throw new IllegalArgumentException("分片 ETag 不能为空");
                    }
                    return new Part(item.getPartNumber(), item.getETag().trim());
                }))
                .sorted(Comparator.comparingInt(Part::partNumber))
                .toList();
        if (parts.size() != session.getTotalParts()) {
            throw new IllegalArgumentException("分片数量与会话不一致");
        }
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).partNumber() != i + 1) {
                throw new IllegalArgumentException("分片编号重复或不连续");
            }
        }
        minio.completeMultipartUpload(session.getBucketName(), session.getObjectName(), session.getMinioUploadId(), parts);
        session.setStatus(COMPLETED);
        session.setUpdateTime(System.currentTimeMillis());
        repository.save(session);
        return fileService.completeDirectUpload(session.getBucketName(), session.getObjectName(), session.getFileName(),
                session.getContentType(), session.getTotalSize(), request == null ? null : optionalText(request.getUploadTraceId()));
    }

    public void abortSession(String sessionId) {
        TransferUploadSession session = requireOwnedSession(sessionId);
        if (UPLOADING.equals(session.getStatus())) {
            abortMinioQuietly(session);
            closeUploadingSession(session, ABORTED);
        }
    }

    @Scheduled(fixedDelayString = "${transfer.multipart.cleanup-interval-ms:1800000}")
    public void cleanupExpiredSessions() {
        for (TransferUploadSession session : repository.findByStatusAndExpireTimeLessThan(UPLOADING, System.currentTimeMillis())) {
            if (abortMinioQuietly(session)) {
                closeUploadingSession(session, EXPIRED);
            }
        }
    }

    private TransferUploadSession requireOwnedSession(String sessionId) {
        permissions.assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        String owner = currentUsername();
        TransferUploadSession session = repository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("分片上传会话不存在"));
        if (!owner.equals(session.getOwnerUsername())) {
            throw new SecurityException("无权访问该分片上传会话");
        }
        permissions.assertBucketPermission(session.getBucketName(), BucketAction.UPLOAD);
        return session;
    }

    private void requireActive(TransferUploadSession session) {
        if (!UPLOADING.equals(session.getStatus()) || session.getExpireTime() <= System.currentTimeMillis()) {
            throw new IllegalStateException("分片上传会话已结束或过期");
        }
    }

    private String currentUsername() {
        PermissionSnapshot snapshot = permissions.currentSnapshot();
        String username = snapshot == null ? null : optionalText(snapshot.username());
        if (username == null) {
            throw new SecurityException("未获取到当前用户");
        }
        return username;
    }

    private List<Part> listParts(TransferUploadSession session) throws Exception {
        return minio.listParts(session.getBucketName(), session.getObjectName(), session.getMinioUploadId());
    }

    private boolean abortMinioQuietly(TransferUploadSession session) {
        try {
            minio.abortMultipartUpload(session.getBucketName(), session.getObjectName(), session.getMinioUploadId());
            return true;
        } catch (Exception e) {
            if (e instanceof ErrorResponseException error && isMissingUpload(error)) {
                return true;
            }
            log.warn("Failed to abort MinIO multipart upload for session {}: {}", session.getId(), e.getMessage());
            return false;
        }
    }

    private static boolean isMissingUpload(ErrorResponseException error) {
        return error.errorResponse() != null && "NoSuchUpload".equals(error.errorResponse().code());
    }

    private void closeUploadingSession(TransferUploadSession session, String status) {
        repository.closeUploadingSession(session.getId(), status, System.currentTimeMillis());
    }

    private MultipartUploadInitResponse response(TransferUploadSession session, boolean resumed, List<UploadedPart> parts) {
        return new MultipartUploadInitResponse(session.getId(), session.getBucketName(), session.getObjectName(),
                session.getFileName(), session.getPartSize(), session.getTotalParts(), resumed, parts);
    }

    private static long ceilDiv(long value, long divisor) {
        return (value - 1) / divisor + 1;
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
