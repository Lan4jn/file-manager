(function (globalScope) {
    var contextPathHelpers = (function () {
        if (globalScope && globalScope.ContextPathHelpers) {
            return globalScope.ContextPathHelpers;
        }
        if (typeof module !== 'undefined' && module.exports) {
            try {
                return require('./context-path-helpers.js');
            } catch (error) {
                return null;
            }
        }
        return null;
    })();

    function getCurrentContextPath() {
        return globalScope && typeof globalScope.__APP_CONTEXT_PATH__ === 'string'
            ? globalScope.__APP_CONTEXT_PATH__
            : '';
    }

    function createAppDownloadMatcher(contextPath) {
        return function (targetUrl) {
            if (!targetUrl) {
                return false;
            }
            var resolved = new URL(targetUrl, 'http://localhost');
            if (!contextPathHelpers || typeof contextPathHelpers.isContextPathUrlMatch !== 'function') {
                return resolved.pathname.indexOf('/api/file/download') === 0;
            }
            return contextPathHelpers.isContextPathUrlMatch(contextPath, resolved.pathname, '/api/file/download');
        };
    }

    function createProtectedArchiveDownloadMatcher(contextPath) {
        return function (targetUrl) {
            if (!targetUrl) {
                return false;
            }
            var resolved = new URL(targetUrl, 'http://localhost');
            var appPrefix = contextPathHelpers && typeof contextPathHelpers.joinContextPath === 'function'
                ? contextPathHelpers.joinContextPath(contextPath, '/api/transfer/archive-tasks/')
                : '/api/transfer/archive-tasks/';
            return resolved.pathname.indexOf(appPrefix) === 0
                && resolved.pathname.endsWith('/download')
                && !resolved.searchParams.has('shareCode');
        };
    }

    function resolveDownloadSessionUrl() {
        return '/transfer/download/session';
    }

    function resolveUploadSessionUrl() {
        return '/transfer/upload/session';
    }

    function resolveUploadCompleteUrl() {
        return '/transfer/upload/complete';
    }

    function saveBlob(blob, fileName) {
        var url = window.URL.createObjectURL(blob);
        var link = document.createElement('a');
        link.href = url;
        if (fileName) {
            link.setAttribute('download', fileName);
        }
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(function () {
            window.URL.revokeObjectURL(url);
        }, 1000);
    }

    function createNativeDownload(url) {
        if (!url) {
            throw new Error('当前会话未返回可用下载地址');
        }
        if (typeof document === 'undefined') {
            return url;
        }
        var link = document.createElement('a');
        link.href = url;
        link.rel = 'noopener';
        document.body.appendChild(link);
        link.click();
        link.remove();
        return url;
    }

    function shouldFetchWithAuth(url) {
        if (!url || typeof window === 'undefined') {
            return false;
        }
        var resolved = new URL(url, window.location.origin);
        var contextPath = getCurrentContextPath();
        var matcher = createAppDownloadMatcher(contextPath);
        var archiveMatcher = createProtectedArchiveDownloadMatcher(contextPath);
        return resolved.origin === window.location.origin
            && (matcher(resolved.toString()) || archiveMatcher(resolved.toString()));
    }

    var activeTokenProvider = null;

    function setTokenProvider(provider) {
        activeTokenProvider = typeof provider === 'function' ? provider : null;
    }

    function getActiveToken() {
        if (typeof activeTokenProvider === 'function') {
            var provided = activeTokenProvider();
            if (typeof provided === 'string') {
                return provided;
            }
        }
        var token = '';
        try {
            token = (window.localStorage && window.localStorage.getItem('token')) || '';
        } catch (e) {
            token = '';
        }
        return token;
    }

    async function fetchProtectedDownload(url, fileName) {
        var token = getActiveToken();
        var headers = token ? { Authorization: 'Bearer ' + token } : {};
        return fetchBlobDownload(url, fileName, headers);
    }

    async function fetchBlobDownload(url, fileName, headers) {
        var response = await fetch(url, {
            method: 'GET',
            headers: headers || {}
        });
        if (!response.ok) {
            throw new Error('下载失败: HTTP ' + response.status);
        }
        // This fallback buffers only small files; native/signed and file-system downloads stream instead.
        var cap = 100 * 1024 * 1024;
        var length = Number(response.headers.get('Content-Length'));
        var message = '文件超过浏览器内存下载上限，请使用支持写盘的浏览器或授权下载直链';
        if (length > cap) { if (response.body) await response.body.cancel(); throw new Error(message); }
        var reader = response.body && response.body.getReader();
        if (!reader) throw new Error('浏览器不支持安全的分段读取，请使用授权下载直链');
        var parts = [], count = 0;
        try {
            while (true) {
                var item = await reader.read();
                if (item.done) break;
                count += item.value.byteLength;
                if (count > cap) { await reader.cancel(); throw new Error(message); }
                parts.push(item.value);
            }
        } finally { reader.releaseLock(); }
        var blob = new Blob(parts, {type: response.headers.get('Content-Type') || 'application/octet-stream'});
        saveBlob(blob, fileName);
        return url;
    }

    async function runSingleFileDownload(session, navigate) {
        var resolvedSession = session || {};
        if (!resolvedSession.downloadUrl) {
            throw new Error('当前会话未返回可用下载地址');
        }

        if (resolvedSession.fallbackUrl) {
            try {
                return await fetchBlobDownload(resolvedSession.downloadUrl, resolvedSession.fileName);
            } catch (error) {
                if (!resolvedSession.fallbackUrl) {
                    throw error;
                }
                if (shouldFetchWithAuth(resolvedSession.fallbackUrl)) {
                    return fetchProtectedDownload(resolvedSession.fallbackUrl, resolvedSession.fileName);
                }
                var fallbackNavigate = typeof navigate === 'function' ? navigate : createNativeDownload;
                fallbackNavigate(resolvedSession.fallbackUrl);
                return resolvedSession.fallbackUrl;
            }
        }
        if (shouldFetchWithAuth(resolvedSession.downloadUrl)) {
            return fetchProtectedDownload(resolvedSession.downloadUrl, resolvedSession.fileName);
        }
        var effectiveNavigate = typeof navigate === 'function' ? navigate : createNativeDownload;
        effectiveNavigate(resolvedSession.downloadUrl);
        return resolvedSession.downloadUrl;
    }

    async function runArchiveDownload(url, fileName, navigate) {
        if (!url) {
            throw new Error('当前会话未返回可用下载地址');
        }
        if (shouldFetchWithAuth(url)) {
            return fetchProtectedDownload(url, fileName);
        }
        var effectiveNavigate = typeof navigate === 'function' ? navigate : createNativeDownload;
        effectiveNavigate(url);
        return url;
    }

    function uploadWithXhr(options) {
        return new Promise(function (resolve, reject) {
            var xhr = new XMLHttpRequest();
            xhr.open(options.method || 'PUT', options.url, true);
            Object.entries(options.headers || {}).forEach(function (entry) {
                xhr.setRequestHeader(entry[0], entry[1]);
            });
            if (typeof options.onXhr === 'function') {
                options.onXhr(xhr);
            }
            xhr.upload.onprogress = function (event) {
                if (!event.lengthComputable || typeof options.onProgress !== 'function') {
                    return;
                }
                options.onProgress({
                    loaded: event.loaded,
                    total: event.total,
                    percent: event.total > 0 ? (event.loaded / event.total) * 100 : 0
                });
            };
            xhr.onload = function () {
                if (xhr.status >= 200 && xhr.status < 300) {
                    resolve(xhr);
                    return;
                }
                reject(new Error('上传失败: HTTP ' + xhr.status));
            };
            xhr.onerror = function () {
                reject(new Error('网络错误'));
            };
            xhr.onabort = function () {
                reject(new Error('上传已取消'));
            };
            xhr.send(options.body);
        });
    }

    var exported = {
        resolveDownloadSessionUrl: resolveDownloadSessionUrl,
        resolveUploadSessionUrl: resolveUploadSessionUrl,
        resolveUploadCompleteUrl: resolveUploadCompleteUrl,
        createAppDownloadMatcher: createAppDownloadMatcher,
        createProtectedArchiveDownloadMatcher: createProtectedArchiveDownloadMatcher,
        createNativeDownload: createNativeDownload,
        runSingleFileDownload: runSingleFileDownload,
        runArchiveDownload: runArchiveDownload,
        uploadWithXhr: uploadWithXhr,
        setTokenProvider: setTokenProvider
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.TransferUiHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
