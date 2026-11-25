const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

// Helper to extract function body with balanced braces
function extractFunctionBody(code, functionName) {
    const idx = code.indexOf(`function ${functionName}()`);
    if (idx === -1) return null;
    const start = code.indexOf('{', idx);
    let depth = 1;
    let end = start + 1;
    while (end < code.length && depth > 0) {
        if (code[end] === '{') depth++;
        else if (code[end] === '}') depth--;
        end++;
    }
    return code.slice(start + 1, end - 1);
}

// 1. Verify transfer-ui-helpers handles throwing localStorage
test('transfer-ui-helpers runArchiveDownload tolerates throwing localStorage', async () => {
    const originalWindow = globalThis.window;
    const originalDocument = globalThis.document;
    const originalFetch = globalThis.fetch;
    const originalSetTimeout = globalThis.setTimeout;
    try {
        let fetchCalledWithHeaders = null;
        globalThis.setTimeout = () => 1;
        globalThis.window = {
            location: { origin: 'http://localhost:8080', pathname: '/' },
            __APP_CONTEXT_PATH__: '',
            get localStorage() {
                throw new Error('SecurityError: The operation is insecure');
            },
            URL: {
                createObjectURL: () => 'blob:test-archive',
                revokeObjectURL: () => {},
            },
        };
        globalThis.document = {
            createElement: () => ({
                click: () => {},
                remove: () => {},
                setAttribute: () => {},
            }),
            body: {
                appendChild: () => {},
            },
        };
        globalThis.fetch = async (url, options) => {
            fetchCalledWithHeaders = options.headers;
            return {
                ok: true,
                headers: new Map([['Content-Length', '10']]),
                body: {
                    getReader() {
                        let done = false;
                        return {
                            releaseLock() {},
                            async read() {
                                if (done) return { done: true, value: undefined };
                                done = true;
                                return { done: false, value: new Uint8Array([1, 2, 3]) };
                            },
                        };
                    },
                },
            };
        };

        const transferHelpersPath = path.resolve(__dirname, '../../main/resources/static/js/transfer-ui-helpers.js');
        delete require.cache[require.resolve(transferHelpersPath)];
        const TransferUiHelpers = require(transferHelpersPath);

        await TransferUiHelpers.runArchiveDownload('http://localhost:8080/api/transfer/archive-tasks/task-1/download', 'test.zip');
        assert.deepEqual(fetchCalledWithHeaders, {});
    } finally {
        globalThis.window = originalWindow;
        globalThis.document = originalDocument;
        globalThis.fetch = originalFetch;
        globalThis.setTimeout = originalSetTimeout;
    }
});

// 2. Verify share.html authorizedHeaders tolerates throwing localStorage
test('share.html authorizedHeaders tolerates throwing localStorage', () => {
    const shareHtmlPath = path.resolve(__dirname, '../../main/resources/static/share.html');
    const shareHtml = fs.readFileSync(shareHtmlPath, 'utf8');

    const fnBody = extractFunctionBody(shareHtml, 'authorizedHeaders');
    assert.ok(fnBody, 'authorizedHeaders function should exist in share.html');

    const originalWindow = globalThis.window;
    try {
        globalThis.window = {
            get localStorage() {
                throw new Error('SecurityError: Access denied');
            },
        };
        const fn = new Function(fnBody);
        const headers = fn();
        assert.deepEqual(headers, {});
    } finally {
        globalThis.window = originalWindow;
    }
});

// 3. Verify share.html theme toggle does not evaluate bare throwing window.localStorage
test('share.html theme toggle does not evaluate bare throwing window.localStorage', () => {
    const shareHtmlPath = path.resolve(__dirname, '../../main/resources/static/share.html');
    const shareHtml = fs.readFileSync(shareHtmlPath, 'utf8');

    // It should not pass bare window.localStorage directly into toggleTheme without guarding
    assert.ok(
        !shareHtml.includes('themeApi.toggleTheme(currentTheme, window.localStorage, document)'),
        'share.html should guard or safely pass storage to toggleTheme'
    );
});

