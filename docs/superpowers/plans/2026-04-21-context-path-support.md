# Context Path Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the application fully support `server.servlet.context-path` so the UI, API calls, downloads, and share pages all work under both `/` and a non-root prefix such as `/fm`.

**Architecture:** Keep Spring route mappings unchanged and let the servlet context path prepend them at runtime. Replace hard-coded browser root paths with shared context-path helpers in the front-end, and add one server-side helper path builder for URLs returned by download/transfer services.

**Tech Stack:** Spring Boot 3.2, Spring MVC, static HTML, Vue 3, Axios, JUnit 5, node:test

---

## File Map

**Create**
- `src/main/resources/static/js/context-path-helpers.js`
- `src/test/java/com/lan4jn/file/service/TransferProxyServiceContextPathTest.java`
- `src/test/js/context-path-helpers.test.js`

**Modify**
- `config/application.yml`
- `config/application-dev.yml`
- `config/application-prod.yml`
- `src/main/java/com/lan4jn/file/config/WebMvcConfig.java`
- `src/main/java/com/lan4jn/file/service/TransferProxyService.java`
- `src/main/resources/static/index.html`
- `src/main/resources/static/lite.html`
- `src/main/resources/static/share.html`
- `src/main/resources/static/test.html`
- `src/main/resources/static/js/app.js`
- `src/main/resources/static/js/lite.js`
- `src/main/resources/static/js/download-manager.js`
- `src/main/resources/static/js/transfer-ui-helpers.js`
- `src/main/resources/static/js/url-helpers.js`
- `pom.xml`

## Task 1: Add Client-Side Context Path Helper

**Files:**
- Create: `src/main/resources/static/js/context-path-helpers.js`
- Test: `src/test/js/context-path-helpers.test.js`

- [ ] **Step 1: Write the failing front-end test**

Create `src/test/js/context-path-helpers.test.js`:

```javascript
const test = require('node:test');
const assert = require('node:assert/strict');

const {
    normalizeContextPath,
    joinContextPath,
    isContextPathUrlMatch
} = require('../../main/resources/static/js/context-path-helpers.js');

test('normalizeContextPath should keep root path empty for browser joins', () => {
    assert.equal(normalizeContextPath('/'), '');
    assert.equal(normalizeContextPath(''), '');
    assert.equal(normalizeContextPath('/fm/'), '/fm');
});

test('joinContextPath should prepend non-root context path', () => {
    assert.equal(joinContextPath('', '/api/file/list'), '/api/file/list');
    assert.equal(joinContextPath('/fm', '/api/file/list'), '/fm/api/file/list');
    assert.equal(joinContextPath('/fm/', 'lite'), '/fm/lite');
});

test('isContextPathUrlMatch should match prefixed application URLs', () => {
    assert.equal(isContextPathUrlMatch('/fm', '/fm/api/file/download', '/api/file/download'), true);
    assert.equal(isContextPathUrlMatch('/fm', '/api/file/download', '/api/file/download'), false);
    assert.equal(isContextPathUrlMatch('', '/api/file/download', '/api/file/download'), true);
});
```

- [ ] **Step 2: Run the front-end test to verify it fails**

Run:

```bash
node --test src/test/js/context-path-helpers.test.js
```

Expected: FAIL because `context-path-helpers.js` does not exist yet.

- [ ] **Step 3: Implement the helper module**

Create `src/main/resources/static/js/context-path-helpers.js`:

