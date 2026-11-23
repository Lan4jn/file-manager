(function (globalScope) {
    function normalizePositiveNumber(value, fallback) {
        var numberValue = Number(value);
        if (!Number.isFinite(numberValue) || numberValue <= 0) {
            return fallback;
        }
        return Math.floor(numberValue);
    }

    function buildPagedShareParams(page, size, validOnly) {
        return {
            page: normalizePositiveNumber(page, 1),
            size: normalizePositiveNumber(size, 25),
            validOnly: !!validOnly
        };
    }

    function resolvePageAfterFilterChange() {
        return 1;
    }

    var exported = {
        buildPagedShareParams: buildPagedShareParams,
        resolvePageAfterFilterChange: resolvePageAfterFilterChange
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }
    if (globalScope) {
        globalScope.SharePaginationHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
