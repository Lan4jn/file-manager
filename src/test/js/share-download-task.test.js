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

test('should create archive task for share download all instead of calling /api/share/download-all directly', () => {
    const html = fs.readFileSync('src/main/resources/static/share.html', 'utf8');

    assert.match(html, /axios\.post\('\/api\/transfer\/archive-tasks'/);
    assert.doesNotMatch(html, /axios\.get\('\/api\/share\/download-all'/);
});

test('should poll archive task until COMPLETED\\/FAILED\\/EXPIRED on share page', () => {
    const html = fs.readFileSync('src/main/resources/static/share.html', 'utf8');

    assert.match(html, /\/api\/transfer\/archive-tasks\/' \+ taskId/);
    assert.match(html, /task\.status === 'COMPLETED'/);
    assert.match(html, /task\.status === 'FAILED'/);
    assert.match(html, /task\.status === 'EXPIRED'/);
});

test('should query share archive task with share credentials', () => {
    const html = fs.readFileSync('src/main/resources/static/share.html', 'utf8');

    assert.match(html, /axios\.get\('\/api\/transfer\/archive-tasks\/' \+ taskId,\s*\{/);
    assert.match(html, /shareCode:\s*shareCode/);
    assert.match(html, /password:\s*passwordInput\.value/);
});

test('should trigger native download for share archive completion instead of blob objectURL saving', () => {
    const html = fs.readFileSync('src/main/resources/static/share.html', 'utf8');

    assert.match(html, /TransferUiHelpers\.createNativeDownload/);
    assert.doesNotMatch(html, /FileManagerDownloads\.saveBlob/);
    assert.doesNotMatch(html, /createObjectURL/);
});

test('should render share download task action buttons', () => {
    const html = fs.readFileSync('src/main/resources/static/share.html', 'utf8');
    assert.match(html, /createTaskActionButton\('⏸', '暂停下载'/);
    assert.match(html, /createTaskActionButton\('▶', '继续下载'/);
    assert.match(html, /createTaskActionButton\('✕', '取消下载'/);
    assert.match(html, /createTaskActionButton\('↻', '重新下载'/);
});