// 4. Verify app.js safe token storage, finishLogin degradation, and logout tolerance
test('app.js token handling degrades to in-memory on storage failure with non-leaking warning', () => {
    const appJsPath = path.resolve(__dirname, '../../main/resources/static/js/app.js');
    const appJs = fs.readFileSync(appJsPath, 'utf8');

    // Verification 1: token initialization must not directly access localStorage without fallback
    assert.ok(
        !appJs.includes("const token = ref(localStorage.getItem('token') || '');"),
        'token ref should not directly call unshielded localStorage.getItem'
    );

    // Verification 2: finishLogin must handle setItem throwing QuotaExceededError/SecurityError
    // and provide clear non-leaking user notification, without copying token to URL
    assert.ok(
        appJs.includes("本地存储受限") || appJs.includes("存储受限"),
        'finishLogin should warn user when local storage is restricted or unavailable'
    );

    // Verification 3: logout must safely handle removeItem errors
    assert.ok(
        !appJs.includes("localStorage.removeItem('token');\n            userInfo.value"),
        'logout should safely handle removeItem without throwing'
    );

    // Verification 4: must not copy token to URL
    assert.ok(
        !appJs.includes("URLSearchParams") || !appJs.includes(".set('token'"),
        'token must never be copied to URL'
    );
});

// 5. Test token safe helper functions directly with simulated storage errors
test('token helper functions handle storage quota/security errors gracefully', () => {
    const appJsPath = path.resolve(__dirname, '../../main/resources/static/js/app.js');
    const appJs = fs.readFileSync(appJsPath, 'utf8');

    // Extract helper functions
    const helpersSource = appJs.slice(
        appJs.indexOf('const getStoredToken = () => {'),
        appJs.indexOf('const permissionUiHelpers =')
    );
    assert.ok(helpersSource.length > 0, 'token helpers should be defined');

    const originalWindow = globalThis.window;
    try {
        // Scenario A: window.localStorage getter throws SecurityError
        globalThis.window = {
            get localStorage() {
                throw new Error('SecurityError: Access denied');
            },
        };
        const envA = new Function(`${helpersSource}\nreturn { getStoredToken, persistStoredToken, clearStoredToken };`)();
        assert.equal(envA.getStoredToken(), '');
        assert.equal(envA.persistStoredToken('test-token'), false);
        assert.doesNotThrow(() => envA.clearStoredToken());

        // Scenario B: setItem throws QuotaExceededError
        let removedKey = null;
        globalThis.window = {
            localStorage: {
                getItem: () => 'existing-token',
                setItem: () => {
                    const err = new Error('QuotaExceededError: DOM Exception 22');
                    err.name = 'QuotaExceededError';
                    throw err;
                },
                removeItem: (key) => {
                    removedKey = key;
                },
            },
        };
        const envB = new Function(`${helpersSource}\nreturn { getStoredToken, persistStoredToken, clearStoredToken };`)();
        assert.equal(envB.getStoredToken(), 'existing-token');
        assert.equal(envB.persistStoredToken('new-token'), false);
        envB.clearStoredToken();
        assert.equal(removedKey, 'token');

        // Scenario C: normal storage works
        let savedKey = null, savedVal = null;
        globalThis.window = {
            localStorage: {
                getItem: () => '',
                setItem: (k, v) => { savedKey = k; savedVal = v; },
                removeItem: () => {},
            },
        };
        const envC = new Function(`${helpersSource}\nreturn { getStoredToken, persistStoredToken, clearStoredToken };`)();
        assert.equal(envC.persistStoredToken('abc-123'), true);
        assert.equal(savedKey, 'token');
        assert.equal(savedVal, 'abc-123');
    } finally {
        globalThis.window = originalWindow;
    }
});

