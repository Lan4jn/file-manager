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
            targetName: item.targetName || raw.name || item.name || '待上传文件',
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

    function applyBatchRename(items, rule) {
        var prefix = String(rule && rule.prefix || '');
        var suffix = String(rule && rule.suffix || '');
        var findText = String(rule && rule.findText || '');
        var replaceText = String(rule && rule.replaceText || '');
        return (items || []).map(function (item) {
            if (!item) {
                return item;
            }
            var baseName = String(item.targetName || item.name || '').trim();
            var nextName = baseName;
            if (findText) {
                nextName = nextName.split(findText).join(replaceText);
            }
            var extIndex = nextName.lastIndexOf('.');
            if (extIndex > 0) {
                var fileBase = nextName.slice(0, extIndex);
                var extension = nextName.slice(extIndex);
                nextName = prefix + fileBase + suffix + extension;
            } else {
                nextName = prefix + nextName + suffix;
            }
            return {
                ...item,
                targetName: nextName
            };
        });
    }

    function shouldFallbackToApplicationUpload(error) {
        var message = String(error && error.message || '').toLowerCase();
        if (!message || message.indexOf('上传已取消') >= 0) {
            return false;
        }
        return message.indexOf('object does not exist') >= 0
            || message.indexOf('no such key') >= 0
            || message.indexOf('nosuchkey') >= 0
            || message.indexOf('not found') >= 0
            || message.indexOf('key does not exist') >= 0
            || message.indexOf('对象不存在') >= 0;
    }

    var exported = {
        normalizePendingUploadFiles: normalizePendingUploadFiles,
        mergePendingUploadFiles: mergePendingUploadFiles,
        clearClosedUploadTasks: clearClosedUploadTasks,
        applyBatchRename: applyBatchRename,
        shouldFallbackToApplicationUpload: shouldFallbackToApplicationUpload
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.UploadDialogHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
