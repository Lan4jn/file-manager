const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

test('should wire file single download through transfer session in app js', () => {
    const appJs = fs.readFileSync('src/main/resources/static/js/app.js', 'utf8');

    assert.match(appJs, /transferUiHelpers/);
    assert.match(appJs, /resolveDownloadSessionUrl/);
    assert.match(appJs, /scene:\s*'FILE'/);
    assert.match(appJs, /api\.post\(transferUiHelpers\.resolveDownloadSessionUrl/);
    assert.match(appJs, /transferUiHelpers\.runSingleFileDownload/);
});

test('should not duplicate api prefix for file transfer session request', () => {
    const appJs = fs.readFileSync('src/main/resources/static/js/app.js', 'utf8');

    assert.match(appJs, /const api = axios\.create\(\{\s*baseURL: '\/api'/s);
    assert.match(appJs, /resolveDownloadSessionUrl:\s*\(\)\s*=>\s*'\/transfer\/download\/session'/);
    assert.doesNotMatch(appJs, /resolveDownloadSessionUrl:\s*\(\)\s*=>\s*'\/api\/transfer\/download\/session'/);
});

test('should load transfer ui helpers before app js in index html', () => {
    const html = fs.readFileSync('src/main/resources/static/index.html', 'utf8');

    assert.match(html, /<script src="\/js\/transfer-ui-helpers\.js"><\/script>/);
});