// 6. Test that 401 responses trigger logout without confusing it with storage failures
test('axios 401 interceptor calls logout and does not record or report storage error', () => {
    const appJsPath = path.resolve(__dirname, '../../main/resources/static/js/app.js');
    const appJs = fs.readFileSync(appJsPath, 'utf8');

    // Find 401 handling
    const interceptorIdx = appJs.indexOf('api.interceptors.response.use');
    assert.ok(interceptorIdx !== -1, 'response interceptor should be configured');
    const interceptorSnippet = appJs.slice(interceptorIdx, interceptorIdx + 1200);

    // Verify 401 simply calls logout
    assert.ok(interceptorSnippet.includes('error.response.status === 401'), 'should match 401 status');
    assert.ok(interceptorSnippet.includes('logout();'), '401 should trigger logout()');

    // Verify 401 branch does not set or log storage failure
    const error401Branch = interceptorSnippet.slice(
        interceptorSnippet.indexOf('error.response.status === 401')
    );
    assert.ok(
        !error401Branch.includes('本地存储') && !error401Branch.includes('storage'),
        '401 response must not be treated as a storage failure'
    );
});

// 7. Verify transfer-ui-helpers prefers active in-memory token over stale persisted token
test('transfer-ui-helpers prefers active token provider over stale persisted token', async () => {
    const originalWindow = globalThis.window;
    const originalDocument = globalThis.document;
    const originalFetch = globalThis.fetch;
    const originalSetTimeout = globalThis.setTimeout;
    try {
        let sentAuthHeader = null;
        globalThis.setTimeout = () => 1;
        globalThis.window = {
            location: { origin: 'http://localhost:8080', pathname: '/' },
            __APP_CONTEXT_PATH__: '',
            localStorage: {
                getItem: (k) => k === 'token' ? 'stale-persisted-token' : null,
            },
            URL: {
                createObjectURL: () => 'blob:test',
                revokeObjectURL: () => {},
            },
        };
        globalThis.document = {
            createElement: () => ({ click() {}, remove() {}, setAttribute() {} }),
            body: { appendChild() {} },
        };
        globalThis.fetch = async (url, options) => {
            sentAuthHeader = options.headers ? options.headers.Authorization : null;
            return {
                ok: true,
                headers: new Map([['Content-Length', '10']]),
                body: {
                    getReader() {
                        let done = false;
                        return {
                            releaseLock() {},
                            async read() {
                                if (done) return { done: true, value: undefined };
                                done = true;
                                return { done: false, value: new Uint8Array([1, 2, 3]) };
                            },
                        };
                    },
                },
            };
        };

        const transferHelpersPath = path.resolve(__dirname, '../../main/resources/static/js/transfer-ui-helpers.js');
        delete require.cache[require.resolve(transferHelpersPath)];
        const TransferUiHelpers = require(transferHelpersPath);

        assert.equal(typeof TransferUiHelpers.setTokenProvider, 'function');
        // Register current active in-memory token
        TransferUiHelpers.setTokenProvider(() => 'new-memory-token');

        await TransferUiHelpers.runArchiveDownload('http://localhost:8080/api/transfer/archive-tasks/task-1/download', 'test.zip');
        assert.equal(sentAuthHeader, 'Bearer new-memory-token');
    } finally {
        globalThis.window = originalWindow;
        globalThis.document = originalDocument;
        globalThis.fetch = originalFetch;
        globalThis.setTimeout = originalSetTimeout;
    }
});

// 8. Verify logout warns user when storage clearing fails to prevent token residue
test('logout advises clearing site data when persistent logout fails', () => {
    const appJsPath = path.resolve(__dirname, '../../main/resources/static/js/app.js');
    const appJs = fs.readFileSync(appJsPath, 'utf8');

    // Verification: logout should have warning when clearStoredToken reports failure
    assert.ok(
        appJs.includes('本地存储清理受限') || appJs.includes('清理站点数据'),
        'logout should notify user when persistent token could not be removed'
    );
});
