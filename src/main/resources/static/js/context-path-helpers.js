(function (global) {
    const normalizeContextPath = (value) => {
        const raw = String(value || '').trim();
        if (!raw || raw === '/') {
            return '';
        }
        const withLeadingSlash = raw.startsWith('/') ? raw : ('/' + raw);
        return withLeadingSlash.replace(/\/+$/, '');
    };

    const normalizeAppPath = (value) => {
        const raw = String(value || '').trim();
        if (!raw) {
            return '/';
        }
        return raw.startsWith('/') ? raw : ('/' + raw);
    };

    const joinContextPath = (contextPath, path) => {
        const normalizedContextPath = normalizeContextPath(contextPath);
        const normalizedPath = normalizeAppPath(path);
        return normalizedContextPath + normalizedPath;
    };

    const inferContextPath = (pathname, suffixes) => {
        const normalizedPath = normalizeAppPath(pathname);
        const candidates = Array.isArray(suffixes) ? suffixes : [];
        for (const suffix of candidates) {
            if (!suffix) {
                continue;
            }
            if (suffix === '/' && normalizedPath.endsWith('/')) {
                return normalizeContextPath(normalizedPath.slice(0, -1) || '/');
            }
            if (normalizedPath === suffix) {
                return '';
            }
            if (normalizedPath.endsWith(suffix)) {
                return normalizeContextPath(normalizedPath.slice(0, -suffix.length) || '/');
            }
        }
        return normalizeContextPath(normalizedPath);
    };

    const isContextPathUrlMatch = (contextPath, pathname, appPath) =>
        String(pathname || '') === joinContextPath(contextPath, appPath);

    const api = {
        normalizeContextPath,
        joinContextPath,
        inferContextPath,
        isContextPathUrlMatch
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
    global.ContextPathHelpers = api;
})(typeof window !== 'undefined' ? window : globalThis);
