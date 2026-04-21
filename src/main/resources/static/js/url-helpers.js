(function (globalScope) {
    var contextPathHelpers = globalScope && globalScope.ContextPathHelpers;

    function resolveArguments(contextPathOrBasePath, basePathOrShare, maybeShare, maybeIncludePassword) {
        if (typeof maybeIncludePassword === 'boolean' || (maybeShare && typeof maybeShare === 'object')) {
            return {
                contextPath: contextPathOrBasePath,
                value: basePathOrShare,
                extra: maybeShare,
                flag: maybeIncludePassword
            };
        }
        return {
            contextPath: '',
            value: contextPathOrBasePath,
            extra: basePathOrShare,
            flag: maybeShare
        };
    }

    function buildApiUrl(origin, contextPathOrBasePath, basePathOrParams, maybeParams) {
        var args = resolveArguments(contextPathOrBasePath, basePathOrParams, maybeParams);
        var basePath = args.value;
        var params = args.extra;
        var contextPath = args.contextPath;
        var fullPath = contextPathHelpers && typeof contextPathHelpers.joinContextPath === 'function'
            ? contextPathHelpers.joinContextPath(contextPath, basePath)
            : basePath;
        var url = new URL(fullPath, origin);
        Object.keys(params || {}).forEach(function (key) {
            var value = params[key];
            if (value !== undefined && value !== null && value !== '') {
                url.searchParams.set(key, value);
            }
        });
        return url.toString();
    }

    function buildShareUrl(origin, contextPathOrShare, shareOrIncludePassword, maybeIncludePassword) {
        var args = resolveArguments(contextPathOrShare, shareOrIncludePassword, maybeIncludePassword);
        var contextPath = args.contextPath;
        var share = args.value;
        var includePassword = args.extra;
        var sharePath = '/share/' + ((share && share.shareCode) || '');
        var fullPath = contextPathHelpers && typeof contextPathHelpers.joinContextPath === 'function'
            ? contextPathHelpers.joinContextPath(contextPath, sharePath)
            : sharePath;
        var url = new URL(fullPath, origin);
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
