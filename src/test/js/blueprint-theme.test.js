const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

const staticRoot = resolve(__dirname, '../../main/resources/static');
const pages = {
    'index.html': readFileSync(resolve(staticRoot, 'index.html'), 'utf8'),
    'share.html': readFileSync(resolve(staticRoot, 'share.html'), 'utf8'),
};

for (const [page, markup] of Object.entries(pages)) {
    test(`${page} loads the build152 theme assets without legacy blueprint assets`, () => {
        const styles = [...markup.matchAll(/<link\b(?=[^>]*\srel=(["'])stylesheet\1)[^>]*\shref=(["'])([^"']+)\2/g)]
            .map((match) => match[3]);
        const tokensIndex = styles.indexOf('./css/tokens.css?v=build152');
        const themeIndex = styles.indexOf('./css/workspace-theme.css?v=build152');
        assert.ok(tokensIndex >= 0, 'tokens.css at build152 must be loaded');
        assert.ok(themeIndex > tokensIndex, 'workspace-theme.css at build152 must load after tokens.css');
        assert.match(markup, /<script\b[^>]*\ssrc=(["'])\.\/js\/theme-helpers\.js\?v=build152\1/);
        assert.doesNotMatch(markup, /ground\.svg|workspace-blueprint\.css|\?v=build151\b/);
    });

    test(`${page} uses build152 SVG and ICO favicons`, () => {
        assert.match(markup, /<link rel="icon" type="image\/svg\+xml" href="\.\/favicon\.svg\?v=build152">\s*<link rel="alternate icon" type="image\/x-icon" href="\.\/favicon\.ico\?v=build152">/);
    });
}

test('both pages set first-paint backgrounds inline and index.html hides the app until Vue mounts', () => {
    const indexMarkup = pages['index.html'];
    assert.ok(indexMarkup.includes('<div id="app" v-cloak>'));
    assert.match(indexMarkup, /<style>[^<]*\[v-cloak\]\s*\{\s*display:\s*none\s*!important\s*;?\s*\}/);

    for (const [page, markup] of Object.entries(pages)) {
        assert.match(markup, /<style>[^<]*html\[data-theme="light"\],\s*html\[data-theme="light"\] body\s*\{\s*background-color:\s*#fafafa\s*;?\s*\}/, `${page} must set the light first-paint background inline`);
        assert.match(markup, /<style>[^<]*html\[data-theme="dark"\],\s*html\[data-theme="dark"\] body\s*\{\s*background-color:\s*#13161b\s*;?\s*\}/, `${page} must set the dark first-paint background inline`);
    }
});

test('the public verification panel has a visible heading, an associated field label and a theme toggle', () => {
    const markup = pages['share.html'];
    assert.match(markup, /id="verifyBlock"[^>]+aria-labelledby="verifyHeading"[\s\S]*?<h2 id="verifyHeading">验证提取码<\/h2>/);
    assert.match(markup, /<label for="password">提取码<\/label>/);
    assert.match(markup, /\sid="shareThemeToggle"/);
});
