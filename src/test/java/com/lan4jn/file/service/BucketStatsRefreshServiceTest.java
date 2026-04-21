package com.lan4jn.file.service;

import com.lan4jn.file.entity.BucketStatsSnapshot;
import com.lan4jn.file.repository.BucketStatsSnapshotRepository;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BucketStatsRefreshServiceTest {

    @Test
    void refreshingBucketSuccessfullyPersistsSuccessStats() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        BucketStatsSnapshotRepository repository = mock(BucketStatsSnapshotRepository.class);
        BucketService bucketService = new BucketService(minioClient);

        when(minioClient.listObjects(any())).thenReturn(List.of(
                objectResult("alpha.txt", 128L),
                objectResult("beta.txt", 256L)
        ));
        when(repository.findById("bucket-a")).thenReturn(Optional.empty());

        Object service = createService(minioClient, repository, bucketService);
        invokeRefreshBucket(service, "bucket-a");

        ArgumentCaptor<BucketStatsSnapshot> captor = ArgumentCaptor.forClass(BucketStatsSnapshot.class);
        verify(repository).save(captor.capture());

        BucketStatsSnapshot saved = captor.getValue();
        assertEquals("bucket-a", saved.getBucketName());
        assertEquals("SUCCESS", saved.getStatus());
        assertEquals(2L, saved.getObjectCount());
        assertEquals(384L, saved.getTotalSize());
        assertNull(saved.getErrorMessage());
        assertNotNull(saved.getLastCalculatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void failingBucketRefreshPreservesPreviousStatsAndMarksFailure() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        BucketStatsSnapshotRepository repository = mock(BucketStatsSnapshotRepository.class);
        BucketService bucketService = new BucketService(minioClient);
        BucketStatsSnapshot previous = new BucketStatsSnapshot();
        previous.setBucketName("bucket-a");
        previous.setObjectCount(7L);
        previous.setTotalSize(777L);
        previous.setStatus("SUCCESS");
        previous.setLastCalculatedAt(LocalDateTime.of(2026, 4, 20, 8, 30));
        previous.setUpdatedAt(LocalDateTime.of(2026, 4, 20, 8, 30));

        when(minioClient.listObjects(any())).thenReturn(List.of(failingObjectResult("scan failed")));
        when(repository.findById("bucket-a")).thenReturn(Optional.of(previous));

        Object service = createService(minioClient, repository, bucketService);
        invokeRefreshBucket(service, "bucket-a");

        ArgumentCaptor<BucketStatsSnapshot> captor = ArgumentCaptor.forClass(BucketStatsSnapshot.class);
        verify(repository).save(captor.capture());

        BucketStatsSnapshot saved = captor.getValue();
        assertEquals("bucket-a", saved.getBucketName());
        assertEquals("FAILED", saved.getStatus());
        assertEquals(7L, saved.getObjectCount());
        assertEquals(777L, saved.getTotalSize());
        assertNotNull(saved.getErrorMessage());
        assertEquals(previous.getLastCalculatedAt(), saved.getLastCalculatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    private Object createService(MinioClient minioClient,
                                 BucketStatsSnapshotRepository repository,
                                 BucketService bucketService) throws Exception {
        try {
            Class<?> serviceClass = Class.forName("com.lan4jn.file.service.BucketStatsRefreshService");
            Constructor<?> constructor = serviceClass.getDeclaredConstructor(
                    MinioClient.class,
                    BucketStatsSnapshotRepository.class,
                    BucketService.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(minioClient, repository, bucketService);
        } catch (ClassNotFoundException e) {
            fail("BucketStatsRefreshService is missing");
            return null;
        } catch (NoSuchMethodException e) {
            fail("BucketStatsRefreshService constructor signature does not match task requirements");
            return null;
        }
    }

    private void invokeRefreshBucket(Object service, String bucketName) throws Exception {
        try {
            Method method = service.getClass().getDeclaredMethod("refreshBucket", String.class);
            method.setAccessible(true);
            method.invoke(service, bucketName);
        } catch (NoSuchMethodException e) {
            fail("BucketStatsRefreshService.refreshBucket(String) is missing");
        }
    }

    private Result<Item> objectResult(String objectName, long size) throws Exception {
        Item item = new Item() {
        };
        setField(item, "objectName", objectName);
        setField(item, "size", size);
        setField(item, "isDir", false);
        return new Result<>(item);
    }

    private Result<Item> failingObjectResult(String message) {
        return new Result<>(new RuntimeException(message));
    }

    private void setField(Item item, String fieldName, Object value) throws Exception {
        Field field = Item.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(item, value);
    }
}
