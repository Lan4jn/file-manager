# Share Valid Filter Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `仅显示有效分享` filters and real pagination to “我的分享” and 收件箱.

**Architecture:** Add backend pagination/filtering first so the UI never loads all records. Extend existing `/api/share/list` and `/api/messages/inbox` with `page`, `size`, and `validOnly`; both return `PageResult`. Frontend keeps separate pagination/filter state for my shares and inbox, resets to page 1 when filters or page sizes change, and renders Element Plus pagination controls.

**Tech Stack:** Spring Boot 3, Spring Data JPA, JUnit 5, MockMvc, Vue 3, Element Plus, Node.js test runner.

---

### Task 1: Backend my-share pagination and valid filter

**Files:**
- Modify: `src/main/java/com/lan4jn/file/repository/ShareLinkRepository.java`
- Modify: `src/main/java/com/lan4jn/file/service/ShareLinkService.java`
- Modify: `src/main/java/com/lan4jn/file/service/ShareService.java`
- Modify: `src/main/java/com/lan4jn/file/controller/ShareController.java`
- Modify: `src/test/java/com/lan4jn/file/service/ShareLinkServiceTest.java`
- Modify: `src/test/java/com/lan4jn/file/controller/ShareControllerTest.java`

- [ ] **Step 1: Write failing service tests**

Add tests that prove `validOnly=true` only returns shares where `cancelled != true` and `expireTime > now`, and that pagination metadata is preserved in `PageResult<ShareLink>`.

- [ ] **Step 2: Run red backend tests**

Run `JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=ShareLinkServiceTest,ShareControllerTest test` and verify failure from missing paged API.

- [ ] **Step 3: Implement repository/service/controller changes**

Add paged repository methods, add `getShareLinksByUser(createBy, page, size, validOnly)`, expose `GET /api/share/list?page=&size=&validOnly=` returning `PageResult<ShareLink>`, and keep current list method as compatibility wrapper.

- [ ] **Step 4: Run green backend tests**

Run the same Maven command and verify exit code 0.

### Task 2: Backend inbox valid filter

**Files:**
- Modify: `src/main/java/com/lan4jn/file/repository/UserShareMessageRepository.java`
- Modify: `src/main/java/com/lan4jn/file/service/UserShareMessageService.java`
- Modify: `src/main/java/com/lan4jn/file/controller/MessageController.java`
- Modify: `src/test/java/com/lan4jn/file/service/UserShareMessageServiceTest.java`
- Modify: `src/test/java/com/lan4jn/file/controller/MessageControllerTest.java`

- [ ] **Step 1: Write failing inbox tests**

Add tests that `validOnly=false` keeps existing inbox behavior, and `validOnly=true` returns only share messages whose linked `ShareLink` is not cancelled and not expired.

- [ ] **Step 2: Run red inbox tests**

Run `JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=UserShareMessageServiceTest,MessageControllerTest test` and verify failure from missing `validOnly` support.

- [ ] **Step 3: Implement repository/service/controller filter**

Add a JPQL paged query joining `UserShareMessage.shareCode` to `ShareLink.shareCode`, filtering `messageType='SHARE'`, non-deleted messages, non-cancelled shares, and `expireTime > now`. Extend controller with `validOnly` parameter.

- [ ] **Step 4: Run green inbox tests**

Run the same Maven command and verify exit code 0.

### Task 3: Frontend pagination helper and UI wiring

**Files:**
- Create: `src/main/resources/static/js/share-pagination-helpers.js`
- Create: `src/test/js/share-pagination-helpers.test.js`
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/js/app.js`

- [ ] **Step 1: Write failing frontend helper tests**

Add tests for building request params `{ page, size, validOnly }` and resetting page to 1 when `validOnly` or page size changes.

- [ ] **Step 2: Run red frontend tests**

Run `node --test src/test/js/share-pagination-helpers.test.js` and verify failure because helper does not exist.

- [ ] **Step 3: Implement helper and UI state**

Add `sharePage/sharePageSize/shareTotal/shareValidOnly` and `inboxPage/inboxPageSize/inboxTotal/inboxValidOnly`. Update `loadShares()` and `loadInbox()` to pass pagination/filter params and read `PageResult.items/total`. Add `el-checkbox` and `el-pagination` controls for both lists.

- [ ] **Step 4: Run green frontend tests**

Run `node --test src/test/js/share-pagination-helpers.test.js` and `node --check src/main/resources/static/js/app.js`.

### Task 4: Version and final verification

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Bump version**

Update project version from `1.8.1-build124` to `1.8.1-build125`.

- [ ] **Step 2: Run targeted verification**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=ShareLinkServiceTest,ShareControllerTest,UserShareMessageServiceTest,MessageControllerTest test
node --test src/test/js/share-pagination-helpers.test.js src/test/js/share-management-helpers.test.js src/test/js/message-center-helpers.test.js
node --check src/main/resources/static/js/app.js
```

Expected: all commands exit with code 0.
