(function (globalScope) {
    function buildFileSyncKey(bucket, objectName) {
        return String(bucket || '') + '::' + String(objectName || '');
    }

    function isFileSyncing(file, syncingKeys) {
        if (!file || !syncingKeys || typeof syncingKeys.has !== 'function') {
            return false;
        }
        return syncingKeys.has(buildFileSyncKey(file.bucket || file.bucketName, file.objectName));
    }

    function canOperateFile(file, syncingKeys) {
        return !isFileSyncing(file, syncingKeys);
    }

    function applySyncingState(items, syncingKeys) {
        return (items || []).map(function (item) {
            return Object.assign({}, item, {
                syncing: isFileSyncing(item, syncingKeys)
            });
        });
    }

    function resolveFileSortState(change) {
        if (!change || !change.prop || !change.order) {
            return {
                sortBy: 'name',
                order: 'asc',
                folderFirst: true,
                explicitSort: false
            };
        }
        var sortBy = change.prop;
        if (sortBy === 'fileName') {
            sortBy = 'name';
        } else if (sortBy === 'uploadTime') {
            sortBy = 'time';
        }
        return {
            sortBy: sortBy,
            order: change.order === 'descending' ? 'desc' : 'asc',
            folderFirst: false,
            explicitSort: true
        };
    }

    var exported = {
        buildFileSyncKey: buildFileSyncKey,
        isFileSyncing: isFileSyncing,
        canOperateFile: canOperateFile,
        applySyncingState: applySyncingState,
        resolveFileSortState: resolveFileSortState
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.FileListUiHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
