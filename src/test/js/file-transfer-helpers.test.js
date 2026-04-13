const test = require('node:test');
const assert = require('node:assert/strict');

const helpers = require('../../main/resources/static/js/file-transfer-helpers.js');

test('should build single item transfer summary', () => {
    const summary = helpers.buildTransferSummary({
        operation: 'MOVE',
        sources: [{ fileName: 'readme.txt', objectName: 'docs/readme.txt' }]
    });

    assert.equal(summary, '移动 readme.txt');
});

test('should build batch transfer summary', () => {
    const summary = helpers.buildTransferSummary({
        operation: 'COPY',
        sources: [
            { fileName: 'a.txt' },
            { fileName: 'b.txt' }
        ]
    });

    assert.equal(summary, '复制已选择的 2 个项目');
});

test('should normalize root and child target folders', () => {
    assert.equal(helpers.normalizeTransferTargetFolder(''), '');
    assert.equal(helpers.normalizeTransferTargetFolder('archive'), 'archive/');
    assert.equal(helpers.normalizeTransferTargetFolder('archive/'), 'archive/');
});

test('should map conflict choice into backend conflict policy', () => {
    assert.equal(helpers.resolveConflictPolicy('overwrite'), 'OVERWRITE');
    assert.equal(helpers.resolveConflictPolicy('rename'), 'RENAME');
    assert.equal(helpers.resolveConflictPolicy('cancel'), '');
});
