const test = require('node:test');
const assert = require('node:assert/strict');

const downloads = require('../../main/resources/static/js/download-manager.js');

test('should define batch download task steps', () => {
    assert.deepEqual(downloads.BATCH_DOWNLOAD_STEPS, [
        '校验文件',
        '请求服务端打包',
        '接收压缩包',
        '保存文件'
    ]);
});
