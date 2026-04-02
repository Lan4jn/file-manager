package com.lan4jn.file.service;

import com.lan4jn.file.model.BucketInfo;
import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveBucketArgs;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

final class BucketService {

    private static final Logger log = LoggerFactory.getLogger(BucketService.class);

    private final MinioClient minioClient;

    BucketService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    List<BucketInfo> listBuckets(String sortBy, String order) {
        try {
            List<Bucket> buckets = minioClient.listBuckets();
            List<BucketInfo> result = new ArrayList<>();

            for (Bucket bucket : buckets) {
                BucketInfo info = describeBucket(bucket.name());
                info.setCreationDate(LocalDateTime.ofInstant(
                        bucket.creationDate().toInstant(), ZoneId.systemDefault()));
                result.add(info);
            }

            return sortBuckets(result, sortBy, order);
        } catch (Exception e) {
            log.error("Failed to list buckets", e);
            throw new RuntimeException("获取Bucket列表失败: " + e.getMessage(), e);
        }
    }

    List<BucketInfo> listBuckets(List<String> bucketNames, String sortBy, String order) {
        try {
            List<BucketInfo> result = new ArrayList<>();
            for (String bucketName : new java.util.LinkedHashSet<>(bucketNames)) {
                if (bucketName == null || bucketName.isBlank()) {
                    continue;
                }
                result.add(describeBucket(bucketName));
            }
            return sortBuckets(result, sortBy, order);
        } catch (Exception e) {
            log.error("Failed to list authorized buckets", e);
            throw new RuntimeException("获取Bucket列表失败: " + e.getMessage(), e);
        }
    }

    BucketInfo createBucket(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (exists) {
                throw new RuntimeException("Bucket已存在: " + bucketName);
            }
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());

            BucketInfo info = new BucketInfo();
            info.setName(bucketName);
            info.setCompatible(true);
            info.setCreationDate(LocalDateTime.now());
            info.setObjectCount(0L);
            info.setTotalSize(0L);
            log.info("Bucket created: {}", bucketName);
            return info;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create bucket: {}", bucketName, e);
            throw new RuntimeException("创建Bucket失败: " + e.getMessage(), e);
        }
    }

    void deleteBucket(String bucketName) {
        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).recursive(true).build());
            for (Result<Item> result : objects) {
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(result.get().objectName())
                        .build());
            }
            minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
            log.info("Bucket deleted: {}", bucketName);
        } catch (Exception e) {
            log.error("Failed to delete bucket: {}", bucketName, e);
            throw new RuntimeException("删除Bucket失败: " + e.getMessage(), e);
        }
    }

    private long valueOf(Long value) {
        return value != null ? value : 0L;
    }

    private LocalDateTime valueOf(LocalDateTime value) {
        return value != null ? value : LocalDateTime.MIN;
    }

    private String valueOf(String value) {
        return value != null ? value : "";
    }

    private boolean isS3CompatibleBucketName(String bucketName) {
        if (bucketName == null || bucketName.length() < 3 || bucketName.length() > 63) {
            return false;
        }
        if (!bucketName.matches("^[a-z0-9][a-z0-9.-]*[a-z0-9]$")) {
            return false;
        }
        if (bucketName.contains("..")) {
            return false;
        }
        return !bucketName.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$");
    }

    private BucketInfo describeBucket(String bucketName) {
        BucketInfo info = new BucketInfo();
        info.setName(bucketName);
        info.setCompatible(isS3CompatibleBucketName(bucketName));
        info.setObjectCount(0L);
        info.setTotalSize(0L);

        try {
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).recursive(true).build());
            long objectCount = 0;
            long totalSize = 0;
            for (Result<Item> obj : objects) {
                Item item = obj.get();
                if (!item.isDir()) {
                    objectCount++;
                    totalSize += item.size();
                }
            }
            info.setObjectCount(objectCount);
            info.setTotalSize(totalSize);
        } catch (Exception e) {
            log.warn("Failed to load stats for bucket: {}", bucketName, e);
        }
        return info;
    }

    private List<BucketInfo> sortBuckets(List<BucketInfo> buckets, String sortBy, String order) {
        buckets.sort((b1, b2) -> {
            int resultVal;
            switch (sortBy) {
                case "size":
                    resultVal = Long.compare(valueOf(b1.getTotalSize()), valueOf(b2.getTotalSize()));
                    break;
                case "time":
                    resultVal = valueOf(b1.getCreationDate()).compareTo(valueOf(b2.getCreationDate()));
                    break;
                default:
                    resultVal = valueOf(b1.getName()).compareTo(valueOf(b2.getName()));
            }
            return "desc".equalsIgnoreCase(order) ? -resultVal : resultVal;
        });
        return buckets;
    }
}
