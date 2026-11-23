# v1.8.1 Share Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver v1.8.1 share-management improvements: inline share content, path hover tips, recipient display, and separate delete-record/cancel-share actions.

**Architecture:** Keep the existing `ShareLink` model as the source of share lifecycle state, adding lightweight flags for creator-side hiding and cancellation. Reuse existing `/api/share/items` for content details and existing `UserShareMessage` records for recipient display so the feature stays incremental and compatible with current sharing flows.

**Tech Stack:** Spring Boot 3, Spring MVC, Spring Data JPA, JUnit 5, MockMvc, Vue 3, Element Plus, Node syntax checks.

---

### Task 1: Share lifecycle state and backend behavior

**Files:**
- Modify: `src/main/java/com/lan4jn/file/model/ShareLink.java`
- Modify: `src/main/java/com/lan4jn/file/repository/ShareLinkRepository.java`
- Modify: `src/main/java/com/lan4jn/file/service/ShareLinkService.java`
- Modify: `src/main/java/com/lan4jn/file/service/ShareService.java`
- Test: `src/test/java/com/lan4jn/file/service/ShareLinkServiceTest.java`

- [ ] **Step 1: Write failing tests for delete-record and cancel-share semantics**

Add these tests to `src/test/java/com/lan4jn/file/service/ShareLinkServiceTest.java`:

```java
@Test
void shouldHideShareRecordWithoutCancellingShare() {
    FileService fileService = mock(FileService.class);
    ShareLinkRepository shareLinkRepository = mock(ShareLinkRepository.class);
    ShareFileRecordRepository shareFileRecordRepository = mock(ShareFileRecordRepository.class);

    ShareLink shareLink = new ShareLink();
    shareLink.setShareCode("CODE1234");
    shareLink.setCreateBy("creator");

    when(shareLinkRepository.findByShareCode("CODE1234")).thenReturn(Optional.of(shareLink));
    when(shareLinkRepository.save(any(ShareLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ShareLinkService shareLinkService = new ShareLinkService(
            fileService, shareLinkRepository, shareFileRecordRepository, new ShareSupport(), 24);

    shareLinkService.deleteShareLink("CODE1234", "creator");

    assertEquals(Boolean.TRUE, shareLink.getHiddenByCreator());
    assertEquals(null, shareLink.getCancelled());
    verify(shareLinkRepository).save(shareLink);
}

@Test
void shouldCancelShareLink() {
    FileService fileService = mock(FileService.class);
    ShareLinkRepository shareLinkRepository = mock(ShareLinkRepository.class);
    ShareFileRecordRepository shareFileRecordRepository = mock(ShareFileRecordRepository.class);

    ShareLink shareLink = new ShareLink();
    shareLink.setShareCode("CODE1234");
    shareLink.setCreateBy("creator");

    when(shareLinkRepository.findByShareCode("CODE1234")).thenReturn(Optional.of(shareLink));
    when(shareLinkRepository.save(any(ShareLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

    ShareLinkService shareLinkService = new ShareLinkService(
            fileService, shareLinkRepository, shareFileRecordRepository, new ShareSupport(), 24);

    shareLinkService.cancelShareLink("CODE1234", "creator");

    assertEquals(Boolean.TRUE, shareLink.getCancelled());
    verify(shareLinkRepository).save(shareLink);
}

@Test
void shouldRejectCancelledShare() {
    FileService fileService = mock(FileService.class);
    ShareLinkRepository shareLinkRepository = mock(ShareLinkRepository.class);
    ShareFileRecordRepository shareFileRecordRepository = mock(ShareFileRecordRepository.class);

    ShareLink shareLink = new ShareLink();
    shareLink.setShareCode("CODE1234");
    shareLink.setExpireTime(LocalDateTime.now().plusHours(1));
    shareLink.setCancelled(true);

    when(shareLinkRepository.findByShareCode("CODE1234")).thenReturn(Optional.of(shareLink));

    ShareLinkService shareLinkService = new ShareLinkService(
            fileService, shareLinkRepository, shareFileRecordRepository, new ShareSupport(), 24);

    RuntimeException error = assertThrows(RuntimeException.class,
            () -> shareLinkService.resolveShare("CODE1234", null, false));

    assertEquals("分享已取消", error.getMessage());
}
```

