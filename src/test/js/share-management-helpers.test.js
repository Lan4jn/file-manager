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

test('should format share item loading error with backend reason', () => {
    assert.equal(
        helpers.formatShareItemsErrorMessage({ response: { data: { message: '分享已取消' } } }),
        '加载失败：分享已取消'
    );
});

test('should format share item loading error with fallback reason', () => {
    assert.equal(
        helpers.formatShareItemsErrorMessage(null),
        '加载失败：分享内容加载失败，请稍后重试'
    );
});
