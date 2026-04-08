const test = require('node:test');
const assert = require('node:assert/strict');

const downloads = require('../../main/resources/static/js/download-manager.js');

test('should use chunked download when file size reaches threshold', () => {
    assert.equal(downloads.shouldUseChunkedDownload(104857600, 100), true);
    assert.equal(downloads.shouldUseChunkedDownload(104857599, 100), false);
});

test('should build inclusive range header', () => {
    assert.equal(downloads.buildRangeHeader(0, 8, 20), 'bytes=0-7');
    assert.equal(downloads.buildRangeHeader(16, 8, 20), 'bytes=16-19');
});
