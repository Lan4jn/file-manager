const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const { readFileSync, existsSync } = require('node:fs');
const { resolve } = require('node:path');
const { test } = require('node:test');

const staticRoot = resolve(__dirname, '../../main/resources/static');
const share = readFileSync(resolve(staticRoot, 'share.html'));
const themePath = resolve(staticRoot, 'css/workspace-blueprint.css');

test('public share loads the cache-keyed blueprint after the compatibility theme', () => {
    const links = [...share.toString('utf8').matchAll(/<link\b[^>]*\bhref="([^"]+)"/g)].map((match) => match[1]);
    const index = links.indexOf('./css/workspace-theme.css?v=build143');
    assert.ok(index >= 0, 'share must load the compatibility theme at build143');
    assert.equal(links[index + 1], './css/workspace-blueprint.css?v=build143');
    assert.ok(existsSync(themePath), 'linked blueprint CSS must be packaged');
});

test('app and share can resolve the bundled blueprint from a context-relative CSS URL', () => {
    assert.ok(existsSync(themePath), 'blueprint stylesheet is missing');
    const theme = readFileSync(themePath, 'utf8');
    const image = theme.match(/url\(['"]?(\.\.\/vendor\/workspace-blueprint\/ground\.svg)['"]?\)/)?.[1];
    assert.ok(image, 'diagram URL must resolve relative to the stylesheet');
    const asset = readFileSync(resolve(staticRoot, 'css', image), 'utf8');
    assert.match(asset, /<svg\b[^>]*\bviewBox=/);
});

test('share keeps the reviewed seven script elements byte-for-byte', () => {
    const scripts = [...share.toString('latin1').matchAll(/<script\b[^>]*>[\s\S]*?<\/script>/gi)];
    // Git can check out the whole HTML in either EOL convention. Hash the raw bytes.
    const businessScriptHash = share.includes(Buffer.from('\r\n'))
        ? '5da26672714624bf7c1bcbdde45be160ad87a97b371a27ff5486672a1be5edfe'
        : '027aa75ba527e908d0b10073be358be7241ac1fda8be1a70147ca89b08d9a2d3';
    assert.deepEqual(scripts.map(([blob]) => createHash('sha256').update(blob, 'latin1').digest('hex')), [
        '20b7905956d315a48334c8f7d4d14be10b1a3bff4130bd99cd25a3a8cd6b855e',
        '67d8e8523f70a0e339dc46102b52216aa09e19f98edcac670c4c5c47e24acb14',
        'a8e761a91038c11223dbac3c94249c776b383267600b8a5ecde072fb87185a6d',
        'b6e662c1a0a4b3b2e973423e597ce3b75ca6c2d0dcb7d0f63a810671ab87edbb',
        '9ae373c125dc9c95f61c91cec1958394b3bb849dbe46928145cf7c0454d4c93d',
        businessScriptHash,
        '6d1c4d411182dd16e76ea19d6c5c494c0c1dfc910d2b4b0f790edf7ccd050319',
    ]);
});

test('the public verification panel has a visible heading and an associated field label', () => {
    const markup = share.toString('utf8');
    assert.match(markup, /id="verifyBlock"[^>]+aria-labelledby="verifyHeading"[\s\S]*?<h2 id="verifyHeading">验证提取码<\/h2>/);
    assert.match(markup, /<label for="password">提取码<\/label>/);
});
