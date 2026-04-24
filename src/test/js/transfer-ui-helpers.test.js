const test = require('node:test');
const assert = require('node:assert/strict');

const transferUiHelpers = require('../../main/resources/static/js/transfer-ui-helpers.js');

test('should request transfer session before file download', () => {
    const url = transferUiHelpers.resolveDownloadSessionUrl({
        scene: 'FILE',
        bucket: 'bucket-a',
        objectName: 'docs/readme.txt'
    });

    assert.equal(url, '/transfer/download/session');
});

test('should use native navigation when session response returns proxy download url', async () => {
    const session = { mode: 'PROXY', downloadUrl: '/api/transfer/download/stream/token-1', fileName: 'readme.txt' };
    const calls = [];

    await transferUiHelpers.runSingleFileDownload(session, (url) => calls.push(url));

    assert.deepEqual(calls, ['/api/transfer/download/stream/token-1']);
});

test('should defer object url revoke until after blob download is handed off', async () => {
    const originalWindow = global.window;
    const originalDocument = global.document;
    const originalFetch = global.fetch;
    const originalSetTimeout = global.setTimeout;

    const revoked = [];
    const timers = [];
    const clicked = [];

    global.window = {
        URL: {
            createObjectURL: () => 'blob:test-url',
            revokeObjectURL: (url) => revoked.push(url)
        }
    };
    global.document = {
        body: {
            appendChild: () => {},
            removeChild: () => {}
        },
        createElement: () => ({
            setAttribute: () => {},
            click: () => clicked.push('clicked'),
            remove: () => {}
        })
    };
    global.fetch = async () => ({
        ok: true,
        blob: async () => new Blob(['hello'])
    });
    global.setTimeout = (fn, delay) => {
        timers.push({ fn, delay });
        return timers.length;
    };

    try {
        const session = {
            mode: 'DIRECT',
            downloadUrl: 'https://download.example.test/file',
            fallbackUrl: '/api/transfer/download/stream/token-1',
            fileName: 'readme.txt'
        };

        await transferUiHelpers.runSingleFileDownload(session);

        assert.deepEqual(clicked, ['clicked']);
        assert.deepEqual(revoked, []);
        assert.equal(timers.length, 1);

        timers[0].fn();

        assert.deepEqual(revoked, ['blob:test-url']);
    } finally {
        global.window = originalWindow;
        global.document = originalDocument;
        global.fetch = originalFetch;
        global.setTimeout = originalSetTimeout;
    }
});

test('should fetch archive download with auth for authenticated batch archive url', async () => {
    const originalWindow = global.window;
    const originalDocument = global.document;
    const originalFetch = global.fetch;
    const originalSetTimeout = global.setTimeout;

    const fetchCalls = [];
    const clicked = [];

    global.window = {
        location: { origin: 'http://localhost:9080' },
        __APP_CONTEXT_PATH__: '',
        localStorage: {
            getItem: (key) => key === 'token' ? 'jwt-token' : null
        },
        URL: {
            createObjectURL: () => 'blob:archive',
            revokeObjectURL: () => {}
        }
    };
    global.document = {
        body: {
            appendChild: () => {},
            removeChild: () => {}
        },
        createElement: () => ({
            setAttribute: () => {},
            click: () => clicked.push('clicked'),
            remove: () => {}
        })
    };
    global.fetch = async (url, options) => {
        fetchCalls.push({ url, options });
        return {
            ok: true,
            blob: async () => new Blob(['zip'])
        };
    };
    global.setTimeout = (fn) => {
        fn();
        return 1;
    };

    try {
        await transferUiHelpers.runArchiveDownload('/api/transfer/archive-tasks/88/download', 'batch.zip');

        assert.equal(fetchCalls.length, 1);
        assert.equal(fetchCalls[0].url, '/api/transfer/archive-tasks/88/download');
        assert.equal(fetchCalls[0].options.headers.Authorization, 'Bearer jwt-token');
        assert.deepEqual(clicked, ['clicked']);
    } finally {
        global.window = originalWindow;
        global.document = originalDocument;
        global.fetch = originalFetch;
        global.setTimeout = originalSetTimeout;
    }
});

test('should keep native navigation for share archive download url', async () => {
    const calls = [];

    await transferUiHelpers.runArchiveDownload(
        '/api/transfer/archive-tasks/88/download?shareCode=SHARE001&password=1234',
        'share.zip',
        (url) => calls.push(url)
    );

    assert.deepEqual(calls, ['/api/transfer/archive-tasks/88/download?shareCode=SHARE001&password=1234']);
});
