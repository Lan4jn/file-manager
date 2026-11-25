(function (root) {
    'use strict';

    var STORAGE_KEY = 'fm-theme';

    function normalizeTheme(value) {
        return value === 'dark' ? 'dark' : 'light';
    }

    function resolveInitialTheme(storage) {
        try {
            if (storage && typeof storage.getItem === 'function') {
                return normalizeTheme(storage.getItem(STORAGE_KEY));
            }
        } catch (e) {
            // Ignore storage access errors in restricted frames
        }
        return 'light';
    }

    function applyTheme(theme, doc) {
        var normalized = normalizeTheme(theme);
        if (!doc || !doc.documentElement) {
            return normalized;
        }
        var rootEl = doc.documentElement;
        rootEl.dataset.theme = normalized;
        if (rootEl.classList && typeof rootEl.classList.toggle === 'function') {
            rootEl.classList.toggle('dark', normalized === 'dark');
        }
        if (rootEl.style) {
            rootEl.style.colorScheme = normalized;
        }
        if (typeof doc.querySelector === 'function') {
            var metaThemeColor = doc.querySelector('meta[name="theme-color"]');
            if (metaThemeColor && typeof metaThemeColor.setAttribute === 'function') {
                metaThemeColor.setAttribute('content', normalized === 'dark' ? '#13161b' : '#fafafa');
            }
        }
        return normalized;
    }

    function persistTheme(theme, storage) {
        var normalized = normalizeTheme(theme);
        try {
            if (storage && typeof storage.setItem === 'function') {
                storage.setItem(STORAGE_KEY, normalized);
            }
        } catch (e) {
            // Ignore quota or private mode errors
        }
        return normalized;
    }

    function getSafeStorage(win) {
        var targetWin = win || (typeof window !== 'undefined' ? window : null);
        try {
            if (targetWin && targetWin.localStorage) {
                return targetWin.localStorage;
            }
        } catch (e) {
            // Storage access restricted or disabled
        }
        return null;
    }

    function initTheme(win, doc) {
        var targetWin = win || (typeof window !== 'undefined' ? window : null);
        var targetDoc = doc || (typeof document !== 'undefined' ? document : null);
        var storage = getSafeStorage(targetWin);
        var initial = resolveInitialTheme(storage);
        return applyTheme(initial, targetDoc);
    }

    function toggleTheme(current, storage, doc) {
        var next = normalizeTheme(current) === 'dark' ? 'light' : 'dark';
        var effectiveStorage = storage !== undefined ? storage : getSafeStorage();
        persistTheme(next, effectiveStorage);
        return applyTheme(next, doc);
    }

    var api = {
        STORAGE_KEY: STORAGE_KEY,
        normalizeTheme: normalizeTheme,
        resolveInitialTheme: resolveInitialTheme,
        applyTheme: applyTheme,
        persistTheme: persistTheme,
        initTheme: initTheme,
        toggleTheme: toggleTheme,
        getSafeStorage: getSafeStorage
    };

    if (root) {
        root.FileManagerTheme = api;
    }
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = api;
    }
})(typeof window !== 'undefined' ? window : globalThis);
