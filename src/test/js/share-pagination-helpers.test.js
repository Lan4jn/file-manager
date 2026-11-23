const test = require('node:test');
const assert = require('node:assert/strict');

const helpers = require('../../main/resources/static/js/share-pagination-helpers.js');

test('should build paged request params with validOnly', () => {
    assert.deepEqual(helpers.buildPagedShareParams(2, 50, true), {
        page: 2,
        size: 50,
        validOnly: true
    });
});

test('should normalize invalid pagination params', () => {
    assert.deepEqual(helpers.buildPagedShareParams(0, 0, false), {
        page: 1,
        size: 25,
        validOnly: false
    });
});

test('should reset page to first page when filter changes', () => {
    assert.equal(helpers.resolvePageAfterFilterChange(5), 1);
});
