(function (globalScope) {
    function buildApiUrl(origin, basePath, params) {
        var url = new URL(basePath, origin);
        Object.keys(params || {}).forEach(function (key) {
            var value = params[key];
            if (value !== undefined && value !== null && value !== '') {
                url.searchParams.set(key, value);
            }
        });
        return url.toString();
    }

    function buildShareUrl(origin, share, includePassword) {
        var url = new URL('/share/' + ((share && share.shareCode) || ''), origin);
        if (includePassword && share && share.hasPassword && share.password) {
            url.searchParams.set('password', share.password);
        }
        return url.toString();
    }

    var exported = {
        buildApiUrl: buildApiUrl,
        buildShareUrl: buildShareUrl
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.UrlHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
