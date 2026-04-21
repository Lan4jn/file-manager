const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

test('bucket page should expose refresh stats controls and status bindings', () => {
    const html = fs.readFileSync('src/main/resources/static/index.html', 'utf8');
    const appJs = fs.readFileSync('src/main/resources/static/js/app.js', 'utf8');

    assert.match(html, /刷新桶统计/);
    assert.match(appJs, /\/file\/buckets\/stats\/refresh/);
    assert.match(appJs, /\/file\/buckets\/stats\/status/);
    assert.match(appJs, /statsStatus/);
});
