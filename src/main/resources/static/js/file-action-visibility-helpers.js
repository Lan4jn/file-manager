(function (globalScope) {
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
        splitRowActions: splitRowActions
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.FileActionVisibilityHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
