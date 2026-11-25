package com.lan4jn.file.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferSessionServiceTest {

    @Test
    void shouldIssueAndResolveFileDownloadToken() {
        TransferSessionService service = new TransferSessionService();

        String token = service.issueFileDownloadToken("alice", "bucket-a", "docs/readme.txt", "readme.txt");
        TransferSessionService.DownloadTicket ticket = service.requireDownloadTicket(token, "alice");

        assertEquals("FILE", ticket.getScene());
        assertEquals("alice", ticket.getOwnerUsername());
        assertEquals("bucket-a", ticket.getBucket());
        assertEquals("docs/readme.txt", ticket.getObjectName());
        assertEquals("readme.txt", ticket.getFileName());
    }

    @Test
    void shouldIssueDownloadTokenWithSixtyMinuteDefaultTtl() {
        TransferSessionService service = new TransferSessionService();
        long beforeIssue = System.currentTimeMillis();
        String token = service.issueFileDownloadToken("alice", "bucket-a", "docs/readme.txt", "readme.txt");
        long afterIssue = System.currentTimeMillis();

        long expiresAt = service.requireDownloadTicket(token).getExpireAt();

        assertTrue(expiresAt >= beforeIssue + 3_600_000L && expiresAt <= afterIssue + 3_600_000L,
                "The issued download token should expire after sixty minutes");
    }

    @Test
    void shouldRejectUnknownToken() {
        TransferSessionService service = new TransferSessionService();

        assertThrows(IllegalArgumentException.class, () -> service.requireDownloadTicket("missing-token", "alice"));
    }

    @Test
    void shouldRejectDownloadTokenFromDifferentUser() {
        TransferSessionService service = new TransferSessionService();

        String token = service.issueFileDownloadToken("alice", "bucket-a", "docs/readme.txt", "readme.txt");

        assertThrows(SecurityException.class, () -> service.requireDownloadTicket(token, "bob"));
    }

    @Test
    void shouldResolveDownloadTokenWithoutOperatorContext() {
        TransferSessionService service = new TransferSessionService();

        String token = service.issueFileDownloadToken("alice", "bucket-a", "docs/readme.txt", "readme.txt");
        TransferSessionService.DownloadTicket ticket = service.requireDownloadTicket(token);

        assertEquals("alice", ticket.getOwnerUsername());
        assertEquals("docs/readme.txt", ticket.getObjectName());
    }

    @Test
    void shouldRejectExpiredToken() {
        TransferSessionService service = new TransferSessionService();

        String token = service.issueDownloadToken(
                "FILE", "alice", "bucket-a", "docs/readme.txt", "readme.txt", -1L);

        assertThrows(IllegalArgumentException.class, () -> service.requireDownloadTicket(token, "alice"));
    }

    @Test
    void shouldResolveTokenAcrossServiceInstances() {
        TransferSessionService issuer = new TransferSessionService();
        String token = issuer.issueFileDownloadToken("alice", "bucket-a", "docs/readme.txt", "readme.txt");

        TransferSessionService verifier = new TransferSessionService();
        TransferSessionService.DownloadTicket ticket = verifier.requireDownloadTicket(token, "alice");

        assertEquals("docs/readme.txt", ticket.getObjectName());
    }
}
