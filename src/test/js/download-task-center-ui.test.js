const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

test('should define download task center state in app js', () => {
    const appJs = fs.readFileSync('src/main/resources/static/js/app.js', 'utf8');
    assert.match(appJs, /const downloadTasks = ref\(\[\]\)/);
    assert.match(appJs, /const downloadCenterVisible = ref\(false\)/);
    assert.match(appJs, /const downloadCenterMinimized = ref\(false\)/);
});

test('should render download task center container in index html', () => {
    const html = fs.readFileSync('src/main/resources/static/index.html', 'utf8');
    assert.match(html, /download-task-center/);
    assert.match(html, /download-center-toggle/);
});

test('should render download task center as floating popup shell in index html', () => {
    const html = fs.readFileSync('src/main/resources/static/index.html', 'utf8');
    assert.match(html, /download-task-center__dialog/);
    assert.match(html, /download-task-center__backdrop/);
});

test('should define download helpers fallback in app js', () => {
    const appJs = fs.readFileSync('src/main/resources/static/js/app.js', 'utf8');
    assert.match(appJs, /const downloadHelpers = window\.FileManagerDownloads \|\| \{\}/);
});

test('should use large file download wording in app js', () => {
    const appJs = fs.readFileSync('src/main/resources/static/js/app.js', 'utf8');
    assert.match(appJs, /正在下载大文件/);
    assert.doesNotMatch(appJs, /正在分块下载/);
});

test('should define download task action handlers in app js', () => {
    const appJs = fs.readFileSync('src/main/resources/static/js/app.js', 'utf8');
    assert.match(appJs, /const pauseDownloadTask = async \(task\) =>/);
    assert.match(appJs, /const resumeDownloadTask = async \(task\) =>/);
    assert.match(appJs, /const cancelDownloadTask = async \(task\) =>/);
    assert.match(appJs, /const retryDownloadTask = async \(task\) =>/);
});

test('should render icon style task action buttons in index html', () => {
    const html = fs.readFileSync('src/main/resources/static/index.html', 'utf8');
    assert.match(html, /title="暂停下载"/);
    assert.match(html, /title="继续下载"/);
    assert.match(html, /title="取消下载"/);
    assert.match(html, /title="重新下载"/);
});
