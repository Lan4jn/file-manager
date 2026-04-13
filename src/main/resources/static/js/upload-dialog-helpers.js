(function (globalScope) {
    function toPendingUploadItem(item, index) {
        if (!item) {
            return null;
        }
        var raw = item.raw || item;
        if (!raw) {
            return null;
        }
        return {
            id: item.uid || ('pending-' + (raw.name || 'file') + '-' + (raw.size || 0) + '-' + (raw.lastModified || 0) + '-' + index),
            name: raw.name || item.name || '待上传文件',
            size: raw.size || item.size || 0,
            raw: raw
        };
    }

    function normalizePendingUploadFiles(files) {
        return (files || []).map(function (item, index) {
            return toPendingUploadItem(item, index);
        }).filter(Boolean);
    }

    function mergePendingUploadFiles(existingFiles, incomingFiles) {
        var merged = [];
        var seen = new Set();
        function buildDedupKey(item) {
            if (!item) {
                return '';
            }
            var raw = item.raw || {};
            var rawName = raw.name || item.name || '';
            var rawSize = raw.size || item.size || 0;
            var rawLastModified = raw.lastModified || 0;
            if (rawName) {
                return rawName + '::' + rawSize + '::' + rawLastModified;
            }
            return String(item.id || '');
        }
        normalizePendingUploadFiles(existingFiles).concat(normalizePendingUploadFiles(incomingFiles)).forEach(function (item) {
            var dedupKey = buildDedupKey(item);
            if (!item || !dedupKey || seen.has(dedupKey)) {
                return;
            }
            seen.add(dedupKey);
            merged.push(item);
        });
        return merged;
    }

    function clearClosedUploadTasks(tasks) {
        return (tasks || []).filter(function (task) {
            return task && task.status === 'running';
        });
    }

    var exported = {
        normalizePendingUploadFiles: normalizePendingUploadFiles,
        mergePendingUploadFiles: mergePendingUploadFiles,
        clearClosedUploadTasks: clearClosedUploadTasks
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.UploadDialogHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
