const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

test('should define frontend download threshold in app config defaults', () => {
    const appJs = fs.readFileSync('src/main/resources/static/js/app.js', 'utf8');
    assert.match(appJs, /frontendDownloadThresholdMB:\s*100/);
});
