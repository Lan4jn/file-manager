const test = require('node:test');
const assert = require('node:assert/strict');

const {
    normalizeContextPath,
    joinContextPath,
    isContextPathUrlMatch
} = require('../../main/resources/static/js/context-path-helpers.js');
const transferUiHelpers = require('../../main/resources/static/js/transfer-ui-helpers.js');

test('normalizeContextPath should keep root path empty for browser joins', () => {
    assert.equal(normalizeContextPath('/'), '');
    assert.equal(normalizeContextPath(''), '');
    assert.equal(normalizeContextPath('/fm/'), '/fm');
});

test('joinContextPath should prepend non-root context path', () => {
    assert.equal(joinContextPath('', '/api/file/list'), '/api/file/list');
    assert.equal(joinContextPath('/fm', '/api/file/list'), '/fm/api/file/list');
    assert.equal(joinContextPath('/fm/', 'full'), '/fm/full');
});

test('isContextPathUrlMatch should match prefixed application urls', () => {
    assert.equal(isContextPathUrlMatch('/fm', '/fm/api/file/download', '/api/file/download'), true);
    assert.equal(isContextPathUrlMatch('/fm', '/api/file/download', '/api/file/download'), false);
    assert.equal(isContextPathUrlMatch('', '/api/file/download', '/api/file/download'), true);
});

test('transfer download matcher should accept context-prefixed file download url', () => {
    const matcher = transferUiHelpers.createAppDownloadMatcher('/fm');
    assert.equal(matcher('https://example.com/fm/api/file/download?objectName=a'), true);
    assert.equal(matcher('https://example.com/api/file/download?objectName=a'), false);
});
