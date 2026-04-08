const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

test('should render share page download task entry', () => {
    const html = fs.readFileSync('src/main/resources/static/share.html', 'utf8');
    assert.match(html, /share-download-task-center/);
    assert.match(html, /share-download-toggle/);
});

test('should use dynamic download threshold on share page instead of hard coded 100MB', () => {
    const html = fs.readFileSync('src/main/resources/static/share.html', 'utf8');
    assert.match(html, /var downloadThresholdMB = 100/);
    assert.doesNotMatch(html, /shouldUseChunkedDownload\(file\.size \|\| 0, 100\)/);
    assert.doesNotMatch(html, /shouldUseChunkedDownload\(item\.size \|\| 0, 100\)/);
});
