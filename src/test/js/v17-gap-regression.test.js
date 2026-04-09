const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const projectRoot = path.resolve(__dirname, '..', '..', '..');
const indexHtml = fs.readFileSync(path.join(projectRoot, 'src/main/resources/static/index.html'), 'utf8');
const appCss = fs.readFileSync(path.join(projectRoot, 'src/main/resources/static/css/app.css'), 'utf8');

test('should add preview copy button for text preview', () => {
    assert.match(indexHtml, /复制文本/);
});

test('should render dedicated file drop zone around file table', () => {
    assert.match(indexHtml, /class="file-drop-zone"/);
});

test('should not keep refresh button inside address bar prepend area', () => {
    assert.doesNotMatch(indexHtml, /path-prepend-btn/);
});

test('should render message center in chat stream layout', () => {
    assert.match(indexHtml, /message-chat-stream/);
    assert.match(indexHtml, /message-bubble/);
});

test('should remove workspace eyebrow from main page header', () => {
    assert.doesNotMatch(indexHtml, /<p class="eyebrow">Workspace<\/p>/);
});

test('should render compact title and description row in main header', () => {
    assert.match(indexHtml, /class="header-title-row"/);
    assert.match(indexHtml, /class="header-context"/);
});

test('should use compact file page layout classes', () => {
    assert.match(indexHtml, /class="page-content page-content--files"/);
    assert.match(indexHtml, /class="drive-overview drive-overview--compact"/);
    assert.match(appCss, /\.page-content--files/);
    assert.match(appCss, /\.drive-overview--compact/);
});
