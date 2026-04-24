(function (globalScope) {
    function normalizeOverview(overview) {
        var payload = overview || {};
        return {
            defaultWorkbench: !!payload.defaultWorkbench,
            recentDirectories: Array.isArray(payload.recentDirectories) ? payload.recentDirectories : [],
            favoriteDirectories: Array.isArray(payload.favoriteDirectories) ? payload.favoriteDirectories : [],
            shareReminders: Array.isArray(payload.shareReminders) ? payload.shareReminders : []
        };
    }

    function resolveInitialMenu(currentMenu, overview, canAccessMenu) {
        var normalizedOverview = normalizeOverview(overview);
        if (normalizedOverview.defaultWorkbench && (!canAccessMenu || canAccessMenu('workbench'))) {
            return 'workbench';
        }
        return currentMenu || 'buckets';
    }

    function hasWorkbenchContent(overview) {
        var normalizedOverview = normalizeOverview(overview);
        return normalizedOverview.recentDirectories.length > 0
            || normalizedOverview.favoriteDirectories.length > 0
            || normalizedOverview.shareReminders.length > 0;
    }

    var exported = {
        normalizeOverview: normalizeOverview,
        resolveInitialMenu: resolveInitialMenu,
        hasWorkbenchContent: hasWorkbenchContent
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.WorkbenchHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
