package com.lan4jn.file.service;

import com.lan4jn.file.entity.BucketStatsSnapshot;
import com.lan4jn.file.model.BucketInfo;
import com.lan4jn.file.repository.BucketStatsSnapshotRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
final class BucketService {

    private static final Logger log = LoggerFactory.getLogger(BucketService.class);

    private final MinioClient minioClient;
    private final BucketStatsSnapshotRepository snapshotRepository;

    BucketService(MinioClient minioClient) {
        this(minioClient, null);
    }

    @Autowired
    BucketService(MinioClient minioClient, BucketStatsSnapshotRepository snapshotRepository) {
        this.minioClient = minioClient;
        this.snapshotRepository = snapshotRepository;
    }

    List<BucketInfo> listBuckets(String sortBy, String order) {
        return listBucketsWithoutStats(sortBy, order);
    }

    List<BucketInfo> listBucketsWithoutStats(String sortBy, String order) {
        try {
            List<Bucket> buckets = minioClient.listBuckets();
            List<BucketInfo> result = new ArrayList<>();
            Map<String, BucketStatsSnapshot> snapshots = loadSnapshots(
                    buckets.stream().map(Bucket::name).collect(Collectors.toList()));

            for (Bucket bucket : buckets) {
                BucketInfo info = describeBucketMetadata(bucket.name(), snapshots.get(bucket.name()));
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
        return listBucketsWithoutStats(bucketNames, sortBy, order);
    }

    List<BucketInfo> listBucketsWithoutStats(List<String> bucketNames, String sortBy, String order) {
        try {
            List<Bucket> allBuckets = minioClient.listBuckets();
            List<BucketInfo> result = new ArrayList<>();
            List<String> targets = normalizeBucketNames(bucketNames);
            Map<String, BucketStatsSnapshot> snapshots = loadSnapshots(targets);
            for (String bucketName : targets) {
                BucketInfo info = describeBucketMetadata(bucketName, snapshots.get(bucketName));
                allBuckets.stream()
                        .filter(bucket -> bucketName.equals(bucket.name()))
                        .findFirst()
                        .ifPresent(bucket -> info.setCreationDate(LocalDateTime.ofInstant(
                                bucket.creationDate().toInstant(), ZoneId.systemDefault())));
                result.add(info);
            }
            return sortBuckets(result, sortBy, order);
        } catch (Exception e) {
            log.error("Failed to list authorized buckets", e);
            throw new RuntimeException("获取Bucket列表失败: " + e.getMessage(), e);
        }
    }

    List<String> listAllBucketNames() {
        try {
            return minioClient.listBuckets().stream()
                    .map(Bucket::name)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to list bucket names", e);
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
            initializeSnapshot(bucketName);
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
            deleteSnapshot(bucketName);
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

    private BucketInfo describeBucketMetadata(String bucketName, BucketStatsSnapshot snapshot) {
        BucketInfo info = new BucketInfo();
        info.setName(bucketName);
        info.setCompatible(isS3CompatibleBucketName(bucketName));
        info.setObjectCount(snapshot != null ? valueOf(snapshot.getObjectCount()) : 0L);
        info.setTotalSize(snapshot != null ? valueOf(snapshot.getTotalSize()) : 0L);
        if (snapshot != null) {
            info.setStatsStatus(snapshot.getStatus());
            info.setStatsErrorMessage(snapshot.getErrorMessage());
            info.setStatsUpdatedAt(snapshot.getUpdatedAt());
        }
        return info;
    }

    private List<String> normalizeBucketNames(List<String> bucketNames) {
        if (bucketNames == null || bucketNames.isEmpty()) {
            return List.of();
        }
        List<String> targets = new ArrayList<>();
        for (String bucketName : new LinkedHashSet<>(bucketNames)) {
            if (bucketName != null && !bucketName.isBlank()) {
                targets.add(bucketName);
            }
        }
        return targets;
    }

    private Map<String, BucketStatsSnapshot> loadSnapshots(List<String> bucketNames) {
        if (snapshotRepository == null || bucketNames == null || bucketNames.isEmpty()) {
            return Map.of();
        }
        return snapshotRepository.findByBucketNameIn(bucketNames).stream()
                .collect(Collectors.toMap(BucketStatsSnapshot::getBucketName, Function.identity()));
    }

    private void initializeSnapshot(String bucketName) {
        if (snapshotRepository == null) {
            return;
        }
        BucketStatsSnapshot snapshot = new BucketStatsSnapshot();
        snapshot.setBucketName(bucketName);
        snapshot.setObjectCount(0L);
        snapshot.setTotalSize(0L);
        snapshot.setStatus("NOT_CALCULATED");
        snapshot.setUpdatedAt(LocalDateTime.now());
        snapshotRepository.save(snapshot);
    }

    private void deleteSnapshot(String bucketName) {
        if (snapshotRepository == null) {
            return;
        }
        snapshotRepository.deleteByBucketName(bucketName);
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
