# Bucket Stats Snapshot Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split bucket listing from bucket size calculation so the bucket page reads fast from database snapshots while manual and scheduled refresh jobs compute and persist bucket stats asynchronously.

**Architecture:** Add a bucket stats snapshot entity/repository plus a refresh service that owns task state, MinIO scanning, persistence, and concurrency control. Change `GET /api/file/buckets` to merge bucket metadata with persisted stats, add refresh/status endpoints, and update the bucket page to trigger and observe refresh instead of waiting on real-time scans.

**Tech Stack:** Spring Boot 3.2, Spring Data JPA, SQLite, MinIO Java SDK, Vue 3, Element Plus, JUnit 5, node:test

---

## File Map

**Create**
- `src/main/java/com/lan4jn/file/entity/BucketStatsSnapshot.java`
- `src/main/java/com/lan4jn/file/repository/BucketStatsSnapshotRepository.java`
- `src/main/java/com/lan4jn/file/model/BucketStatsRefreshStatus.java`
- `src/main/java/com/lan4jn/file/service/BucketStatsRefreshService.java`
- `src/test/java/com/lan4jn/file/service/BucketStatsRefreshServiceTest.java`
- `src/test/js/bucket-stats-page.test.js`

**Modify**
- `src/main/java/com/lan4jn/file/FileManagerApplication.java`
- `src/main/java/com/lan4jn/file/model/BucketInfo.java`
- `src/main/java/com/lan4jn/file/service/BucketService.java`
- `src/main/java/com/lan4jn/file/service/FileService.java`
- `src/main/java/com/lan4jn/file/controller/FileController.java`
- `src/main/resources/static/index.html`
- `src/main/resources/static/js/app.js`
- `src/test/java/com/lan4jn/file/controller/FileControllerTest.java`
- `pom.xml`

## Task 1: Persist Bucket Stats Snapshots

**Files:**
- Create: `src/main/java/com/lan4jn/file/entity/BucketStatsSnapshot.java`
- Create: `src/main/java/com/lan4jn/file/repository/BucketStatsSnapshotRepository.java`
- Modify: `src/main/java/com/lan4jn/file/model/BucketInfo.java`
- Test: `src/test/java/com/lan4jn/file/controller/FileControllerTest.java`

- [ ] **Step 1: Write the failing controller test for snapshot-backed bucket fields**

Add a new test in `src/test/java/com/lan4jn/file/controller/FileControllerTest.java` asserting that bucket list responses expose stats fields without requiring real-time calculation:

```java
@Test
void shouldReturnBucketStatsSnapshotFields() throws Exception {
    BucketInfo bucketInfo = new BucketInfo();
    bucketInfo.setName("bucket-a");
    bucketInfo.setObjectCount(12L);
    bucketInfo.setTotalSize(345L);
    bucketInfo.setStatsStatus("SUCCESS");
    bucketInfo.setStatsUpdatedAt(LocalDateTime.of(2026, 4, 21, 10, 30));

    when(fileService.listBuckets("name", "asc")).thenReturn(List.of(bucketInfo));
    when(permissionAssertService.currentSnapshot()).thenReturn(new PermissionSnapshot(
            1L, "admin", true, Set.of("ADMIN"), Set.of(), Set.of(), Map.of()
    ));

    mockMvc.perform(get("/api/file/buckets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].statsStatus").value("SUCCESS"))
            .andExpect(jsonPath("$.data[0].objectCount").value(12))
            .andExpect(jsonPath("$.data[0].totalSize").value(345))
            .andExpect(jsonPath("$.data[0].statsUpdatedAt").exists());
}
```

- [ ] **Step 2: Run the targeted controller test to verify it fails**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=FileControllerTest test
```

Expected: FAIL because `BucketInfo` does not yet expose `statsStatus` / `statsUpdatedAt`.

- [ ] **Step 3: Add the bucket snapshot entity and repository**

Create `src/main/java/com/lan4jn/file/entity/BucketStatsSnapshot.java`:

```java
package com.lan4jn.file.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "bucket_stats_snapshot")
public class BucketStatsSnapshot {