Add imports if missing:

```java
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
```

- [ ] **Step 2: Run service tests to verify failure**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=ShareLinkServiceTest test
```

Expected: FAIL because `ShareLink` does not expose `hiddenByCreator` / `cancelled`, and `ShareLinkService` does not yet provide `cancelShareLink`.

- [ ] **Step 3: Add ShareLink lifecycle fields**

Modify `src/main/java/com/lan4jn/file/model/ShareLink.java`:

```java
private Boolean cancelled;
private Boolean hiddenByCreator;

public Boolean getCancelled() {
    return cancelled;
}

public void setCancelled(Boolean cancelled) {
    this.cancelled = cancelled;
}

public Boolean getHiddenByCreator() {
    return hiddenByCreator;
}

public void setHiddenByCreator(Boolean hiddenByCreator) {
    this.hiddenByCreator = hiddenByCreator;
}
```

- [ ] **Step 4: Filter hidden records in repository**

Modify `src/main/java/com/lan4jn/file/repository/ShareLinkRepository.java`:

```java
import org.springframework.data.jpa.repository.Query;

@Query("select s from ShareLink s where s.createBy = ?1 and (s.hiddenByCreator is null or s.hiddenByCreator = false) order by s.createTime desc")
List<ShareLink> findVisibleByCreateByOrderByCreateTimeDesc(String createBy);
```

Keep the existing `findByCreateByOrderByCreateTimeDesc` method if other code still uses it.

- [ ] **Step 5: Implement lifecycle behavior in service**

Modify `src/main/java/com/lan4jn/file/service/ShareLinkService.java`:

```java
void deleteShareLink(String shareCode, String createBy) {
    ShareLink shareLink = shareLinkRepository.findByShareCode(shareCode)
            .orElseThrow(() -> new RuntimeException("分享链接不存在"));
    if (!Objects.equals(shareLink.getCreateBy(), createBy)) {
        throw new RuntimeException("无权删除该分享");
    }
    shareLink.setHiddenByCreator(true);
    shareLinkRepository.save(shareLink);
    log.info("Share record hidden: {}", shareCode);
}

void cancelShareLink(String shareCode, String createBy) {
    ShareLink shareLink = shareLinkRepository.findByShareCode(shareCode)
            .orElseThrow(() -> new RuntimeException("分享链接不存在"));
    if (!Objects.equals(shareLink.getCreateBy(), createBy)) {
        throw new RuntimeException("无权取消该分享");
    }
    shareLink.setCancelled(true);
    shareLinkRepository.save(shareLink);
    log.info("Share link cancelled: {}", shareCode);
}

List<ShareLink> getShareLinksByUser(String createBy) {
    return shareLinkRepository.findVisibleByCreateByOrderByCreateTimeDesc(createBy);
}

private ShareLink findValidShare(String shareCode) {
    ShareLink shareLink = shareLinkRepository.findByShareCode(shareCode)
            .orElseThrow(() -> new RuntimeException("分享链接不存在"));
    if (Boolean.TRUE.equals(shareLink.getCancelled())) {
        throw new RuntimeException("分享已取消");
    }
    if (LocalDateTime.now().isAfter(shareLink.getExpireTime())) {
        throw new RuntimeException("分享链接已过期");
    }
    return shareLink;
}

