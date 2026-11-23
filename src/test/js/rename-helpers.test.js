const test = require('node:test');
const assert = require('node:assert/strict');

const helpers = require('../../main/resources/static/js/rename-helpers.js');

test('should reject blank rename target name', () => {
    assert.equal(helpers.validateRenameTargetName('   '), '名称不能为空');
});

test('should reject path separator in rename target name', () => {
    assert.equal(helpers.validateRenameTargetName('bad/name.txt'), '名称不能包含路径分隔符');
});

test('should build rename payload', () => {
    const payload = helpers.buildRenamePayload({
        sourcePath: 'docs/a.txt',
        targetName: 'b.txt',
        conflictPolicy: 'RENAME'
    }, 'bucket-a');

    assert.deepEqual(payload, {
        bucket: 'bucket-a',
        sourcePath: 'docs/a.txt',
        targetName: 'b.txt',
        conflictPolicy: 'RENAME'
    });
});
