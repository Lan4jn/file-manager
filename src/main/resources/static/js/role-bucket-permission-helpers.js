(function (globalScope) {
    function uniqueActions(actions) {
        if (!Array.isArray(actions)) {
            return [];
        }
        return [...new Set(actions.filter(Boolean))];
    }

    function mergeRoleBucketPermissionRows(rows, editedIndex) {
        if (!Array.isArray(rows) || editedIndex < 0 || editedIndex >= rows.length) {
            return {
                rows: Array.isArray(rows) ? rows : [],
                merged: false,
                mergedIntoIndex: -1
            };
        }

        const currentRow = rows[editedIndex];
        const bucketName = currentRow?.bucketName;
        if (!bucketName) {
            return {
                rows: rows.slice(),
                merged: false,
                mergedIntoIndex: -1
            };
        }

        const existingIndex = rows.findIndex((row, index) => index !== editedIndex && row?.bucketName === bucketName);
        if (existingIndex < 0) {
            return {
                rows: rows.slice(),
                merged: false,
                mergedIntoIndex: -1
            };
        }

        const targetRow = rows[existingIndex] || {};
        const sourceRow = currentRow || {};
        const mergedRow = {
            ...targetRow,
            enabled: targetRow.enabled !== false || sourceRow.enabled !== false,
            bucketName,
            actions: uniqueActions([...(targetRow.actions || []), ...(sourceRow.actions || [])])
        };
        const nextRows = rows.slice();
        nextRows[existingIndex] = mergedRow;
        nextRows.splice(editedIndex, 1);

        const normalizedIndex = editedIndex < existingIndex ? existingIndex - 1 : existingIndex;
        return {
            rows: nextRows,
            merged: true,
            mergedIntoIndex: normalizedIndex
        };
    }

    function shouldShowAllBucketsHint(bucketName) {
        return bucketName === '*';
    }

    const exported = {
        mergeRoleBucketPermissionRows,
        shouldShowAllBucketsHint
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.RoleBucketPermissionHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
