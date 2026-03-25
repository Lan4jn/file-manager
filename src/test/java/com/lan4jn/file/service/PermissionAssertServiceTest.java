package com.lan4jn.file.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionAssertServiceTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAllowWildcardBucketPermission() {
        PermissionResolverService resolverService = mock(PermissionResolverService.class);
        PermissionAssertService assertService = new PermissionAssertService(resolverService);

        Map<String, java.util.Set<String>> bucketPermissions = new LinkedHashMap<>();
        bucketPermissions.put("*", new LinkedHashSet<>(java.util.List.of("*")));

        when(resolverService.resolveByUsername("alice")).thenReturn(new PermissionSnapshot(
                1L,
                "alice",
                false,
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                bucketPermissions
        ));

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", null));

        assertDoesNotThrow(() -> assertService.assertBucketPermission("future-bucket", "DOWNLOAD"));
    }

    @Test
    void shouldPreferSpecificBucketPermissionOverGlobalBucketPermission() {
        PermissionResolverService resolverService = mock(PermissionResolverService.class);
        PermissionAssertService assertService = new PermissionAssertService(resolverService);

        Map<String, java.util.Set<String>> bucketPermissions = new LinkedHashMap<>();
        bucketPermissions.put("*", new LinkedHashSet<>(java.util.List.of("DOWNLOAD")));
        bucketPermissions.put("bucket-a", new LinkedHashSet<>(java.util.List.of("READ")));

        when(resolverService.resolveByUsername("alice")).thenReturn(new PermissionSnapshot(
                1L,
                "alice",
                false,
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                bucketPermissions
        ));

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", null));

        assertThrows(SecurityException.class, () -> assertService.assertBucketPermission("bucket-a", "DOWNLOAD"));
        assertDoesNotThrow(() -> assertService.assertBucketPermission("bucket-a", "READ"));
        assertDoesNotThrow(() -> assertService.assertBucketPermission("bucket-b", "DOWNLOAD"));
    }
}