boolean validateShareCode(String shareCode) {
    Optional<ShareLink> shareLink = shareLinkRepository.findByShareCode(shareCode);
    return shareLink.isPresent()
            && !Boolean.TRUE.equals(shareLink.get().getCancelled())
            && !LocalDateTime.now().isAfter(shareLink.get().getExpireTime());
}
```

Set defaults during share creation in both single and batch paths:

```java
shareLink.setCancelled(false);
shareLink.setHiddenByCreator(false);
```

- [ ] **Step 6: Expose cancellation through ShareService**

Modify `src/main/java/com/lan4jn/file/service/ShareService.java`:

```java
public void cancelShareLink(String shareCode, String createBy) {
    shareLinkService.cancelShareLink(shareCode, createBy);
}
```

- [ ] **Step 7: Run service tests to verify pass**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=ShareLinkServiceTest test
```

Expected: PASS.

### Task 2: Recipient display data for user shares

**Files:**
- Modify: `src/main/java/com/lan4jn/file/model/ShareLink.java`
- Modify: `src/main/java/com/lan4jn/file/repository/UserShareMessageRepository.java`
- Modify: `src/main/java/com/lan4jn/file/service/ShareLinkService.java`
- Test: `src/test/java/com/lan4jn/file/service/ShareLinkServiceTest.java`

- [ ] **Step 1: Write failing test for recipient display text**

Add this test to `src/test/java/com/lan4jn/file/service/ShareLinkServiceTest.java`:

```java
@Test
void shouldEnrichUserShareRecipients() {
    FileService fileService = mock(FileService.class);
    ShareLinkRepository shareLinkRepository = mock(ShareLinkRepository.class);
    ShareFileRecordRepository shareFileRecordRepository = mock(ShareFileRecordRepository.class);
    UserRepository userRepository = mock(UserRepository.class);
    UserShareMessageRepository messageRepository = mock(UserShareMessageRepository.class);

    ShareLink shareLink = new ShareLink();
    shareLink.setShareCode("CODE1234");
    shareLink.setCreateBy("creator");
    shareLink.setTargetType("USER");

    UserShareMessage message = new UserShareMessage();
    message.setShareCode("CODE1234");
    message.setRecipientUserId(2L);

    User recipient = new User();
    recipient.setId(2L);
    recipient.setUsername("userA");
    recipient.setNickname("用户A");

    when(shareLinkRepository.findVisibleByCreateByOrderByCreateTimeDesc("creator")).thenReturn(List.of(shareLink));
    when(messageRepository.findByShareCode("CODE1234")).thenReturn(List.of(message));
    when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));

    ShareLinkService shareLinkService = new ShareLinkService(
            fileService, shareLinkRepository, shareFileRecordRepository,
            userRepository, messageRepository, new ShareSupport(), 24);

    List<ShareLink> shareLinks = shareLinkService.getShareLinksByUser("creator");

    assertEquals("用户A(userA)", shareLinks.get(0).getRecipientDisplayText());
}
```

Add imports if missing:

```java
import com.lan4jn.file.entity.User;
import com.lan4jn.file.model.UserShareMessage;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.repository.UserShareMessageRepository;
import java.util.List;
```

- [ ] **Step 2: Run service tests to verify failure**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=ShareLinkServiceTest test
```

Expected: FAIL because `recipientDisplayText` and `findByShareCode` do not exist.

- [ ] **Step 3: Add transient display field**

Modify `src/main/java/com/lan4jn/file/model/ShareLink.java`:

```java
@Transient
private String recipientDisplayText;

public String getRecipientDisplayText() {
    return recipientDisplayText;
}

public void setRecipientDisplayText(String recipientDisplayText) {
    this.recipientDisplayText = recipientDisplayText;
}
```

- [ ] **Step 4: Add message lookup repository method**

Modify `src/main/java/com/lan4jn/file/repository/UserShareMessageRepository.java`:

```java
List<UserShareMessage> findByShareCode(String shareCode);
```

- [ ] **Step 5: Enrich share list results**

Modify `src/main/java/com/lan4jn/file/service/ShareLinkService.java`:

```java
import java.util.LinkedHashSet;

List<ShareLink> getShareLinksByUser(String createBy) {
    List<ShareLink> shareLinks = shareLinkRepository.findVisibleByCreateByOrderByCreateTimeDesc(createBy);
    for (ShareLink shareLink : shareLinks) {
        enrichRecipientDisplayText(shareLink);
    }
    return shareLinks;
}

