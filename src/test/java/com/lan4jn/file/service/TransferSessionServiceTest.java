package com.lan4jn.file.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
