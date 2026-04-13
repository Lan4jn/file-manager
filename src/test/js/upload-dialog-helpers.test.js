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