private void enrichRecipientDisplayText(ShareLink shareLink) {
    if (userShareMessageRepository == null || userRepository == null || !TARGET_TYPE_USER.equals(shareLink.getTargetType())) {
        shareLink.setRecipientDisplayText(null);
        return;
    }
    List<UserShareMessage> messages = userShareMessageRepository.findByShareCode(shareLink.getShareCode());
    LinkedHashSet<String> names = new LinkedHashSet<>();
    for (UserShareMessage message : messages) {
        if (message.getRecipientUserId() == null) {
            continue;
        }
        userRepository.findById(message.getRecipientUserId())
                .map(this::formatRecipientName)
                .ifPresent(names::add);
    }
    shareLink.setRecipientDisplayText(String.join("、", names));
}

private String formatRecipientName(User user) {
    if (user.getNickname() == null || user.getNickname().isBlank()) {
        return user.getUsername();
    }
    return user.getNickname() + "(" + user.getUsername() + ")";
}
```

- [ ] **Step 6: Run service tests to verify pass**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=ShareLinkServiceTest test
```

Expected: PASS.

### Task 3: Share controller cancel endpoint

**Files:**
- Modify: `src/main/java/com/lan4jn/file/controller/ShareController.java`
- Test: `src/test/java/com/lan4jn/file/controller/ShareControllerTest.java`

- [ ] **Step 1: Write failing controller test for cancel endpoint**

Add this test to `src/test/java/com/lan4jn/file/controller/ShareControllerTest.java`:

```java
@Test
void shouldCancelShareLink() throws Exception {
    mockMvc.perform(post("/api/share/cancel")
                    .param("shareCode", "CODE1234")
                    .principal(new UsernamePasswordAuthenticationToken("admin", "N/A")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

    verify(shareService).cancelShareLink("CODE1234", "admin");
}
```

- [ ] **Step 2: Run controller test to verify failure**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=ShareControllerTest#shouldCancelShareLink test
```

Expected: FAIL because `/api/share/cancel` does not exist.

- [ ] **Step 3: Add cancel endpoint**

Modify `src/main/java/com/lan4jn/file/controller/ShareController.java`:

```java
@PostMapping("/cancel")
public Result<Void> cancelShare(@RequestParam("shareCode") String shareCode, Authentication authentication) {
    permissionAssertService.assertApiPermission(PermissionCodes.API_SHARE_MANAGE);
    shareCode = RequestValidationUtils.requireText(shareCode, "shareCode");
    log.info("Cancelling share link: {}", shareCode);
    shareService.cancelShareLink(shareCode, authentication.getName());
    return Result.success();
}
```

Also change the delete endpoint log message to match its new semantics:

```java
log.info("Deleting share record: {}", shareCode);
```

- [ ] **Step 4: Run controller test to verify pass**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=ShareControllerTest#shouldCancelShareLink test
```

Expected: PASS.

### Task 4: Share-content frontend helper

**Files:**
- Create: `src/main/resources/static/js/share-management-helpers.js`
- Modify: `src/main/resources/static/index.html`
- Test: `src/test/js/share-management-helpers.test.js`

- [ ] **Step 1: Write failing JS tests for content display rules**

Create `src/test/js/share-management-helpers.test.js`:

