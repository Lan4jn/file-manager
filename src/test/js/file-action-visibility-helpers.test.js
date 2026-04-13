const test = require('node:test');
const assert = require('node:assert/strict');

const helpers = require('../../main/resources/static/js/file-action-visibility-helpers.js');

test('should collapse configured hidden row actions into more menu', () => {
    const result = helpers.splitRowActions([
        { code: 'DOWNLOAD', visible: true },
        { code: 'SHARE', visible: true },
        { code: 'PREVIEW', visible: true },
        { code: 'DELETE', visible: true },
        { code: 'MOVE', visible: true },
        { code: 'COPY', visible: true }
    ], ['DELETE', 'MOVE', 'COPY']);

    assert.deepEqual(result.primary.map(item => item.code), ['DOWNLOAD', 'SHARE', 'PREVIEW']);
    assert.deepEqual(result.more.map(item => item.code), ['DELETE', 'MOVE', 'COPY']);
});

test('should omit actions without permission from both primary and more lists', () => {
    const result = helpers.splitRowActions([
        { code: 'DOWNLOAD', visible: true },
        { code: 'DELETE', visible: false },
        { code: 'MOVE', visible: false }
    ], ['DELETE', 'MOVE']);

    assert.deepEqual(result.primary.map(item => item.code), ['DOWNLOAD']);
    assert.deepEqual(result.more, []);
});

test('should not show more menu when no collapsed actions remain', () => {
    const result = helpers.splitRowActions([
        { code: 'DOWNLOAD', visible: true },
        { code: 'PREVIEW', visible: true }
    ], ['DELETE', 'MOVE', 'COPY']);

    assert.equal(result.hasMore, false);
});
