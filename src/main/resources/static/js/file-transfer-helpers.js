(function (globalScope) {
    function buildTransferSummary(state) {
        var operation = String(state && state.operation || '').toUpperCase();
        var sources = (state && state.sources) || [];
        if (sources.length === 1) {
            return (operation === 'MOVE' ? '移动 ' : '复制 ') + (sources[0].fileName || sources[0].objectName || '当前项目');
        }
        return (operation === 'MOVE' ? '移动已选择的 ' : '复制已选择的 ') + sources.length + ' 个项目';
    }

    function normalizeTransferTargetFolder(targetFolder) {
        var value = String(targetFolder || '').trim();
        if (!value) {
            return '';
        }
        return value.endsWith('/') ? value : value + '/';
    }

    function resolveConflictPolicy(choice) {
        if (choice === 'overwrite') {
            return 'OVERWRITE';
        }
        if (choice === 'rename') {
            return 'RENAME';
        }
        return '';
    }

    function resolveParentTransferFolder(targetFolder) {
        var normalized = normalizeTransferTargetFolder(targetFolder);
        if (!normalized) {
            return '';
        }
        var parts = normalized.split('/').filter(function (item) {
            return !!item;
        });
        if (parts.length <= 1) {
            return '';
        }
        return parts.slice(0, parts.length - 1).join('/') + '/';
    }

    function buildTransferFolderOptions(rows, currentFolder) {
        var options = [];
        var parentFolder = resolveParentTransferFolder(currentFolder);
        if (normalizeTransferTargetFolder(currentFolder)) {
            options.push({
                fileName: '..',
                objectName: parentFolder,
                isFolder: true,
                isParent: true
            });
        }
        (rows || []).forEach(function (item) {
            if (!item || !item.isFolder) {
                return;
            }
            options.push(item);
        });
        return options;
    }

    var exported = {
        buildTransferSummary: buildTransferSummary,
        buildTransferFolderOptions: buildTransferFolderOptions,
        normalizeTransferTargetFolder: normalizeTransferTargetFolder,
        resolveParentTransferFolder: resolveParentTransferFolder,
        resolveConflictPolicy: resolveConflictPolicy
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.FileTransferHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
