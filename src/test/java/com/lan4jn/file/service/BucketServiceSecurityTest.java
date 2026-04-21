package com.lan4jn.file.service;

import io.minio.MinioClient;
import io.minio.messages.Bucket;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BucketServiceSecurityTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldDescribeOnlyAuthorizedBucketsWithoutEnumeratingObjects() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        Bucket bucketA = mock(Bucket.class);
        Bucket bucketB = mock(Bucket.class);
        when(bucketA.name()).thenReturn("bucket-a");
        when(bucketB.name()).thenReturn("bucket-b");
        when(bucketA.creationDate()).thenReturn(ZonedDateTime.parse("2026-04-21T10:15:30Z"));
        when(bucketB.creationDate()).thenReturn(ZonedDateTime.parse("2026-04-22T08:00:00Z"));
        when(minioClient.listBuckets()).thenReturn(List.of(bucketA, bucketB));
        BucketService bucketService = new BucketService(minioClient);

        Method method = BucketService.class.getDeclaredMethod("listBuckets", List.class, String.class, String.class);
        method.setAccessible(true);

        List<?> buckets = (List<?>) method.invoke(bucketService, List.of("bucket-b", "bucket-a"), "name", "asc");

        assertEquals(List.of("bucket-a", "bucket-b"),
                buckets.stream().map(item -> ((com.lan4jn.file.model.BucketInfo) item).getName()).toList());

        verify(minioClient).listBuckets();
        verify(minioClient, never()).listObjects(any());
    }
}
