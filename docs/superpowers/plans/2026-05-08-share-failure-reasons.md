# Share Failure Reasons Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show clear reasons when share content fails to load, including cancelled/expired shares and deleted or moved shared files.

**Architecture:** Keep current API paths and `Result` structure. Normalize storage/content read failures in `ShareAccessService`, and preserve existing share-state errors from `ShareLinkService`. Add a small front-end helper to format error messages for the “我的分享” table and use a safer fallback in the public share page.

**Tech Stack:** Spring Boot 3, JUnit 5, Mockito, Vue 3, Element Plus, Node.js test runner.

---

### Task 1: Backend share-content failure normalization

**Files:**
- Modify: `src/test/java/com/lan4jn/file/service/ShareAccessServiceTest.java`
- Modify: `src/main/java/com/lan4jn/file/service/ShareAccessService.java`

- [ ] **Step 1: Write failing backend regression test**

Add a test to `ShareAccessServiceTest` where a batch share contains `missing.txt`, `fileService.getFileInfo("missing.txt", "bucket-a")` throws `RuntimeException("获取文件信息失败: Object does not exist")`, and `listSharedItems("SHARE003", null, "")` throws `RuntimeException` with message `分享内容已被删除或移动`.

- [ ] **Step 2: Run backend test and verify red**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=ShareAccessServiceTest test
```

Expected: FAIL because the current code returns the raw `获取文件信息失败...` message.

- [ ] **Step 3: Implement minimal backend normalization**

Add a private wrapper in `ShareAccessService` for shared content reads:

- `loadSharedFileInfo(String objectName, String bucket)` catches known file-info failures and throws `RuntimeException("分享内容已被删除或移动")`.
- Replace direct `fileService.getFileInfo(...)` calls in shared-content listing and archive collection paths with this wrapper.
- Keep existing `resolveShare(...)` errors unchanged so cancelled/expired/password errors still surface as-is.

- [ ] **Step 4: Run backend test and verify green**

Run the same Maven command. Expected: PASS.

### Task 2: Frontend manage-list failure message

**Files:**
- Modify: `src/test/js/share-management-helpers.test.js`
- Modify: `src/main/resources/static/js/share-management-helpers.js`
- Modify: `src/main/resources/static/js/app.js`
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: Write failing frontend helper tests**

Add tests for `formatShareItemsErrorMessage(error)`:

- Axios-style error `{ response: { data: { message: '分享已取消' } } }` returns `加载失败：分享已取消`.
- Empty error returns `加载失败：分享内容加载失败，请稍后重试`.

- [ ] **Step 2: Run helper test and verify red**

Run:

```bash
node --test src/test/js/share-management-helpers.test.js
```

Expected: FAIL because the helper does not exist.

- [ ] **Step 3: Implement helper and wire UI**

In `share-management-helpers.js`, export `formatShareItemsErrorMessage(error)`. In `app.js`, set `share.shareItemsErrorMessage` on failure and clear it on success. In `index.html`, render `row.shareItemsErrorMessage || '加载失败：分享内容加载失败，请稍后重试'` instead of fixed `加载失败`.

- [ ] **Step 4: Run frontend checks and verify green**

Run:

```bash
node --test src/test/js/share-management-helpers.test.js
node --check src/main/resources/static/js/app.js
```

Expected: PASS.

### Task 3: Public share page fallback and version bump

**Files:**
- Modify: `src/main/resources/static/share.html`
- Modify: `pom.xml`

- [ ] **Step 1: Improve public share fallback**

Update `extractMessage(error)` in `share.html` so requests without a backend `message` fall back to `分享内容加载失败，请稍后重试` instead of a generic browser/network message.

- [ ] **Step 2: Bump build version**

Update `pom.xml` from `1.8.1-build123` to `1.8.1-build124`.

- [ ] **Step 3: Run targeted verification**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=ShareAccessServiceTest,ShareLinkServiceTest,ShareControllerTest test
node --test src/test/js/share-management-helpers.test.js
node --check src/main/resources/static/js/app.js
```

Expected: all commands exit with code 0.
