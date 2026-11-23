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

    function shouldPreloadBucketsAfterLogin(initialMenu, permissionCodes, superAdmin) {
        if (initialMenu !== 'workbench') {
            return false;
        }
        if (superAdmin) {
            return true;
        }
        return Array.isArray(permissionCodes) && permissionCodes.indexOf('API_FILE_BROWSE') >= 0;
    }

    function normalizeText(value) {
        return String(value || '').trim();
    }

    function resolveShareReminderAction(reminder) {
        var shareCode = normalizeText(reminder && (reminder.shareCode || reminder.relatedShareCode));
        var messageId = null;
        if (reminder && reminder.messageId !== null && reminder.messageId !== undefined) {
            messageId = reminder.messageId;
        } else if (reminder && reminder.id !== null && reminder.id !== undefined) {
            messageId = reminder.id;
        }
        if (messageId !== null && messageId !== undefined) {
            var messageAction = { type: 'message', messageId: messageId };
            if (shareCode) {
                messageAction.shareCode = shareCode;
            }
            return messageAction;
        }
        if (shareCode) {
            return { type: 'share', shareCode: shareCode };
        }
        return { type: 'message-center' };
    }

    var exported = {
        normalizeOverview: normalizeOverview,
        resolveInitialMenu: resolveInitialMenu,
        hasWorkbenchContent: hasWorkbenchContent,
        shouldPreloadBucketsAfterLogin: shouldPreloadBucketsAfterLogin,
        resolveShareReminderAction: resolveShareReminderAction
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.WorkbenchHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
