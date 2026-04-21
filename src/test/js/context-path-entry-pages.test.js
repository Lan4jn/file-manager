const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const pages = [
    'src/main/resources/static/index.html',
    'src/main/resources/static/lite.html',
    'src/main/resources/static/share.html',
    'src/main/resources/static/test.html'
];

test('entry pages should not use host-root asset paths', () => {
    for (const file of pages) {
        const content = fs.readFileSync(file, 'utf8');
        assert.doesNotMatch(content, /(?:href|src)=["']\/(?:css|js|vendor|favicon)/);
        assert.doesNotMatch(content, /window\.location\.href\s*=\s*["']\/(?!\/)/);
    }
});
