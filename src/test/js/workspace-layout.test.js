const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const root = 'src/main/resources/static/';
const html = fs.readFileSync(root + 'index.html', 'utf8');
const app = fs.readFileSync(root + 'js/app.js', 'utf8');

test('workspace has semantic navigation, skip link and a responsive drawer', () => {
    assert.ok(/<main[^>]+id="main-content"/.test(html));
    assert.ok(/<aside[^>]+id="workspace-navigation"/.test(html));
    assert.ok(html.includes('href="#main-content"'));
    assert.ok(html.includes('aria-controls="workspace-navigation"'));
    assert.ok(html.includes(':aria-expanded="navigationOpen"'));
    assert.ok(html.includes('workspace-nav-backdrop'));
    assert.ok(app.includes('const navigationOpen = ref(false)'));
    assert.ok(app.includes('const toggleNavigation ='));
    assert.ok(app.includes('const closeNavigation ='));
});

test('task content replaces promotional cards without removing functional controls', () => {
    assert.ok(!html.includes('hero-grid'));
    assert.ok(html.includes('drive-context'));
    for (const binding of ['@submit.prevent="handleLogin"', 'v-model="loginForm.username"', 'v-model="loginForm.password"', '@click="openUploadDialog"', '@click="batchShare"', '@click="batchDownload"', '@click="batchDelete"', '@select="handleMenuSelect"', 'v-model="shareForm.targetType"', 'v-model="showDiagnosticsDialog"']) assert.ok(html.includes(binding), binding);
});

test('interface fonts and Phosphor icons are local, licensed and loaded', () => {
    const font = root + 'fonts/geist-latin-variable.woff2';
    assert.ok(fs.existsSync(font), 'local Geist font');
    assert.equal(fs.readFileSync(font).subarray(0,4).toString(), 'wOF2');
    assert.ok(fs.existsSync(root + 'fonts/OFL.txt'));
    assert.ok(fs.existsSync(root + 'vendor/phosphor-LICENSE.txt'));
    assert.ok(html.includes('./js/workspace-icons.js'));
    assert.ok(fs.readFileSync(root + 'js/workspace-icons.js','utf8').includes('Phosphor'));
});

test('Phosphor registry covers all dynamic file actions and keyboard names are retained', () => {
    const vm = require('node:vm');
    const sandbox = {window: {Vue: {}}};
    vm.runInNewContext(fs.readFileSync(root+'js/workspace-icons.js','utf8'), sandbox);
    const registry = sandbox.window.ElementPlusIconsVue;
    const actionSource=fs.readFileSync(root+'js/file-action-visibility-helpers.js','utf8');
    for(const [,name] of actionSource.matchAll(/icon: '([^']+)'/g)) assert.ok(registry[name], name);
    assert.ok(html.includes(':aria-label="action.label"'));
    assert.ok(html.includes("<dots-three-vertical"));
    assert.ok(html.includes("<chat-circle"));
    assert.ok(html.includes('min-width="240"'));
    assert.ok(html.includes('@keydown.enter.prevent="handleFileNameClick(row)"'));
});

test('Taste presentation covers entry pages and all management views without editing business JS', () => {
    const share=fs.readFileSync(root+'share.html','utf8');
    for(const doc of [html,share]) {
        assert.ok(doc.includes('data-type="software"'));
        assert.ok(doc.includes('name="site-kind" content="software"'));
        assert.ok(doc.includes('vendor/taste/motion/motion.css'));
        assert.ok(doc.includes('workspace-motion.js'));
    }
    assert.ok(html.includes('class="login-content"'));
    assert.ok(html.includes('class="login-path-guide"'));
    assert.ok(html.includes(':data-page="currentMenu"'));
    for(const page of ['buckets','shares','audit','users','roles','keys','settings']) assert.ok(html.includes('page-content--'+page),page);
    for(const file of ['host-grotesk','jetbrains-mono']) assert.equal(fs.readFileSync(root+'fonts/'+file+'-latin-variable.woff2').subarray(0,4).toString(),'wOF2');
});

test('Chinese Select placeholders do not leak translation keys into file and paging controls', () => {
    assert.ok(app.includes('...window.ElementPlusLocaleZhCn?.el'));
    assert.ok(html.indexOf('./vendor/element-plus/zh-cn.js') < html.indexOf('./js/app.js'));
    const sandbox={};require('node:vm').runInNewContext(fs.readFileSync(root+'vendor/element-plus/zh-cn.js','utf8'),sandbox);
    assert.equal(sandbox.ElementPlusLocaleZhCn.el.table.emptyText,'暂无数据');
    assert.equal(sandbox.ElementPlusLocaleZhCn.el.select.placeholder,'请选择');
});
