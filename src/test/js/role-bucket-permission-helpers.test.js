const test = require('node:test');
const assert = require('node:assert/strict');

const {
    mergeRoleBucketPermissionRows,
    shouldShowAllBucketsHint
} = require('../../main/resources/static/js/role-bucket-permission-helpers.js');

test('should merge duplicate bucket rows and focus existing row', () => {
    const rows = [
        { bucketName: 'bucket-a', actions: ['DOWNLOAD'], enabled: true },
        { bucketName: 'bucket-a', actions: ['UPLOAD'], enabled: true }
    ];

    const result = mergeRoleBucketPermissionRows(rows, 1);

    assert.equal(result.merged, true);
    assert.equal(result.mergedIntoIndex, 0);
    assert.deepEqual(result.rows, [
        { bucketName: 'bucket-a', actions: ['DOWNLOAD', 'UPLOAD'], enabled: true }
    ]);
});

test('should treat all buckets row as visible-all-buckets state', () => {
    assert.equal(shouldShowAllBucketsHint('*'), true);
    assert.equal(shouldShowAllBucketsHint('bucket-a'), false);
});
