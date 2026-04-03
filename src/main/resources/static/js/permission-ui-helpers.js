(function (globalScope) {
    function normalizePermissions(permissionCodes) {
        if (!Array.isArray(permissionCodes)) {
            return new Set();
        }
        return new Set(permissionCodes.filter(Boolean));
    }

    function shouldLoadRolesForUserDialog(permissionCodes) {
        return normalizePermissions(permissionCodes).has('API_ROLE_MANAGE');
    }

    function canOpenRoleDialog(permissionCodes) {
        const permissions = normalizePermissions(permissionCodes);
        return permissions.has('API_ROLE_MANAGE') && permissions.has('API_PERMISSION_READ');
    }

    function shouldShowRoleBucketPermissions(permissionCodes) {
        const permissions = normalizePermissions(permissionCodes);
        return permissions.has('API_FILE_BROWSE')
            || permissions.has('API_FILE_OPERATE')
            || permissions.has('API_SHARE_MANAGE');
    }

    const exported = {
        shouldLoadRolesForUserDialog,
        canOpenRoleDialog,
        shouldShowRoleBucketPermissions
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.PermissionUiHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
