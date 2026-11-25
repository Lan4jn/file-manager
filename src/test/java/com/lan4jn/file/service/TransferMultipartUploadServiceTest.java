package com.lan4jn.file.service;

import com.lan4jn.file.config.MinioMultipartClient;
import com.lan4jn.file.dto.MultipartUploadCompleteRequest;
import com.lan4jn.file.dto.MultipartUploadInitRequest;
import com.lan4jn.file.dto.MultipartUploadInitResponse;
import com.lan4jn.file.dto.UploadResponse;
import com.lan4jn.file.entity.TransferUploadSession;
import com.lan4jn.file.rbac.BucketAction;
import com.lan4jn.file.rbac.PermissionCodes;
import com.lan4jn.file.repository.TransferUploadSessionRepository;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.ErrorResponse;
import io.minio.messages.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferMultipartUploadServiceTest {

    private static final long PART_SIZE = 10_485_760L;
    private static final long TOTAL_SIZE = PART_SIZE + 3;
    private static final String BUCKET = "bucket-a";
    private static final String OBJECT = "docs/renamed.bin";
    private static final String CONTENT_TYPE = "application/octet-stream";
    private static final String SESSION = "session-1";
    private static final String UPLOAD = "upload-1";

    @Mock private MinioMultipartClient minio;
    @Mock private TransferUploadSessionRepository repository;
    @Mock private FileService fileService;
    @Mock private PermissionAssertService permissions;
    @Captor private ArgumentCaptor<TransferUploadSession> savedSession;
    @Captor private ArgumentCaptor<List<Part>> submittedParts;

    private TransferMultipartUploadService service;

    @BeforeEach
    void setUp() {
        service = new TransferMultipartUploadService(minio, repository, fileService, permissions,
                PART_SIZE, 32, 86_400_000L);
    }

    @Test
    void createsSessionUsingPreparedTargetAndDefaultPartSize() throws Exception {
        currentUser("alice");
        MultipartUploadInitRequest request = initRequest(TOTAL_SIZE);
        when(minio.createMultipartUpload(BUCKET, OBJECT, CONTENT_TYPE)).thenReturn(UPLOAD);

        MultipartUploadInitResponse result = service.initSession(request);

        assertFalse(result.isResumed());
        assertEquals(2, result.getTotalParts());
        assertEquals(PART_SIZE, result.getPartSize());
        assertEquals(OBJECT, result.getObjectName());
        assertEquals(List.of(), result.getUploadedParts());
        verify(permissions).assertApiPermission(PermissionCodes.API_FILE_OPERATE);
        verify(permissions).assertBucketPermission(BUCKET, BucketAction.UPLOAD);
        verify(repository).save(savedSession.capture());
        TransferUploadSession persisted = savedSession.getValue();
        assertEquals(result.getSessionId(), persisted.getId());
        assertEquals(UPLOAD, persisted.getMinioUploadId());
        assertEquals("alice", persisted.getOwnerUsername());
        assertEquals("UPLOADING", persisted.getStatus());
        assertEquals(86_400_000L, persisted.getExpireTime() - persisted.getCreateTime());
    }

    @Test
    void increasesPartSizeWhenLastPartialPartWouldExceed9000Parts() throws Exception {
        currentUser("alice");
        MultipartUploadInitRequest request = initRequest(47_185_920_001L);
        request.setPartSize(1L);
        when(minio.createMultipartUpload(BUCKET, OBJECT, CONTENT_TYPE)).thenReturn(UPLOAD);

        MultipartUploadInitResponse result = service.initSession(request);

        assertEquals(5_242_881L, result.getPartSize());
        assertEquals(9000, result.getTotalParts());
    }

    @Test
    void resumesWithPartsAlreadyStoredInMinio() throws Exception {
        currentUser("alice");
        MultipartUploadInitRequest request = initRequest(TOTAL_SIZE);
        request.setFileFingerprint("fingerprint");
        resumableSession(session());
        when(minio.listParts(BUCKET, OBJECT, UPLOAD)).thenReturn(List.of(new Part(1, "\"etag-1\"")));

        MultipartUploadInitResponse result = service.initSession(request);

        assertTrue(result.isResumed());
        assertEquals(SESSION, result.getSessionId());
        assertEquals(1, result.getUploadedParts().size());
        assertEquals(1, result.getUploadedParts().get(0).getPartNumber());
        assertEquals("etag-1", result.getUploadedParts().get(0).getETag());
    }

    @Test
    void expiresMissingMinioUploadAndCreatesReplacement() throws Exception {
        currentUser("alice");
        MultipartUploadInitRequest request = initRequest(TOTAL_SIZE);
        request.setFileFingerprint("fingerprint");
        TransferUploadSession old = session();
        resumableSession(old);
        ErrorResponseException missing = mock(ErrorResponseException.class);
        ErrorResponse error = mock(ErrorResponse.class);
        when(missing.errorResponse()).thenReturn(error);
        when(error.code()).thenReturn("NoSuchUpload");
        when(minio.listParts(BUCKET, OBJECT, UPLOAD)).thenThrow(missing);
        when(minio.createMultipartUpload(BUCKET, OBJECT, CONTENT_TYPE)).thenReturn("new-upload");

        MultipartUploadInitResponse result = service.initSession(request);

        verify(repository).closeUploadingSession(eq(SESSION), eq("EXPIRED"), anyLong());
        assertFalse(result.isResumed());
        assertNotEquals(SESSION, result.getSessionId());
    }

    @Test
    void forwardsOriginalStreamAndUpdatesSessionActivity() throws Exception {
        ownedSession();
        var stream = new ByteArrayInputStream(new byte[] {1, 2, 3});
        when(minio.uploadPartStream(BUCKET, OBJECT, UPLOAD, 2, stream, 3L)).thenReturn("etag-2");

        assertEquals(Map.of("partNumber", 2, "eTag", "etag-2"), service.uploadPart(SESSION, 2, stream, 3L));

        assertEquals(3, stream.available());
        verify(minio).uploadPartStream(BUCKET, OBJECT, UPLOAD, 2, stream, 3L);
        verify(repository).touchUploadingSession(eq(SESSION), anyLong());
    }

    @Test
    void rejectsOutOfRangePartAndInvalidLengthBeforeMinio() {
        ownedSession();
        var stream = new ByteArrayInputStream(new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> service.uploadPart(SESSION, 3, stream, 3L));
        assertThrows(IllegalArgumentException.class, () -> service.uploadPart(SESSION, 1, stream, -1L));
        assertThrows(IllegalArgumentException.class, () -> service.uploadPart(SESSION, 1, stream, PART_SIZE + 1));
        verifyNoInteractions(minio);
    }

    @Test
    void rejectsLastPartLargerThanRemainingDeclaredSize() {
        ownedSession();
        var stream = new ByteArrayInputStream(new byte[4]);

        assertThrows(IllegalArgumentException.class, () -> service.uploadPart(SESSION, 2, stream, 4L));
        verifyNoInteractions(minio);
    }

    @Test
    void rejectsShortPartBeforePublishingAnIncompleteObject() {
        ownedSession();
        var stream = new ByteArrayInputStream(new byte[2]);

        assertThrows(IllegalArgumentException.class, () -> service.uploadPart(SESSION, 2, stream, 2L));
        verifyNoInteractions(minio);
    }

    @Test
    void deniesAccessToAnotherUsersSessionBeforeMinio() {
        currentUser("bob");
        when(repository.findById(SESSION)).thenReturn(Optional.of(session()));

        assertThrows(SecurityException.class, () -> service.abortSession(SESSION));
        verifyNoInteractions(minio);
    }

    @Test
    void completesSortedPartsAndReusesUploadResponse() throws Exception {
        TransferUploadSession session = ownedSession();
        MultipartUploadCompleteRequest request = new MultipartUploadCompleteRequest();
        request.setUploadTraceId("trace-1");
        request.setParts(List.of(part(2), part(1)));
        UploadResponse expected = new UploadResponse();
        when(fileService.completeDirectUpload(BUCKET, OBJECT, "renamed.bin", CONTENT_TYPE, TOTAL_SIZE, "trace-1"))
                .thenReturn(expected);

        assertSame(expected, service.completeSession(SESSION, request));

        verify(minio).completeMultipartUpload(eq(BUCKET), eq(OBJECT), eq(UPLOAD), submittedParts.capture());
        assertEquals(List.of(1, 2), submittedParts.getValue().stream().map(Part::partNumber).toList());
        assertEquals(List.of("etag-1", "etag-2"), submittedParts.getValue().stream().map(Part::etag).toList());
        assertEquals("COMPLETED", session.getStatus());
        verify(repository).save(session);
    }

    @Test
    void requiresAllPartsWhenCompletingFromMinioList() throws Exception {
        TransferUploadSession session = ownedSession();
        when(minio.listParts(BUCKET, OBJECT, UPLOAD)).thenReturn(List.of(new Part(1, "etag-1")));

        assertThrows(IllegalArgumentException.class, () -> service.completeSession(SESSION, new MultipartUploadCompleteRequest()));
        assertEquals("UPLOADING", session.getStatus());
        verifyNoInteractions(fileService);
    }

    @Test
    void abortsSessionEvenIfMinioAlreadyRemovedTheUpload() throws Exception {
        ownedSession();
        doThrow(new IOException("upload unavailable")).when(minio).abortMultipartUpload(BUCKET, OBJECT, UPLOAD);

        service.abortSession(SESSION);

        verify(repository).closeUploadingSession(eq(SESSION), eq("ABORTED"), anyLong());
    }

    @Test
    void abortsAndExpiresOldSessionsWithoutUserContext() throws Exception {
        TransferUploadSession expired = session();
        expired.setExpireTime(1L);
        when(repository.findByStatusAndExpireTimeLessThan(eq("UPLOADING"), anyLong())).thenReturn(List.of(expired));

        service.cleanupExpiredSessions();

        verify(minio).abortMultipartUpload(BUCKET, OBJECT, UPLOAD);
        verify(repository).closeUploadingSession(eq(SESSION), eq("EXPIRED"), anyLong());
        verifyNoInteractions(permissions);
    }

    @Test
    void leavesFailedCleanupEligibleForRetry() throws Exception {
        TransferUploadSession expired = session();
        expired.setExpireTime(1L);
        when(repository.findByStatusAndExpireTimeLessThan(eq("UPLOADING"), anyLong())).thenReturn(List.of(expired));
        doThrow(new IOException("temporary network failure")).when(minio).abortMultipartUpload(BUCKET, OBJECT, UPLOAD);

        service.cleanupExpiredSessions();

        verify(repository, never()).closeUploadingSession(eq(SESSION), eq("EXPIRED"), anyLong());
        verify(repository, never()).save(expired);
    }

    private void currentUser(String username) {
        when(permissions.currentSnapshot()).thenReturn(new PermissionSnapshot(1L, username, false,
                Set.of(), Set.of(), Set.of(), Map.of()));
    }

    private MultipartUploadInitRequest initRequest(long size) {
        var request = new MultipartUploadInitRequest();
        request.setBucket(BUCKET);
        request.setFolder("docs");
        request.setFileName("original.bin");
        request.setTargetFileName("renamed.bin");
        request.setContentType(CONTENT_TYPE);
        request.setSize(size);
        when(fileService.prepareDirectUpload("renamed.bin", size, "docs", BUCKET))
                .thenReturn(new FileDomainSupport.PreparedUpload(BUCKET, OBJECT, "renamed.bin", size));
        return request;
    }

    private void resumableSession(TransferUploadSession session) {
        when(repository.findFirstByOwnerUsernameAndFileFingerprintAndBucketNameAndObjectNameAndTotalSizeAndStatusAndExpireTimeGreaterThanOrderByUpdateTimeDesc(
                eq("alice"), eq("fingerprint"), eq(BUCKET), eq(OBJECT), eq(TOTAL_SIZE), eq("UPLOADING"), anyLong()))
                .thenReturn(Optional.of(session));
    }

    private TransferUploadSession ownedSession() {
        currentUser("alice");
        TransferUploadSession session = session();
        when(repository.findById(SESSION)).thenReturn(Optional.of(session));
        return session;
    }

    private TransferUploadSession session() {
        TransferUploadSession session = new TransferUploadSession();
        session.setId(SESSION);
        session.setOwnerUsername("alice");
        session.setBucketName(BUCKET);
        session.setObjectName(OBJECT);
        session.setFileName("renamed.bin");
        session.setContentType(CONTENT_TYPE);
        session.setTotalSize(TOTAL_SIZE);
        session.setPartSize(PART_SIZE);
        session.setTotalParts(2);
        session.setMinioUploadId(UPLOAD);
        session.setFileFingerprint("fingerprint");
        session.setStatus("UPLOADING");
        session.setUpdateTime(1L);
        session.setExpireTime(Long.MAX_VALUE);
        return session;
    }

    private MultipartUploadCompleteRequest.PartItem part(int number) {
        var part = new MultipartUploadCompleteRequest.PartItem();
        part.setPartNumber(number);
        part.setETag("etag-" + number);
        return part;
    }
}
