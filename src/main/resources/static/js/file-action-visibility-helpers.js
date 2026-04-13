(function (globalScope) {
    var ROW_ACTION_META = {
        DOWNLOAD: { code: 'DOWNLOAD', label: '下载', icon: 'Download' },
        SHARE: { code: 'SHARE', label: '分享', icon: 'Share' },
        PREVIEW: { code: 'PREVIEW', label: '预览', icon: 'View' },
        DELETE: { code: 'DELETE', label: '删除', icon: 'Delete' },
        MOVE: { code: 'MOVE', label: '移动', icon: 'Rank' },
        COPY: { code: 'COPY', label: '复制', icon: 'DocumentCopy' }
    };

    function getRowActionMeta(code) {
        var normalizedCode = String(code || '').toUpperCase();
        return ROW_ACTION_META[normalizedCode] || {
            code: normalizedCode,
            label: normalizedCode,
            icon: ''
        };
    }

    function splitRowActions(actions, hiddenActionCodes) {
        var hiddenSet = new Set((hiddenActionCodes || []).map(function (item) {
            return String(item || '').toUpperCase();
        }));
        var visibleActions = (actions || []).filter(function (action) {
            return action && action.visible;
        });
        var primary = [];
        var more = [];
        visibleActions.forEach(function (action) {
            if (hiddenSet.has(String(action.code || '').toUpperCase())) {
                more.push(action);
            } else {
                primary.push(action);
            }
        });
        return {
            primary: primary,
            more: more,
            hasMore: more.length > 0
        };
    }

    var exported = {
        getRowActionMeta: getRowActionMeta,
        splitRowActions: splitRowActions
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.FileActionVisibilityHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