```javascript
const test = require('node:test');
const assert = require('node:assert/strict');

const helpers = require('../../main/resources/static/js/share-management-helpers.js');

test('should build single file share item from share record', () => {
    const items = helpers.normalizeShareContentItems({
        bucket: 'bucket-a',
        objectName: 'docs/report.pdf',
        fileName: 'report.pdf',
        isFolder: false
    });

    assert.deepEqual(items, [{
        fileName: 'report.pdf',
        objectName: 'docs/report.pdf',
        isFolder: false,
        tooltip: 'bucket-a/docs/report.pdf'
    }]);
});

test('should show first three share items and hidden count', () => {
    const result = helpers.buildVisibleShareContent([
        { fileName: 'a.txt', objectName: 'a.txt', isFolder: false },
        { fileName: 'b.txt', objectName: 'b.txt', isFolder: false },
        { fileName: 'c.txt', objectName: 'c.txt', isFolder: false },
        { fileName: 'd.txt', objectName: 'd.txt', isFolder: false }
    ], 3);

    assert.equal(result.visibleItems.length, 3);
    assert.equal(result.hiddenCount, 1);
});

test('should format user share target with recipients', () => {
    assert.equal(
        helpers.formatShareTargetType({ targetType: 'USER', recipientDisplayText: '用户A(userA)' }),
        '分享给：用户A(userA)'
    );
});
```

- [ ] **Step 2: Run JS test to verify failure**

Run:

```bash
node --test src/test/js/share-management-helpers.test.js
```

Expected: FAIL because `share-management-helpers.js` does not exist.

- [ ] **Step 3: Implement helper**

Create `src/main/resources/static/js/share-management-helpers.js`:

```javascript
(function (globalScope) {
    function joinPath(bucket, objectName) {
        var normalizedBucket = String(bucket || '').replace(/\/+$/, '');
        var normalizedObject = String(objectName || '').replace(/^\/+/, '');
        if (!normalizedBucket) {
            return normalizedObject;
        }
        if (!normalizedObject) {
            return normalizedBucket;
        }
        return normalizedBucket + '/' + normalizedObject;
    }

    function normalizeShareContentItems(share, rawItems) {
        if (!share) {
            return [];
        }
        if (!share.isFolder) {
            var singleObjectName = share.objectName || '';
            return [{
                fileName: share.fileName || singleObjectName || '文件',
                objectName: singleObjectName,
                isFolder: false,
                tooltip: joinPath(share.bucket, singleObjectName)
            }];
        }
        return (rawItems || []).map(function (item) {
            var objectName = item.objectName || '';
            return {
                fileName: item.fileName || objectName || '项目',
                objectName: objectName,
                isFolder: !!item.isFolder,
                tooltip: joinPath(share.bucket, objectName)
            };
        });
    }

    function buildVisibleShareContent(items, limit) {
        var normalizedLimit = limit || 3;
        var allItems = items || [];
        return {
            visibleItems: allItems.slice(0, normalizedLimit),
            hiddenCount: Math.max(allItems.length - normalizedLimit, 0),
            hiddenItems: allItems.slice(normalizedLimit)
        };
    }

    function formatShareTargetType(share) {
        if (!share || share.targetType !== 'USER') {
            return '分享到链接';
        }
        return share.recipientDisplayText ? '分享给：' + share.recipientDisplayText : '分享到用户';
    }

    var exported = {
        normalizeShareContentItems: normalizeShareContentItems,
        buildVisibleShareContent: buildVisibleShareContent,
        formatShareTargetType: formatShareTargetType
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }
    if (globalScope) {
        globalScope.ShareManagementHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
```

- [ ] **Step 4: Include helper script in page**

Modify `src/main/resources/static/index.html`, add before `app.js` script include:

```html
<script src="./js/share-management-helpers.js"></script>
```

- [ ] **Step 5: Run JS test to verify pass**

Run:

```bash
node --test src/test/js/share-management-helpers.test.js
```

Expected: PASS.

