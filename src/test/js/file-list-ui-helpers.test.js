const test = require('node:test');
const assert = require('node:assert/strict');

const {
    buildFileSyncKey,
    isFileSyncing,
    canOperateFile,
    applySyncingState,
    resolveFileSortState
} = require('../../main/resources/static/js/file-list-ui-helpers.js');

test('should build stable syncing key from bucket and object name', () => {
    assert.equal(buildFileSyncKey('bucket-a', 'docs/a.txt'), 'bucket-a::docs/a.txt');
});

test('should mark file as syncing and disable operations', () => {
    const syncingKeys = new Set([buildFileSyncKey('bucket-a', 'docs/a.txt')]);
    const file = { bucket: 'bucket-a', objectName: 'docs/a.txt' };

    assert.equal(isFileSyncing(file, syncingKeys), true);
    assert.equal(canOperateFile(file, syncingKeys), false);
});

test('should apply syncing state onto file list rows', () => {
    const syncingKeys = new Set([buildFileSyncKey('bucket-a', 'docs/a.txt')]);
    const rows = applySyncingState([
        { bucket: 'bucket-a', objectName: 'docs/a.txt', fileName: 'a.txt' },
        { bucket: 'bucket-a', objectName: 'docs/b.txt', fileName: 'b.txt' }
    ], syncingKeys);

    assert.equal(rows[0].syncing, true);
    assert.equal(rows[1].syncing, false);
});

test('should switch to explicit sort mode when sort arrow is clicked', () => {
    assert.deepEqual(resolveFileSortState({ prop: 'uploadTime', order: 'descending' }), {
        sortBy: 'time',
        order: 'desc',
        folderFirst: false,
        explicitSort: true
    });
});

test('should restore default folder-first mode when sorting is cleared', () => {
    assert.deepEqual(resolveFileSortState({ prop: null, order: null }), {
        sortBy: 'name',
        order: 'asc',
        folderFirst: true,
        explicitSort: false
    });
});