    @Id
    @Column(name = "bucket_name", nullable = false, length = 128)
    private String bucketName;

    @Column(name = "object_count", nullable = false)
    private Long objectCount = 0L;

    @Column(name = "total_size", nullable = false)
    private Long totalSize = 0L;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "NOT_CALCULATED";

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "last_calculated_at")
    private LocalDateTime lastCalculatedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // getters/setters
}
```

Create `src/main/java/com/lan4jn/file/repository/BucketStatsSnapshotRepository.java`:

```java
package com.lan4jn.file.repository;

import com.lan4jn.file.entity.BucketStatsSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BucketStatsSnapshotRepository extends JpaRepository<BucketStatsSnapshot, String> {
    List<BucketStatsSnapshot> findByBucketNameIn(Collection<String> bucketNames);
    void deleteByBucketName(String bucketName);
    void deleteByBucketNameNotIn(Collection<String> bucketNames);
}
```

- [ ] **Step 4: Extend `BucketInfo` for snapshot metadata**

Modify `src/main/java/com/lan4jn/file/model/BucketInfo.java`:

```java
private String statsStatus;
private String statsErrorMessage;
private LocalDateTime statsUpdatedAt;

public String getStatsStatus() { return statsStatus; }
public void setStatsStatus(String statsStatus) { this.statsStatus = statsStatus; }

public String getStatsErrorMessage() { return statsErrorMessage; }
public void setStatsErrorMessage(String statsErrorMessage) { this.statsErrorMessage = statsErrorMessage; }

public LocalDateTime getStatsUpdatedAt() { return statsUpdatedAt; }
public void setStatsUpdatedAt(LocalDateTime statsUpdatedAt) { this.statsUpdatedAt = statsUpdatedAt; }
```

- [ ] **Step 5: Run the controller test to verify it passes**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=FileControllerTest test
```

Expected: PASS for the new bucket stats serialization assertion.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/lan4jn/file/entity/BucketStatsSnapshot.java \
        src/main/java/com/lan4jn/file/repository/BucketStatsSnapshotRepository.java \
        src/main/java/com/lan4jn/file/model/BucketInfo.java \
        src/test/java/com/lan4jn/file/controller/FileControllerTest.java
git commit -m "feat: add bucket stats snapshot model"
```

## Task 2: Build Bucket Stats Refresh Service and Scheduler

**Files:**
- Create: `src/main/java/com/lan4jn/file/model/BucketStatsRefreshStatus.java`
- Create: `src/main/java/com/lan4jn/file/service/BucketStatsRefreshService.java`
- Modify: `src/main/java/com/lan4jn/file/FileManagerApplication.java`
- Modify: `src/main/java/com/lan4jn/file/service/BucketService.java`
- Test: `src/test/java/com/lan4jn/file/service/BucketStatsRefreshServiceTest.java`

- [ ] **Step 1: Write the failing service tests**

Create `src/test/java/com/lan4jn/file/service/BucketStatsRefreshServiceTest.java` with two core tests:

```java
@Test
void shouldRefreshBucketSnapshotsAndPersistSuccessStatus() {
    // mocks: MinioClient, BucketStatsSnapshotRepository
    // buckets: bucket-a
    // objects: a.txt(5), b.txt(7)
    // expect snapshot SUCCESS with objectCount=2 totalSize=12
}

@Test
void shouldKeepPreviousValuesWhenSingleBucketRefreshFails() {
    // existing snapshot: objectCount=3 totalSize=99 SUCCESS
    // listObjects throws for bucket-a
    // expect objectCount=3 totalSize=99 status=FAILED errorMessage!=null
}
```

Use `ArgumentCaptor<BucketStatsSnapshot>` to assert saved values and `assertEquals("FAILED", snapshot.getStatus())`.

- [ ] **Step 2: Run the targeted service test to verify it fails**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=BucketStatsRefreshServiceTest test
```