### Task 5: Share table UI and frontend actions

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/js/app.js`
- Test: `src/test/js/share-management-helpers.test.js`

- [ ] **Step 1: Wire helper in app.js**

Add near other helper constants in `src/main/resources/static/js/app.js`:

```javascript
const shareManagementHelpers = window.ShareManagementHelpers || {
    normalizeShareContentItems: () => [],
    buildVisibleShareContent: (items) => ({ visibleItems: items || [], hiddenCount: 0, hiddenItems: [] }),
    formatShareTargetType: (share) => share && share.targetType === 'USER' ? '分享到用户' : '分享到链接'
};
```

- [ ] **Step 2: Use helper for target-type display**

Replace the existing `formatShareTargetType` function in `src/main/resources/static/js/app.js` with:

```javascript
const formatShareTargetType = (share) => shareManagementHelpers.formatShareTargetType(share);
```

- [ ] **Step 3: Build share-content state in loadShares**

Inside `loadShares`, map share records with content state:

```javascript
const contentItems = shareManagementHelpers.normalizeShareContentItems(item);
const visibleContent = shareManagementHelpers.buildVisibleShareContent(contentItems, 3);
return {
    ...item,
    createTimeText: formatDateTime(item.createTime),
    expireTimeText: formatDateTime(item.expireTime),
    hasPasswordText: item.targetType === 'USER'
        ? '站内信'
        : (item.hasPassword && item.password ? '提取码：' + item.password : '公开访问'),
    shareTargetTypeText: formatShareTargetType(item),
    shareUrl: buildShareUrl(item, false),
    shareUrlWithPassword: buildShareUrl(item, true),
    shareNoticeText: buildShareCopyText(item, 'notice'),
    shareItems: contentItems,
    visibleShareItems: visibleContent.visibleItems,
    hiddenShareItemCount: visibleContent.hiddenCount,
    hiddenShareItems: visibleContent.hiddenItems,
    shareItemsLoaded: !item.isFolder,
    shareItemsLoading: false,
    shareItemsError: false
};
```

After assigning `shareList.value`, start async content loads for folder/batch shares:

```javascript
shareList.value.forEach(share => loadShareItemsForManage(share));
```

- [ ] **Step 4: Refresh content state after loading share items**

In `loadShareItemsForManage`, after `res.data` is loaded:

```javascript
const contentItems = shareManagementHelpers.normalizeShareContentItems(share, res.data || []);
const visibleContent = shareManagementHelpers.buildVisibleShareContent(contentItems, 3);
share.shareItems = contentItems;
share.visibleShareItems = visibleContent.visibleItems;
share.hiddenShareItemCount = visibleContent.hiddenCount;
share.hiddenShareItems = visibleContent.hiddenItems;
share.shareItemsLoaded = true;
share.shareItemsError = false;
```

In the catch block:

```javascript
share.shareItems = [];
share.visibleShareItems = [];
share.hiddenShareItemCount = 0;
share.hiddenShareItems = [];
share.shareItemsError = true;
```

- [ ] **Step 5: Add cancelShare action**

Add in `src/main/resources/static/js/app.js` near `deleteShare`:

```javascript
const cancelShare = async (share) => {
    try {
        await ElementPlus.ElMessageBox.confirm('确定要取消这个分享吗？取消后接收人和分享链接都无法继续访问。', '提示', {
            confirmButtonText: '确定',
            cancelButtonText: '取消',
            type: 'warning'
        });
        const res = await api.post('/share/cancel', null, { params: { shareCode: share.shareCode } });
        if (res.code === 200) {
            ElementPlus.ElMessage.success('取消分享成功');
            loadShares();
        }
    } catch (error) {
        if (error !== 'cancel') {
            ElementPlus.ElMessage.error('取消分享失败: ' + (error.response?.data?.message || error.message));
        }
    }
};
```

Export `cancelShare` from the Vue setup return object.

- [ ] **Step 6: Change deleteShare copy**

Update confirmation and success messages in `deleteShare`:

```javascript
await ElementPlus.ElMessageBox.confirm('确定要删除这条分享记录吗？删除记录不会取消已发出的分享。', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
});
```

```javascript
ElementPlus.ElMessage.success('删除记录成功');
```

- [ ] **Step 7: Move share content into table column**

Modify the `我的分享` table in `src/main/resources/static/index.html`:

```html
<el-table-column prop="shareCode" label="分享码"></el-table-column>
<el-table-column label="分享内容" min-width="260">
    <template #default="{ row }">
        <div v-if="row.shareItemsLoading" style="color: #909399;">正在加载...</div>
        <div v-else-if="row.shareItemsError" style="color: #f56c6c;">加载失败</div>
        <div v-else style="display: flex; flex-wrap: wrap; gap: 8px;">
            <el-tooltip
                v-for="item in row.visibleShareItems"
                :key="item.objectName"
                :content="item.tooltip"
                placement="top"
                effect="light"
            >
                <el-tag :type="item.isFolder ? 'warning' : 'info'" effect="plain" size="large">
                    {{ item.isFolder ? '目录' : '文件' }} / {{ item.fileName }}
                </el-tag>
            </el-tooltip>
            <el-tooltip
                v-if="row.hiddenShareItemCount > 0"
                :content="row.hiddenShareItems.map(item => item.tooltip).join('\n')"
                placement="top"
                effect="light"
            >
                <el-tag type="warning" effect="plain" size="large">+{{ row.hiddenShareItemCount }}</el-tag>
            </el-tooltip>
        </div>
    </template>