```javascript
(function (global) {
    const normalizeContextPath = (value) => {
        const raw = String(value || '').trim();
        if (!raw || raw === '/') {
            return '';
        }
        const withLeadingSlash = raw.startsWith('/') ? raw : ('/' + raw);
        return withLeadingSlash.replace(/\/+$/, '');
    };

    const normalizeAppPath = (value) => {
        const raw = String(value || '').trim();
        if (!raw) {
            return '/';
        }
        return raw.startsWith('/') ? raw : ('/' + raw);
    };

    const joinContextPath = (contextPath, path) => {
        const normalizedContextPath = normalizeContextPath(contextPath);
        const normalizedPath = normalizeAppPath(path);
        return normalizedContextPath + normalizedPath;
    };

    const isContextPathUrlMatch = (contextPath, pathname, appPath) => {
        return String(pathname || '') === joinContextPath(contextPath, appPath);
    };

    const api = {
        normalizeContextPath,
        joinContextPath,
        isContextPathUrlMatch
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
    global.ContextPathHelpers = api;
})(typeof window !== 'undefined' ? window : globalThis);
```

- [ ] **Step 4: Run the front-end test to verify it passes**

Run:

```bash
node --test src/test/js/context-path-helpers.test.js
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/js/context-path-helpers.js \
        src/test/js/context-path-helpers.test.js
git commit -m "feat: add context path helper"
```

## Task 2: Make Static Entry Pages Context-Safe

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/lite.html`
- Modify: `src/main/resources/static/share.html`
- Modify: `src/main/resources/static/test.html`
- Create: `src/test/js/context-path-entry-pages.test.js`

- [ ] **Step 1: Write the failing entry-page test**

Create `src/test/js/context-path-entry-pages.test.js`:

```javascript
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const pages = [
    'src/main/resources/static/index.html',
    'src/main/resources/static/lite.html',
    'src/main/resources/static/share.html'
];