Expected: FAIL because refresh service/status model do not exist.

- [ ] **Step 3: Add the refresh status model**

Create `src/main/java/com/lan4jn/file/model/BucketStatsRefreshStatus.java`:

```java
package com.lan4jn.file.model;

import java.time.LocalDateTime;

public class BucketStatsRefreshStatus {
    private String status = "IDLE";
    private int processedBuckets;
    private int totalBuckets;
    private String currentBucket;
    private String message;
    private LocalDateTime lastFinishedAt;
    // getters/setters
}
```

- [ ] **Step 4: Implement the refresh service**

Create `src/main/java/com/lan4jn/file/service/BucketStatsRefreshService.java` with injected `MinioClient`, `BucketStatsSnapshotRepository`, and `ConfigService`:

```java
@Service
public class BucketStatsRefreshService {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final BucketStatsRefreshStatus status = new BucketStatsRefreshStatus();

    public boolean triggerRefresh(List<String> bucketNames) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        CompletableFuture.runAsync(() -> refreshBuckets(bucketNames))
                .whenComplete((ignored, throwable) -> running.set(false));
        return true;
    }

    void refreshBuckets(List<String> bucketNames) {
        // set RUNNING
        // mark each bucket PENDING/RUNNING
        // compute counts/sizes via minioClient.listObjects(...)
        // save SUCCESS or FAILED snapshot per bucket
        // set final status and timestamp
    }

    public BucketStatsRefreshStatus getStatus() {
        return copyStatus();
    }
}
```

In failure handling, keep prior `objectCount` and `totalSize` if a snapshot already exists:

```java
snapshot.setStatus("FAILED");
snapshot.setErrorMessage(ex.getMessage());
snapshot.setLastCalculatedAt(LocalDateTime.now());
snapshot.setUpdatedAt(LocalDateTime.now());
```

- [ ] **Step 5: Add bucket-name enumeration and scheduling support**

Modify `src/main/java/com/lan4jn/file/service/BucketService.java` to expose fast bucket metadata listing without stats scanning:

```java
List<BucketInfo> listBucketsWithoutStats(String sortBy, String order) { ... }
List<BucketInfo> listBucketsWithoutStats(List<String> bucketNames, String sortBy, String order) { ... }
List<String> listAllBucketNames() { ... }
```

Remove `describeBucket()` from list flow and keep only compatibility + creation date population.

Enable scheduling in `src/main/java/com/lan4jn/file/FileManagerApplication.java`:

```java
@EnableScheduling
@SpringBootApplication
public class FileManagerApplication {
```

Add scheduled trigger in the refresh service:

```java
@Scheduled(fixedDelayString = "${bucket.stats.refresh-interval-ms:3600000}")
public void refreshAllBucketsOnSchedule() {
    triggerRefresh(bucketService.listAllBucketNames());
}
```

- [ ] **Step 6: Run the targeted service test to verify it passes**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=BucketStatsRefreshServiceTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/lan4jn/file/model/BucketStatsRefreshStatus.java \
        src/main/java/com/lan4jn/file/service/BucketStatsRefreshService.java \
        src/main/java/com/lan4jn/file/service/BucketService.java \
        src/main/java/com/lan4jn/file/FileManagerApplication.java \
        src/test/java/com/lan4jn/file/service/BucketStatsRefreshServiceTest.java
