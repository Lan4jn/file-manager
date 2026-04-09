const test = require('node:test');
const assert = require('node:assert/strict');

const transferUiHelpers = require('../../main/resources/static/js/transfer-ui-helpers.js');

test('should request transfer session before file download', () => {
    const url = transferUiHelpers.resolveDownloadSessionUrl({
        scene: 'FILE',
        bucket: 'bucket-a',
        objectName: 'docs/readme.txt'
    });

    assert.equal(url, '/api/transfer/download/session');
});

test('should use native navigation when session response returns proxy download url', async () => {
    const session = { mode: 'PROXY', downloadUrl: '/api/transfer/download/stream/token-1', fileName: 'readme.txt' };
    const calls = [];

    await transferUiHelpers.runSingleFileDownload(session, (url) => calls.push(url));

    assert.deepEqual(calls, ['/api/transfer/download/stream/token-1']);
});
