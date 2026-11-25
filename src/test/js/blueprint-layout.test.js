const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

const root = resolve(__dirname, '../../main/resources/static');
const html = readFileSync(resolve(root, 'index.html'), 'utf8');
const css = readFileSync(resolve(root, 'css/workspace-blueprint-layout.css'), 'utf8');
const files = html.slice(html.indexOf('<!-- File Manager -->'), html.indexOf('<!-- Shares -->'));
const buckets = html.slice(html.indexOf('<!-- Bucket Management -->'), html.indexOf('<!-- File Manager -->'));

test('登录页只展示登录面板，并保留密码和证书登录入口', () => {
    const login = html.slice(html.indexOf('<!-- Login Page -->'), html.indexOf('<!-- Main App -->'));
    assert.ok(!login.includes('<aside'), '登录表单旁不应另有说明栏');
    assert.ok(login.includes('@submit.prevent="handleLogin"'));
    assert.ok(login.includes('@click="handleCertLogin"'));
    assert.ok(login.includes('@click="toggleTheme"'));
    assert.doesNotMatch(html, /team-documents|验收材料|交付文件/);
});

test('工作台最近与收藏同主栏，提醒独立为副栏', () => {
    assert.match(html, /class="workbench-directories"\s*>\s*<section\b[^>]*class="workbench-panel workbench-panel--recent"[\s\S]*?workbenchOverview\.recentDirectories[\s\S]*?<\/section>\s*<section\b[^>]*class="workbench-panel workbench-panel--favorites"[\s\S]*?workbenchOverview\.favoriteDirectories[\s\S]*?<\/section>\s*<\/div>\s*<section class="workbench-panel workbench-reminders"/);
});

test('最近与收藏使用独立卡片，收藏有独立强调线', () => {
    assert.match(css, /\.workbench-directories\s*\{[^}]*display:\s*flex\b[^}]*flex-direction:\s*column\b[^}]*gap:\s*20px\b/);
    assert.match(css, /\.workbench-directories\s*\{[^}]*background:\s*transparent\b/);
    const cards = css.match(/\.workbench-directories\s*>\s*\.workbench-panel\s*\{([^}]*)\}/)?.[1] || '';
    assert.match(cards, /border:\s*1px solid var\(--border-default\)/);
    assert.match(cards, /border-radius:\s*var\(--radius-lg\)/);
    assert.match(cards, /background:\s*var\(--surface-panel\)/);
    assert.match(cards, /box-shadow:\s*var\(--shadow-sm\)/);
    assert.match(css, /\.workbench-panel--favorites\s*\{[^}]*border-top:\s*2px solid var\(--workbench-fav-accent\)/);
    assert.doesNotMatch(css, /\.workbench-panel\s*\+\s*\.workbench-panel/);
});

test('最近目录只在 1024px 起使用双列，收藏目录仍为整行清单', () => {
    assert.match(html, /class="workbench-directory-list workbench-directory-list--recent"\s*>[\s\S]*?v-for="item in workbenchOverview\.recentDirectories"/);
    assert.match(html, /class="workbench-directory-list workbench-directory-list--favorites"\s*>[\s\S]*?v-for="item in workbenchOverview\.favoriteDirectories"/);
    assert.match(css, /@media\s*\(\s*min-width\s*:\s*1024px\s*\)\s*\{\s*[^{}]*\.workbench-directory-list--recent\s*\{[^}]*display:\s*grid\b[^}]*grid-template-columns:\s*repeat\(2,\s*minmax\(0,\s*1fr\)\)/);
    assert.match(css, /\.workbench-directory-list--favorites\s*\{[^}]*display:\s*flex\b[^}]*flex-direction:\s*column\b/);
});

test('桌面桶列固定宽度采用批准排布，名称保持弹性', () => {
    const cols = [...buckets.matchAll(/<el-table-column\b[^>]*>/g)].map(([tag]) => /\swidth="(\d+)"/.exec(tag)?.[1]);
    assert.deepEqual(cols, [undefined, '162', '90', '116', '162', '184']);
});

