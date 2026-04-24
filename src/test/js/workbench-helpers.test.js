const test = require('node:test');
const assert = require('node:assert/strict');

const helpers = require('../../main/resources/static/js/workbench-helpers.js');

test('should normalize empty overview payload', () => {
    const overview = helpers.normalizeOverview(null);

    assert.equal(overview.defaultWorkbench, false);
    assert.deepEqual(overview.recentDirectories, []);
    assert.deepEqual(overview.favoriteDirectories, []);
    assert.deepEqual(overview.shareReminders, []);
});

test('should resolve workbench as initial menu when preference is enabled', () => {
    const menu = helpers.resolveInitialMenu('buckets', { defaultWorkbench: true }, () => true);

    assert.equal(menu, 'workbench');
});

test('should keep current menu when workbench preference is disabled', () => {
    const menu = helpers.resolveInitialMenu('files', { defaultWorkbench: false }, () => true);

    assert.equal(menu, 'files');
});

test('should fall back when workbench is not accessible', () => {
    const menu = helpers.resolveInitialMenu('buckets', { defaultWorkbench: true }, () => false);

    assert.equal(menu, 'buckets');
});

test('should detect whether workbench has content', () => {
    assert.equal(helpers.hasWorkbenchContent({ recentDirectories: [] }), false);
    assert.equal(helpers.hasWorkbenchContent({ favoriteDirectories: [{ directoryPath: 'docs/' }] }), true);
});
