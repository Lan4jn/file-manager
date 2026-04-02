package com.lan4jn.file.service;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BucketServiceSecurityTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldDescribeOnlyAuthorizedBucketsWithoutEnumeratingAllBuckets() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(List.<Result<Item>>of());
        BucketService bucketService = new BucketService(minioClient);

        Method method = BucketService.class.getDeclaredMethod("listBuckets", List.class, String.class, String.class);
        method.setAccessible(true);

        List<?> buckets = (List<?>) method.invoke(bucketService, List.of("bucket-b", "bucket-a"), "name", "asc");

        assertEquals(List.of("bucket-a", "bucket-b"),
                buckets.stream().map(item -> ((com.lan4jn.file.model.BucketInfo) item).getName()).toList());

        ArgumentCaptor<ListObjectsArgs> argsCaptor = ArgumentCaptor.forClass(ListObjectsArgs.class);
        verify(minioClient, times(2)).listObjects(argsCaptor.capture());
        assertEquals(List.of("bucket-b", "bucket-a"),
                argsCaptor.getAllValues().stream().map(ListObjectsArgs::bucket).toList());
        verify(minioClient, never()).listBuckets();
    }
}
