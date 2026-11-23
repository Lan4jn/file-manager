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

test('should expose icon and label metadata for every configurable row action', () => {
    const expected = {
        DOWNLOAD: { label: '下载', icon: 'Download' },
        SHARE: { label: '分享', icon: 'Share' },
        PREVIEW: { label: '预览', icon: 'View' },
        RENAME: { label: '重命名', icon: 'Edit' },
        DELETE: { label: '删除', icon: 'Delete' },
        MOVE: { label: '移动', icon: 'Rank' },
        COPY: { label: '复制', icon: 'DocumentCopy' }
    };

    Object.entries(expected).forEach(([code, meta]) => {
        assert.deepEqual(helpers.getRowActionMeta(code), {
            code,
            label: meta.label,
            icon: meta.icon
        });
    });
});
