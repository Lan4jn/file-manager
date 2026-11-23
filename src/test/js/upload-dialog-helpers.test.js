const test = require('node:test');
const assert = require('node:assert/strict');

const helpers = require('../../main/resources/static/js/upload-dialog-helpers.js');

function createRawFile(name, size, lastModified) {
    return { name, size, lastModified };
}

test('should merge changed upload file into pending list without duplicating existing items', () => {
    const first = createRawFile('first.txt', 12, 100);
    const second = createRawFile('second.txt', 24, 200);

    const merged = helpers.mergePendingUploadFiles([
        { id: 'pending-first.txt-12-100-0', name: 'first.txt', size: 12, raw: first }
    ], [
        { uid: 'upload-second', name: 'second.txt', size: 24, raw: second }
    ]);

    assert.equal(merged.length, 2);
    assert.equal(merged[0].name, 'first.txt');
    assert.equal(merged[1].name, 'second.txt');
    assert.equal(merged[1].id, 'upload-second');
});

test('should keep pending item once when same file is selected again', () => {
    const raw = createRawFile('repeat.txt', 8, 300);

    const merged = helpers.mergePendingUploadFiles([], [
        { uid: 'upload-repeat', name: 'repeat.txt', size: 8, raw }
    ]);
    const mergedAgain = helpers.mergePendingUploadFiles(merged, [
        { uid: 'upload-repeat', name: 'repeat.txt', size: 8, raw }
    ]);

    assert.equal(mergedAgain.length, 1);
    assert.equal(mergedAgain[0].name, 'repeat.txt');
});

test('should clear finished upload tasks when upload dialog closes', () => {
    const tasks = [
        { id: 'running', status: 'running' },
        { id: 'done', status: 'completed' },
        { id: 'failed', status: 'failed' },
        { id: 'cancelled', status: 'cancelled' }
    ];

    const remaining = helpers.clearClosedUploadTasks(tasks);

    assert.deepEqual(remaining, [
        { id: 'running', status: 'running' }
    ]);
});

test('should initialize target name from original file name', () => {
    const items = helpers.normalizePendingUploadFiles([
        { uid: 'upload-a', name: 'report.pdf', size: 12, raw: createRawFile('report.pdf', 12, 100) }
    ]);

    assert.equal(items[0].targetName, 'report.pdf');
});

test('should apply batch rename rule to pending files', () => {
    const items = [
        { id: '1', name: 'a.txt', targetName: 'a.txt', raw: createRawFile('a.txt', 1, 1) },
        { id: '2', name: 'b.txt', targetName: 'b.txt', raw: createRawFile('b.txt', 1, 2) }
    ];

    const renamed = helpers.applyBatchRename(items, {
        prefix: 'new-',
        suffix: '-v2',
        findText: '.txt',
        replaceText: '.md'
    });

    assert.equal(renamed[0].targetName, 'new-a-v2.md');
    assert.equal(renamed[1].targetName, 'new-b-v2.md');
});

test('should fallback application upload when direct upload confirmation reports missing object', () => {
    assert.equal(
        helpers.shouldFallbackToApplicationUpload({ message: '确认上传失败: Object does not exist' }),
        true
    );
    assert.equal(
        helpers.shouldFallbackToApplicationUpload({ message: 'The specified key does not exist' }),
        true
    );
    assert.equal(
        helpers.shouldFallbackToApplicationUpload({ message: '对象不存在' }),
        true
    );
});

test('should not fallback application upload when upload is cancelled', () => {
    assert.equal(
        helpers.shouldFallbackToApplicationUpload({ message: '上传已取消' }),
        false
    );
});
