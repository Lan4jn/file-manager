const test = require('node:test');
const assert = require('node:assert/strict');

const {
    buildApiUrl,
    buildShareUrl
} = require('../../main/resources/static/js/url-helpers.js');

test('should encode object name in api url query string', () => {
    const url = buildApiUrl('http://localhost:9080', '/api/file/download', {
        bucket: 'bucket-a',
        objectName: '目录/a #1.txt'
    });

    assert.equal(
        url,
        'http://localhost:9080/api/file/download?bucket=bucket-a&objectName=%E7%9B%AE%E5%BD%95%2Fa+%231.txt'
    );
});

test('should append password to share url when provided', () => {
    const url = buildShareUrl('http://localhost:9080', { shareCode: 'ABCD1234', hasPassword: true, password: '12 3#' }, true);

    assert.equal(
        url,
        'http://localhost:9080/share/ABCD1234?password=12+3%23'
    );
});

test('should build share url without password by default', () => {
    const url = buildShareUrl('http://localhost:9080', { shareCode: 'ABCD1234', hasPassword: true, password: '2468' }, false);

    assert.equal(url, 'http://localhost:9080/share/ABCD1234');
});