test('entry pages should not use host-root asset paths', () => {
    for (const file of pages) {
        const content = fs.readFileSync(file, 'utf8');
        assert.doesNotMatch(content, /(?:href|src)=["']\/(?:css|js|vendor|favicon)/);
    }
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
node --test src/test/js/context-path-entry-pages.test.js
```

Expected: FAIL because entry pages still reference root-relative assets.

- [ ] **Step 3: Convert static asset references to relative paths**

Update each HTML entry page so links and scripts use `./...` paths:

Examples:

```html
<link rel="icon" type="image/svg+xml" href="./favicon.svg?v=20260324">
<link rel="stylesheet" href="./css/app.css">
<script src="./js/axios.min.js"></script>
<script src="./vendor/sha512.min.js"></script>
<script src="./js/context-path-helpers.js"></script>
```

Apply the same rule to:

- main page
- lite page
- share page
- test page if it remains a runnable entry

- [ ] **Step 4: Run the test to verify it passes**

Run:

```bash
node --test src/test/js/context-path-entry-pages.test.js
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/index.html \
        src/main/resources/static/lite.html \
        src/main/resources/static/share.html \
        src/main/resources/static/test.html \
        src/test/js/context-path-entry-pages.test.js
git commit -m "fix: make entry pages context path safe"
```

## Task 3: Make Front-End API, Navigation, and Download Logic Context-Aware

**Files:**
- Modify: `src/main/resources/static/js/app.js`
- Modify: `src/main/resources/static/js/lite.js`
- Modify: `src/main/resources/static/js/download-manager.js`
- Modify: `src/main/resources/static/js/transfer-ui-helpers.js`
- Modify: `src/main/resources/static/js/url-helpers.js`
- Test: `src/test/js/context-path-helpers.test.js`
- Test: `src/test/js/bucket-stats-page.test.js`

- [ ] **Step 1: Write the failing helper regression test**

Extend `src/test/js/context-path-helpers.test.js`:

```javascript
const transferUiHelpers = require('../../main/resources/static/js/transfer-ui-helpers.js');

test('transfer download matcher should accept context-prefixed file download url', () => {
    const matcher = transferUiHelpers.createAppDownloadMatcher('/fm');
    assert.equal(matcher('https://example.com/fm/api/file/download?objectName=a'), true);
    assert.equal(matcher('https://example.com/api/file/download?objectName=a'), false);
});
```

Expected failure: helper does not yet expose context-aware matcher logic.

- [ ] **Step 2: Run the front-end tests to verify failure**

Run:

```bash
node --test src/test/js/context-path-helpers.test.js src/test/js/bucket-stats-page.test.js
```

Expected: FAIL because app/lite/download helpers still use hard-coded `/api` and `/lite`.

- [ ] **Step 3: Implement shared context path usage across front-end scripts**

In `app.js`:

```javascript
const contextPath = window.__APP_CONTEXT_PATH__ || '';
const joinContextPath = (window.ContextPathHelpers || {}).joinContextPath;

const api = axios.create({
    baseURL: joinContextPath(contextPath, '/api'),
    timeout: 30000
});
```

Replace page switches and upload URLs:

```javascript
window.location.href = joinContextPath(contextPath, '/lite');
const uploadUrl = computed(() => joinContextPath(contextPath, '/api/file/upload'));
```

In `lite.js`, do the same for `baseURL`.

In `transfer-ui-helpers.js`, export a context-aware matcher factory:

```javascript
const createAppDownloadMatcher = (contextPath) => (targetUrl) => {
    const resolved = new URL(targetUrl, window.location.origin);
    return ContextPathHelpers.isContextPathUrlMatch(contextPath, resolved.pathname, '/api/file/download');
};
```

In `download-manager.js` and `url-helpers.js`, route all local app links through `joinContextPath(...)`.

- [ ] **Step 4: Re-run the front-end tests to verify they pass**

Run:

```bash
node --test src/test/js/context-path-helpers.test.js \
            src/test/js/context-path-entry-pages.test.js \
            src/test/js/bucket-stats-page.test.js
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/js/app.js \
        src/main/resources/static/js/lite.js \
        src/main/resources/static/js/download-manager.js \
        src/main/resources/static/js/transfer-ui-helpers.js \
        src/main/resources/static/js/url-helpers.js \
        src/test/js/context-path-helpers.test.js \
        src/test/js/context-path-entry-pages.test.js \
        src/test/js/bucket-stats-page.test.js
git commit -m "fix: respect context path in front-end routes"
```

## Task 4: Make Server-Generated Local URLs Context-Aware

**Files:**
- Modify: `src/main/java/com/lan4jn/file/service/TransferProxyService.java`
- Create: `src/test/java/com/lan4jn/file/service/TransferProxyServiceContextPathTest.java`

- [ ] **Step 1: Write the failing service test**

Create `src/test/java/com/lan4jn/file/service/TransferProxyServiceContextPathTest.java`:

```java
package com.lan4jn.file.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferProxyServiceContextPathTest {

    @Test
    void shouldPrefixGeneratedDownloadUrlWithServletContextPath() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/fm");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            TransferProxyService service = new TransferProxyService(null, null, null, null, null);
            Method method = TransferProxyService.class.getDeclaredMethod("buildAppUrl", String.class);
            method.setAccessible(true);

            String url = (String) method.invoke(service, "/api/file/download?objectName=a");

            assertEquals("/fm/api/file/download?objectName=a", url);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
```

- [ ] **Step 2: Run the targeted test to verify it fails**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home \
mvn -q -Dtest=TransferProxyServiceContextPathTest test
```

Expected: FAIL because `TransferProxyService` does not yet prepend servlet context path.

- [ ] **Step 3: Add one server-side local URL helper and use it**

In `TransferProxyService.java`, add a helper:

```java
private String buildAppUrl(String path) {
    ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    String contextPath = "";
    if (attributes != null && attributes.getRequest() != null) {
        contextPath = attributes.getRequest().getContextPath();
    }
    if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
        contextPath = "";
    }
    return contextPath + path;
}
```

Then route all returned local URLs through it:

```java
return buildAppUrl("/api/file/download?objectName=" + encodedObject + "&bucket=" + encodedBucket);
```

Apply the same rule to:

- share download URLs
- transfer stream URLs
- archive task URLs
- upload URLs returned to the browser

- [ ] **Step 4: Run the targeted test to verify it passes**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home \
mvn -q -Dtest=TransferProxyServiceContextPathTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/lan4jn/file/service/TransferProxyService.java \
        src/test/java/com/lan4jn/file/service/TransferProxyServiceContextPathTest.java
git commit -m "fix: prefix transfer urls with context path"
```

## Task 5: Align Configuration and MVC Wiring

**Files:**
- Modify: `config/application.yml`
- Modify: `config/application-dev.yml`
- Modify: `config/application-prod.yml`
- Modify: `src/main/java/com/lan4jn/file/config/WebMvcConfig.java`

- [ ] **Step 1: Write the failing regression test**

Add a new assertion to `src/test/java/com/lan4jn/file/architecture/SplitContractTest.java`:

```java
@Test
void shouldNotRequireCustomContextPathSetting() {
    assertDoesNotThrow(() -> Class.forName("com.lan4jn.file.config.WebMvcConfig"));
}
```

This test is intentionally minimal. The real RED condition comes from compilation/runtime after wiring cleanup if the config class still assumes root-only resource handlers.

- [ ] **Step 2: Run the targeted regression slice**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home \
mvn -q -Dtest=SplitContractTest test
```

Expected: pass or remain neutral before refactor; keep it as a guard while simplifying MVC wiring.

- [ ] **Step 3: Simplify MVC config and add documented default context path**

Update `config/application*.yml` comments to document:

```yaml
server:
  servlet:
    # 留空或省略表示根路径 /
    # 示例: /fm
    context-path: /
```

If the current deployment should keep true default semantics instead of explicit `/`, document it in comments and only set it where required by the operator.

In `WebMvcConfig.java`, remove root/static resource mappings that duplicate Spring Boot static resource handling unnecessarily, while keeping only preview-specific resource mapping if still needed.

- [ ] **Step 4: Run targeted verification**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home \
mvn -q -Dtest=SplitContractTest,TransferProxyServiceContextPathTest test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add config/application.yml \
        config/application-dev.yml \
        config/application-prod.yml \
        src/main/java/com/lan4jn/file/config/WebMvcConfig.java \
        src/test/java/com/lan4jn/file/architecture/SplitContractTest.java
git commit -m "chore: document and align context path config"
```

## Task 6: Full Verification and Version Bump

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: Increment build version once**

Update `pom.xml`:

```xml
<version>1.7.2-build118</version>
```

- [ ] **Step 2: Run front-end verification**

Run:

```bash
node --test src/test/js/context-path-helpers.test.js \
            src/test/js/context-path-entry-pages.test.js \
            src/test/js/bucket-stats-page.test.js
```

Expected: PASS.

- [ ] **Step 3: Run targeted Maven verification**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home \
mvn -q -Dtest=TransferProxyServiceContextPathTest,FileControllerTest,BucketStatsRefreshServiceTest,BucketServiceSecurityTest test
```

Expected: PASS.

- [ ] **Step 4: Run full Maven suite**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home \
mvn test
```

Expected: PASS.

- [ ] **Step 5: Verify final project version**

Run:

```bash
JAVA_HOME=/Users/mewcapoo/Library/Java/JavaVirtualMachines/temurin-17.0.18/Contents/Home \
mvn -q -DforceStdout help:evaluate -Dexpression=project.version
```

Expected: `1.7.2-build118`

- [ ] **Step 6: Commit**

```bash
git add pom.xml
git commit -m "feat: support servlet context path"
```

## Self-Review

- Spec coverage:
  - standard `server.servlet.context-path`: Task 5
  - no `/` fallback under non-root prefix: Tasks 2-5 preserve prefixed-only behavior
  - UI/API/download/share support under prefix: Tasks 2-4
  - root-path behavior unchanged: Tasks 1-6 verification
- Placeholder scan:
  - no TODO/TBD markers remain
- Type consistency:
  - shared helper names are `normalizeContextPath`, `joinContextPath`, `isContextPathUrlMatch`
  - back-end helper name is `buildAppUrl`
