const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const { createHash } = require('node:crypto');
const root = resolve(__dirname, '../../main/resources/static');
function og(html, key) {
    const m = html.match(new RegExp('<meta property="og:' + key + '" content="([^"]+)">'));
    assert.ok(m, '缺少 og:' + key);
    return m[1];
}
test('两个入口使用同一张单栏登录预览，资源校验值与元数据一致', () => {
    const { preview } = JSON.parse(readFileSync(resolve(root, 'vendor/taste/provenance.json'), 'utf8'));
    assert.notEqual(preview.file, 'login-preview.png', '需要新的缓存地址');
    const url = './vendor/taste/' + preview.file;
    const bytes = readFileSync(resolve(root, url));
    assert.equal(bytes.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
    assert.equal(createHash('sha256').update(bytes).digest('hex'), preview.sha256);
    for (const page of ['index.html', 'share.html']) {
        const html = readFileSync(resolve(root, page), 'utf8');
        assert.equal(og(html, 'image'), url);
        assert.equal(Number(og(html, 'image:width')), bytes.readUInt32BE(16));
        assert.equal(Number(og(html, 'image:height')), bytes.readUInt32BE(20));
        assert.equal(og(html, 'image:alt'), '文件管理器单栏账号登录页');
    }
});