</el-table-column>
<el-table-column prop="createTimeText" label="分享时间" width="180"></el-table-column>
```

Remove the old expanded-row `分享内容` block.

- [ ] **Step 8: Update expanded metadata and action buttons**

In expanded metadata add:

```html
<div v-if="row.targetType === 'USER'"><strong>接收用户：</strong>{{ row.recipientDisplayText || '未找到接收用户' }}</div>
<div><strong>分享状态：</strong>{{ row.cancelled ? '已取消' : '有效' }}</div>
```

Update action buttons:

```html
<el-tooltip content="删除分享记录" placement="top" effect="light">
    <el-button v-if="hasApiPermission('API_SHARE_MANAGE')" size="small" type="danger" @click="deleteShare(row)">
        <el-icon><Delete /></el-icon>
    </el-button>
</el-tooltip>
<el-tooltip content="取消分享" placement="top" effect="light">
    <el-button v-if="hasApiPermission('API_SHARE_MANAGE') && !row.cancelled" size="small" type="warning" @click="cancelShare(row)">
        取消分享
    </el-button>
</el-tooltip>
```

- [ ] **Step 9: Run frontend checks**

Run:

```bash
node --test src/test/js/share-management-helpers.test.js
node --check src/main/resources/static/js/app.js
```

Expected: both PASS.

### Task 6: Version notes and final verification

**Files:**
- Modify: `pom.xml`
- Create: `src/main/resources/version-notes/v1.8.1.md`

- [ ] **Step 1: Update version**

Modify `pom.xml`:

```xml
<version>1.8.1-build123</version>
```

- [ ] **Step 2: Add version note**

Create `src/main/resources/version-notes/v1.8.1.md`:

```markdown
# v1.8.1 版本更新内容

## 分享管理优化

- 我的分享列表新增“分享内容”列，分享文件和目录可直接在表格中查看。
- 分享内容标签支持鼠标悬停查看对象存放路径。
- 分享到用户时，分享方式会直接显示具体接收用户。
- 删除分享记录和取消分享拆分为两个动作，避免误删导致分享失效。

## 兼容性修复

- 已取消分享在访问、校验、下载和内容列表入口统一提示“分享已取消”。
- 旧分享记录默认保持有效和可见，升级后不影响原有分享访问。
```

- [ ] **Step 3: Run targeted backend verification**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home mvn -q -Dtest=ShareLinkServiceTest,ShareControllerTest test
```

Expected: PASS. Existing tests may log expected failure-path stack traces; command exit code must be `0`.

- [ ] **Step 4: Run targeted frontend verification**

Run:

```bash
node --test src/test/js/share-management-helpers.test.js
node --check src/main/resources/static/js/app.js
```

Expected: PASS.

- [ ] **Step 5: Inspect changed files**

Run:

```bash
git status --short
git diff --stat
```

Expected: only planned files changed, plus any pre-existing untracked `.DS_Store` remains untouched.
