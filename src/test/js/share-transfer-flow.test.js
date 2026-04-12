const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

test('should wire share single download through transfer session in share html', () => {
    const html = fs.readFileSync('src/main/resources/static/share.html', 'utf8');

    assert.match(html, /\/api\/transfer\/download\/session/);
    assert.match(html, /scene:\s*'SHARE_FILE'/);
    assert.match(html, /scene:\s*'SHARE_ITEM'/);
    assert.match(html, /TransferUiHelpers\.runSingleFileDownload/);
});

test('should load transfer ui helpers on share page', () => {
    const html = fs.readFileSync('src/main/resources/static/share.html', 'utf8');

    assert.match(html, /<script src="\/js\/transfer-ui-helpers\.js"><\/script>/);
});
