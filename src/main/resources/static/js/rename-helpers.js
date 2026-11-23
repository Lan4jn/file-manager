(function (globalScope) {
    function validateRenameTargetName(name) {
        var value = String(name || '').trim();
        if (!value) {
            return '名称不能为空';
        }
        if (value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            return '名称不能包含路径分隔符';
        }
        return '';
    }

    function buildRenamePayload(state, bucket) {
        return {
            bucket: bucket,
            sourcePath: state && state.sourcePath || '',
            targetName: String(state && state.targetName || '').trim(),
            conflictPolicy: state && state.conflictPolicy || 'OVERWRITE'
        };
    }

    var exported = {
        validateRenameTargetName: validateRenameTargetName,
        buildRenamePayload: buildRenamePayload
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.RenameHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
