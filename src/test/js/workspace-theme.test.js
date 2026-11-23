const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const root = 'src/main/resources/static/';

test('retired lightweight assets and switching actions are removed', () => {
    for (const file of ['lite.html', 'css/lite.css', 'js/lite.js']) {
        assert.equal(fs.existsSync(root + file), false, `${file} must be removed`);
    }
    assert.doesNotMatch(fs.readFileSync(root + 'index.html', 'utf8'), /openLiteMode|切换到轻量版/);
    assert.doesNotMatch(fs.readFileSync(root + 'js/app.js', 'utf8'), /openLiteMode|['"]\/lite['"]/);
});

test('official entry pages load the shared scoped theme after their base styles', () => {
    for (const [file, scope] of [['index.html', 'workspace-app'], ['share.html', 'workspace-share']]) {
        const html = fs.readFileSync(root + file, 'utf8');
        assert.match(html, /href="\.\/css\/workspace-theme\.css(?:\?[^\"]*)?"/);
        assert.match(html, new RegExp('<body[^>]*class="[^\"]*' + scope));
        assert.ok(html.indexOf('workspace-theme.css') > Math.max(html.lastIndexOf('</style>'), html.indexOf('./css/app.css')));
    }
});

test('workspace theme provides neutral surfaces, focus and narrow screen support', () => {
    const file = root + 'css/workspace-theme.css';
    assert.ok(fs.existsSync(file), 'shared workspace theme must exist');
    const css = fs.readFileSync(file, 'utf8');
    assert.match(css, /\.workspace-app/);
    assert.match(css, /\.workspace-share/);
    for (const color of ['#dfdedb', '#d3d2ce', '#26251e', '#5a5850', '#2456d6', '#ffffff']) assert.ok(css.toLowerCase().includes(color), color);
    for (const style of ['heading-5','title-3','title-4','body-6','button-sm','caption-5']) assert.ok(css.includes(style), style);
    assert.ok(css.includes('../fonts/host-grotesk-latin-variable.woff2'));
    assert.match(css, /:focus-visible/);
    assert.match(css, /prefers-reduced-motion/);
    assert.match(css, /@media[^\{]*max-width:\s*700px/);
    assert.doesNotMatch(css, /(?:linear|radial)-gradient\s*\(|backdrop-filter:\s*blur/);
});

test('legacy message, version and diagnostic surfaces explicitly receive the flat theme', () => {
    const css=fs.readFileSync(root+'css/workspace-theme.css','utf8');
    for(const selector of ['.message-bubble--unread::before','.version-item.active','.dialog-panel--sidebar','.diagnostics-panel__toolbar']) assert.ok(css.includes(selector), selector);
    assert.ok(css.includes('-webkit-backdrop-filter: none'));
});

test('Taste palette, dark navigation and local mono font form one application system', () => {
    const css=fs.readFileSync(root+'css/workspace-theme.css','utf8');
    for(const token of ['--p-bg','--p-surface','--p-ink','--p-muted','--p-accent','--font-display','--font-text','--font-mono']) assert.ok(css.includes(token), token);
    assert.ok(css.includes('jetbrains-mono-latin-variable.woff2'));
    assert.ok(css.includes('.login-visual__stack'));
    assert.ok(css.includes('.workspace-page-icon'));
});
