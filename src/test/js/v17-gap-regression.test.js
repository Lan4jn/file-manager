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

test('should render workbench title and default-entry controls without hero panel', () => {
    assert.doesNotMatch(indexHtml, /workbench-panel--hero/);
    assert.match(indexHtml, /<div class="page-header page-header--workbench">/);
    assert.match(indexHtml, /<h3 class="heading-5">工作台<\/h3>/);
    assert.match(indexHtml, /下次登录默认显示工作台/);
});

test('should render unread badge and share content area for workbench reminders', () => {
    assert.match(indexHtml, /<el-badge\s+v-if="inboxUnreadCount > 0"/);
    assert.match(indexHtml, /class="workbench-reminder__content"/);
    assert.match(indexHtml, /{{ item\.shareContentText \|\| '分享内容待加载' }}/);
});

test('should use compact file page layout classes', () => {
    assert.match(indexHtml, /class="page-content page-content--files"/);
    assert.match(indexHtml, /class="drive-overview drive-overview--compact drive-context"/);
    assert.match(appCss, /\.page-content--files/);
    assert.match(appCss, /\.drive-overview--compact/);
});