test('更多控制导航与次要动作，真实命令只绑定一次', () => {
    assert.ok(/aria-controls="file-toolbar-overflow"[\s\S]*id="file-toolbar-overflow"[\s\S]*class="nav-buttons"[\s\S]*class="action-buttons"/.test(files), '没有复用导航与操作的更多面板');
    for (const click of ['goBack', 'goForward', 'goUp', 'refreshFiles', 'openUploadDialog', 'showFolderDialog = true', 'toggleCurrentDirectoryFavorite', 'batchShare', 'batchDownload', 'batchMove', 'batchCopy', 'batchDelete']) {
        assert.equal(files.split(`@click="${click}"`).length - 1, 1, click);
    }
    assert.match(files, /:disabled="!canGoBack"/);
    assert.match(files, /:disabled="!canGoForward"/);
    assert.match(files, /ref="pathEditInputRef"[\s\S]*@keydown\.enter="confirmPathEdit"[\s\S]*@keydown\.esc="cancelPathEdit"[\s\S]*@blur="confirmPathEdit"/);
});

test('窄屏文件行在同一行卡保留名称、元数据和操作', () => {
    assert.ok(/class="[^"\n]*blueprint-file-table/.test(files), '缺少独立文件列表布局');
    assert.match(css, /grid-template-areas:\s*"select name ops"\s*"\. size ops"\s*"\. date ops"/);
    assert.match(html, /tokens\.css\?v=build152[\s\S]*workspace-theme\.css\?v=build152[\s\S]*workspace-blueprint-layout\.css\?v=build152/);
});

test('更多从工具栏外收到 Escape、点击后关闭，离开文件页后解绑', () => {
    assert.ok(html.includes('workspace-file-toolbar.js'), '未加载文件工具栏的关闭控制');
    const win = new EventTarget();
    const vm = require('node:vm');
    vm.runInNewContext(readFileSync(resolve(root, 'js/workspace-file-toolbar.js'), 'utf8'), { window: win, AbortController });
    let focusCount = 0;
    const summary = { focus: () => { focusCount++; } };
    const details = { open: true, firstElementChild: summary, getClientRects: () => [1] };
    const panel = new EventTarget();
    const toolbar = {
        ownerDocument: { defaultView: win },
        querySelector: (selector) => selector === '.file-toolbar-more' ? details : panel,
        contains: (target) => target === summary,
    };
    const send = (node, type, data) => node.dispatchEvent(Object.assign(new Event(type, { cancelable: true }), data));
    win.WorkspaceFileToolbar.mount(toolbar);
    assert.equal(send(win, 'keydown', { key: 'Escape' }), false);
    assert.equal(details.open, false);
    assert.equal(focusCount, 1);
    details.open = true;
    win.matches = (selector) => selector === '.path-edit-input-native';
    assert.equal(send(win, 'keydown', { key: 'Escape' }), true, '地址编辑的 Escape 必须继续传给原绑定');
    assert.equal(details.open, false);
    assert.equal(focusCount, 1);
    win.matches = () => false;
    details.open = true;
    assert.equal(send(win, 'pointerdown'), true, '外部点击不能被吞掉');
    assert.equal(details.open, false);
    assert.equal(focusCount, 1, '外部点击不抢焦');
    details.open = true;
    panel.closest = () => null;
    send(panel, 'click');
    assert.equal(details.open, true);
    panel.closest = () => ({});
    send(panel, 'click');
    assert.equal(details.open, false);
    assert.equal(focusCount, 2);
    win.WorkspaceFileToolbar.unmount(toolbar);
    details.open = true;
    send(win, 'keydown', { key: 'Escape' });
    assert.equal(details.open, true, '卸载后不再拦截全局事件');
});

test('提醒副栏不继承旧模板的整行跨列', () => {
    assert.match(css, /\.workbench-grid\s+\.workbench-reminders\s*\{[^}]*grid-column:\s*auto/);
});

test('文件大小与日期单独使用不换行的数值样式', () => {
    for (const prop of ['size', 'uploadTime']) {
        assert.match(files, new RegExp(`prop="${prop}"[^>]*class-name="file-row-number"`));
    }
    assert.match(css, /\.file-row-number\s+\.cell\s*\{[^}]*white-space:\s*nowrap/);
});

test('布局样式包含 1920px 宽屏规则', () => {
    assert.match(css, /@media\s*\(\s*min-width\s*:\s*1920px\s*\)\s*\{/);
});
