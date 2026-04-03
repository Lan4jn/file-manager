const test = require('node:test');
const assert = require('node:assert/strict');

const {
    shouldLoadRolesForUserDialog,
    canOpenRoleDialog,
    shouldShowRoleBucketPermissions
} = require('../../main/resources/static/js/permission-ui-helpers.js');

test('should load role list for user dialog only when role manage permission exists', () => {
    assert.equal(shouldLoadRolesForUserDialog(['API_USER_MANAGE']), false);
    assert.equal(shouldLoadRolesForUserDialog(['API_USER_MANAGE', 'API_ROLE_MANAGE']), true);
});

test('should require both role manage and permission read before opening role dialog', () => {
    assert.equal(canOpenRoleDialog(['API_ROLE_MANAGE']), false);
    assert.equal(canOpenRoleDialog(['API_PERMISSION_READ']), false);
    assert.equal(canOpenRoleDialog(['API_ROLE_MANAGE', 'API_PERMISSION_READ']), true);
});

test('should show role bucket permissions only for file or share related api permissions', () => {
    assert.equal(shouldShowRoleBucketPermissions(['API_BUCKET_MANAGE']), false);
    assert.equal(shouldShowRoleBucketPermissions(['API_FILE_BROWSE']), true);
    assert.equal(shouldShowRoleBucketPermissions(['API_FILE_OPERATE']), true);
    assert.equal(shouldShowRoleBucketPermissions(['API_SHARE_MANAGE']), true);
});
