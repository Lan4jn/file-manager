package com.lan4jn.file.service;

import com.lan4jn.file.entity.BucketStatsSnapshot;
import com.lan4jn.file.model.BucketStatsRefreshStatus;
import com.lan4jn.file.repository.BucketStatsSnapshotRepository;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class BucketStatsRefreshService {

    private static final Logger log = LoggerFactory.getLogger(BucketStatsRefreshService.class);

    private final MinioClient minioClient;
    private final BucketStatsSnapshotRepository snapshotRepository;
    private final BucketService bucketService;
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    private final Object statusLock = new Object();
    private BucketStatsRefreshStatus currentStatus = new BucketStatsRefreshStatus();

    public BucketStatsRefreshService(MinioClient minioClient,
                                     BucketStatsSnapshotRepository snapshotRepository,
                                     BucketService bucketService) {
        this.minioClient = minioClient;
        this.snapshotRepository = snapshotRepository;
        this.bucketService = bucketService;
    }

    public boolean triggerRefresh(List<String> bucketNames) {
        return triggerRefreshAsync(bucketNames);
    }

    public boolean triggerAllBucketsRefreshAsync() {
        return triggerRefreshAsync(bucketService.listAllBucketNames());
    }

    public boolean triggerRefreshAsync(List<String> bucketNames) {
        List<String> targets = normalizeBucketNames(bucketNames);
        if (!refreshing.compareAndSet(false, true)) {
            synchronized (statusLock) {
                currentStatus.setMessage("Bucket stats refresh is already running");
            }
            return false;
        }

        initializeRunningStatus(targets);
        CompletableFuture.runAsync(() -> refreshBuckets(targets));
        return true;
    }

    void refreshBuckets(List<String> bucketNames) {
        List<String> targets = normalizeBucketNames(bucketNames);
        boolean hasFailures = false;
        try {
            initializeRunningStatus(targets);
            for (int i = 0; i < targets.size(); i++) {
                String bucketName = targets.get(i);
                updateProgress(i, targets.size(), bucketName);
                BucketStatsSnapshot snapshot = refreshBucket(bucketName);
                if ("FAILED".equals(snapshot.getStatus())) {
                    hasFailures = true;
                }
                updateProcessed(i + 1, targets.size());
            }
            finishStatus(hasFailures ? "FAILED" : "SUCCESS",
                    hasFailures ? "Bucket stats refresh completed with failures" : "Bucket stats refresh completed");
        } catch (Exception e) {
            log.error("Failed to refresh bucket stats", e);
            finishStatus("FAILED", "Bucket stats refresh failed: " + safeMessage(e));
        } finally {
            refreshing.set(false);
        }
    }

    BucketStatsSnapshot refreshBucket(String bucketName) {
        BucketStatsSnapshot snapshot = snapshotRepository.findById(bucketName)
                .orElseGet(BucketStatsSnapshot::new);
        snapshot.setBucketName(bucketName);

        LocalDateTime now = LocalDateTime.now();
        try {
            long objectCount = 0L;
            long totalSize = 0L;
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder().bucket(bucketName).recursive(true).build());
            for (Result<Item> result : objects) {
                Item item = result.get();
                if (!item.isDir()) {
                    objectCount++;
                    totalSize += item.size();
                }
            }

            snapshot.setObjectCount(objectCount);
            snapshot.setTotalSize(totalSize);
            snapshot.setStatus("SUCCESS");
            snapshot.setErrorMessage(null);
            snapshot.setLastCalculatedAt(now);
        } catch (Exception e) {
            snapshot.setStatus("FAILED");
            snapshot.setErrorMessage(safeMessage(e));
            log.warn("Failed to refresh bucket stats for {}", bucketName, e);
        }

        snapshot.setUpdatedAt(now);
        return snapshotRepository.save(snapshot);
    }

    public BucketStatsRefreshStatus getCurrentStatus() {
        synchronized (statusLock) {
            return new BucketStatsRefreshStatus(currentStatus);
        }
    }

    public BucketStatsRefreshStatus getStatus() {
        return getCurrentStatus();
    }

    @Scheduled(fixedDelayString = "${bucket.stats.refresh-interval-ms:3600000}")
    public void refreshAllBucketsOnSchedule() {
        triggerAllBucketsRefreshAsync();
    }

    private void initializeRunningStatus(List<String> bucketNames) {
        synchronized (statusLock) {
            currentStatus = new BucketStatsRefreshStatus();
            currentStatus.setStatus("RUNNING");
            currentStatus.setProcessedBuckets(0);
            currentStatus.setTotalBuckets(bucketNames.size());
            currentStatus.setCurrentBucket(null);
            currentStatus.setMessage(bucketNames.isEmpty()
                    ? "No buckets available for stats refresh"
                    : "Refreshing bucket stats");
        }
    }

    private void updateProgress(int processedBuckets, int totalBuckets, String currentBucket) {
        synchronized (statusLock) {
            currentStatus.setProcessedBuckets(processedBuckets);
            currentStatus.setTotalBuckets(totalBuckets);
            currentStatus.setCurrentBucket(currentBucket);
        }
    }

    private void updateProcessed(int processedBuckets, int totalBuckets) {
        synchronized (statusLock) {
            currentStatus.setProcessedBuckets(processedBuckets);
            currentStatus.setTotalBuckets(totalBuckets);
        }
    }

    private void finishStatus(String status, String message) {
        synchronized (statusLock) {
            currentStatus.setStatus(status);
            currentStatus.setCurrentBucket(null);
            currentStatus.setMessage(message);
            currentStatus.setLastFinishedAt(LocalDateTime.now());
        }
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

    private String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        Throwable cause = throwable.getCause();
        if (cause != null && (message == null || message.isBlank() || "Exception not handled".equals(message))) {
            String causeMessage = cause.getMessage();
            if (causeMessage != null && !causeMessage.isBlank()) {
                return causeMessage;
            }
            return cause.getClass().getSimpleName();
        }
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return message;
    }
}
