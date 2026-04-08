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

test('should define download helpers fallback in app js', () => {
    const appJs = fs.readFileSync('src/main/resources/static/js/app.js', 'utf8');
    assert.match(appJs, /const downloadHelpers = window\.FileManagerDownloads \|\| \{\}/);
});