git commit -m "feat: add bucket stats refresh service"
```

## Task 3: Wire Snapshot Reads and Refresh Endpoints

**Files:**
- Modify: `src/main/java/com/lan4jn/file/service/FileService.java`
- Modify: `src/main/java/com/lan4jn/file/controller/FileController.java`
- Test: `src/test/java/com/lan4jn/file/controller/FileControllerTest.java`

- [ ] **Step 1: Write the failing controller tests for refresh endpoints**

Add tests in `src/test/java/com/lan4jn/file/controller/FileControllerTest.java`:

```java
@Test
void shouldTriggerBucketStatsRefresh() throws Exception {
    when(fileService.triggerBucketStatsRefresh()).thenReturn(true);

    mockMvc.perform(post("/api/file/buckets/stats/refresh"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.started").value(true));
}

@Test
void shouldReturnBucketStatsRefreshStatus() throws Exception {
    BucketStatsRefreshStatus status = new BucketStatsRefreshStatus();
    status.setStatus("RUNNING");
    status.setProcessedBuckets(1);
    status.setTotalBuckets(3);
    when(fileService.getBucketStatsRefreshStatus()).thenReturn(status);

    mockMvc.perform(get("/api/file/buckets/stats/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("RUNNING"))
            .andExpect(jsonPath("$.data.processedBuckets").value(1));
}
```

- [ ] **Step 2: Run the targeted controller test to verify it fails**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=FileControllerTest test
```

Expected: FAIL because `FileService` and controller endpoints do not yet expose these methods.

- [ ] **Step 3: Update `FileService` to merge snapshots and expose refresh operations**

Modify `src/main/java/com/lan4jn/file/service/FileService.java` constructor and methods to inject/use `BucketStatsRefreshService` and snapshot-backed bucket listing:

```java
private final BucketStatsRefreshService bucketStatsRefreshService;

public List<BucketInfo> listBuckets(String sortBy, String order) {
    return bucketService.listBuckets(sortBy, order);
}

public boolean triggerBucketStatsRefresh() {
    return bucketStatsRefreshService.triggerRefresh(bucketService.listAllBucketNames());
}

public BucketStatsRefreshStatus getBucketStatsRefreshStatus() {
    return bucketStatsRefreshService.getStatus();
}
```

Keep `createBucket` / `deleteBucket` delegating to `BucketService`, but ensure those methods initialize or delete snapshot rows.

- [ ] **Step 4: Add controller endpoints and logging**

Modify `src/main/java/com/lan4jn/file/controller/FileController.java`:

```java
@PostMapping("/buckets/stats/refresh")
public Result<Map<String, Object>> refreshBucketStats() {
    permissionAssertService.assertApiPermission(PermissionCodes.API_BUCKET_MANAGE);
    boolean started = fileService.triggerBucketStatsRefresh();
    return Result.success(Map.of(
            "started", started,
            "message", started ? "桶统计刷新任务已启动" : "桶统计刷新任务已在执行中"
    ));
}

@GetMapping("/buckets/stats/status")
public Result<BucketStatsRefreshStatus> getBucketStatsRefreshStatus() {
    permissionAssertService.assertApiPermission(PermissionCodes.API_FILE_BROWSE);
    return Result.success(fileService.getBucketStatsRefreshStatus());
}
```

Add `log.info(...)` for list and refresh requests to match other file endpoints.

- [ ] **Step 5: Run the targeted controller test to verify it passes**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=FileControllerTest test
```

Expected: PASS for bucket list serialization and refresh/status endpoints.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/lan4jn/file/service/FileService.java \
        src/main/java/com/lan4jn/file/controller/FileController.java \
        src/test/java/com/lan4jn/file/controller/FileControllerTest.java
git commit -m "feat: expose bucket stats refresh endpoints"
```

## Task 4: Update Bucket Page UI and Finish Verification

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/js/app.js`
- Create: `src/test/js/bucket-stats-page.test.js`
- Modify: `pom.xml`

- [ ] **Step 1: Write the failing front-end test for refresh-driven bucket UI**

Create `src/test/js/bucket-stats-page.test.js`:

```javascript
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

test('bucket page should expose refresh stats controls and status bindings', () => {
    const html = fs.readFileSync('src/main/resources/static/index.html', 'utf8');
    const appJs = fs.readFileSync('src/main/resources/static/js/app.js', 'utf8');

    assert.match(html, /刷新桶统计/);
    assert.match(appJs, /\/file\/buckets\/stats\/refresh/);
    assert.match(appJs, /\/file\/buckets\/stats\/status/);
    assert.match(appJs, /statsStatus/);
});
```

- [ ] **Step 2: Run the front-end test to verify it fails**

Run:

```bash
node --test src/test/js/bucket-stats-page.test.js
```

Expected: FAIL because refresh UI/status strings do not exist yet.

- [ ] **Step 3: Implement the bucket page refresh UX**

Modify `src/main/resources/static/index.html` bucket page header and columns:

```html
<el-button
    v-if="hasApiPermission('API_BUCKET_MANAGE')"
    :loading="bucketStatsRefreshing"
    @click="refreshBucketStats"
>
    刷新桶统计
</el-button>
```

Add an update time column:

```html
<el-table-column prop="statsUpdatedAt" label="统计时间" width="180"></el-table-column>
```

Render status-aware size/count cells:

```html
<template #default="{ row }">
    <span v-if="row.statsStatus === 'NOT_CALCULATED'">未统计</span>
    <span v-else-if="row.statsStatus === 'PENDING' || row.statsStatus === 'RUNNING'">统计中</span>
    <el-tooltip v-else-if="row.statsStatus === 'FAILED'" :content="row.statsErrorMessage || '统计失败'">
        <span>统计失败</span>
    </el-tooltip>
    <span v-else>{{ formatSize(row.totalSize) }}</span>
</template>
```

Modify `src/main/resources/static/js/app.js`:

```javascript
const bucketStatsRefreshing = ref(false);
let bucketStatsPollTimer = null;

const getBucketStatsStatus = async () => {
    const res = await api.get('/file/buckets/stats/status');
    return res.data || res;
};

const pollBucketStatsStatus = async () => {
    const res = await getBucketStatsStatus();
    const payload = res.data || res;
    if (payload.status === 'RUNNING') {
        bucketStatsPollTimer = setTimeout(pollBucketStatsStatus, 1000);
        return;
    }
    bucketStatsRefreshing.value = false;
    await loadBuckets();
};

const refreshBucketStats = async () => {
    const res = await api.post('/file/buckets/stats/refresh');
    const payload = res.data || res;
    bucketStatsRefreshing.value = true;
    if (payload.data?.started === false || payload.started === false) {
        await pollBucketStatsStatus();
        return;
    }
    await pollBucketStatsStatus();
};
```

- [ ] **Step 4: Bump version and run end-to-end verification**

Modify `pom.xml` build number once for this code change.

Run:

```bash
node --test src/test/js/bucket-stats-page.test.js
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=FileControllerTest,BucketStatsRefreshServiceTest test
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn test
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -DforceStdout help:evaluate -Dexpression=project.version
```

Expected:

- node test: PASS
- targeted Maven tests: PASS
- full Maven suite: PASS
- version command prints incremented build version

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/index.html \
        src/main/resources/static/js/app.js \
        src/test/js/bucket-stats-page.test.js \
        pom.xml
git commit -m "feat: refresh bucket stats asynchronously"
```

## Self-Review

- Spec coverage:
  - Snapshot persistence: Task 1
  - Async refresh + scheduler: Task 2
  - Refresh/status endpoints: Task 3
  - Bucket page UX and polling: Task 4
- Placeholder scan:
  - No TBD/TODO markers remain
  - Single-bucket failure behavior fixed to “keep previous stats, mark FAILED”
- Type consistency:
  - `BucketInfo` uses `statsStatus`, `statsErrorMessage`, `statsUpdatedAt`
  - Refresh API returns `BucketStatsRefreshStatus`
  - Controller and front-end paths consistently use `/api/file/buckets/stats/refresh` and `/api/file/buckets/stats/status`
