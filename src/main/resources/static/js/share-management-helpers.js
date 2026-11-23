(function (globalScope) {
    function joinPath(bucket, objectName) {
        var normalizedBucket = String(bucket || '').replace(/\/+$/, '');
        var normalizedObject = String(objectName || '').replace(/^\/+/, '');
        if (!normalizedBucket) {
            return normalizedObject;
        }
        if (!normalizedObject) {
            return normalizedBucket;
        }
        return normalizedBucket + '/' + normalizedObject;
    }

    function normalizeShareContentItems(share, rawItems) {
        if (!share) {
            return [];
        }
        if (!share.isFolder) {
            var singleObjectName = share.objectName || '';
            return [{
                fileName: share.fileName || singleObjectName || '文件',
                objectName: singleObjectName,
                isFolder: false,
                tooltip: joinPath(share.bucket, singleObjectName)
            }];
        }
        return (rawItems || []).map(function (item) {
            var objectName = item.objectName || '';
            return {
                fileName: item.fileName || objectName || '项目',
                objectName: objectName,
                isFolder: !!item.isFolder,
                tooltip: joinPath(share.bucket, objectName)
            };
        });
    }

    function buildVisibleShareContent(items, limit) {
        var normalizedLimit = limit || 3;
        var allItems = items || [];
        return {
            visibleItems: allItems.slice(0, normalizedLimit),
            hiddenCount: Math.max(allItems.length - normalizedLimit, 0),
            hiddenItems: allItems.slice(normalizedLimit)
        };
    }

    function formatShareTargetType(share) {
        if (!share || share.targetType !== 'USER') {
            return '分享到链接';
        }
        return share.recipientDisplayText ? '分享给：' + share.recipientDisplayText : '分享到用户';
    }

    function extractErrorMessage(error) {
        if (error && error.response && error.response.data && error.response.data.message) {
            return error.response.data.message;
        }
        if (error && error.message) {
            return error.message;
        }
        return '分享内容加载失败，请稍后重试';
    }

    function formatShareItemsErrorMessage(error) {
        return '加载失败：' + extractErrorMessage(error);
    }

    var exported = {
        normalizeShareContentItems: normalizeShareContentItems,
        buildVisibleShareContent: buildVisibleShareContent,
        formatShareTargetType: formatShareTargetType,
        formatShareItemsErrorMessage: formatShareItemsErrorMessage
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }
    if (globalScope) {
        globalScope.ShareManagementHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
