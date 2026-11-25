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

test('workspace theme and shared tokens provide a flat, accessible design system', () => {
    const tokens = fs.readFileSync(root + 'css/tokens.css', 'utf8');
    const theme = fs.readFileSync(root + 'css/workspace-theme.css', 'utf8');

    assert.match(tokens, /html\[data-theme="dark"\]/);
    for (const token of ['--surface-canvas', '--surface-rail', '--surface-panel', '--accent', '--font-display', '--font-text', '--font-mono']) {
        assert.match(tokens, new RegExp(`^\\s*${token}\\s*:`, 'm'), `missing token declaration: ${token}`);
    }
    for (const font of ['geist-latin-variable.woff2', 'host-grotesk-latin-variable.woff2', 'jetbrains-mono-latin-variable.woff2']) {
        assert.ok(tokens.includes(`../fonts/${font}`), `missing local font URL: ${font}`);
    }

    assert.match(tokens, /--workspace-bg-image\s*:\s*url\(\s*(['"]?)\.\.\/img\/workspace-bg-light\.jpg\1\s*\)/);
    assert.match(tokens, /--workspace-bg-image\s*:\s*url\(\s*(['"]?)\.\.\/img\/workspace-bg-dark\.jpg\1\s*\)/);
    for (const image of ['workspace-bg-light.jpg', 'workspace-bg-dark.jpg']) {
        assert.equal(fs.existsSync(root + `img/${image}`), true, `missing workspace background image: ${image}`);
    }

    assert.match(theme, /:focus-visible/);
    assert.match(tokens, /@media\s*\(\s*prefers-reduced-motion\s*:\s*reduce\s*\)/);
    assert.match(theme, /@media\s*\(\s*max-width\s*:\s*700px\s*\)/);
    assert.match(theme, /\.page-content(?![\w-])[^{}]*\{(?:[^{};]*;)*\s*max-width\s*:\s*none\s*(?:!important\s*)?[;}]/);
    assert.match(theme, /@media\s*\(\s*min-width\s*:\s*1920px\s*\)/);

    for (const [file, css] of [['tokens.css', tokens], ['workspace-theme.css', theme]]) {
        assert.doesNotMatch(css, /backdrop-filter\s*:[^;{}]*\bblur\s*\(|(?:linear|radial)-gradient\s*\(/i, `${file} must not use backdrop blur or gradients`);
    }

    for (const selector of ['.workspace-app', '.workspace-share', '.heading-5', '.workspace-page-icon', '.message-bubble--unread::before', '.version-item.active', '.dialog-panel--sidebar', '.diagnostics-panel__toolbar']) {
        assert.ok(theme.includes(selector), `missing workspace selector: ${selector}`);
    }
    assert.match(theme, /-webkit-backdrop-filter\s*:\s*none\s*;/);
});
