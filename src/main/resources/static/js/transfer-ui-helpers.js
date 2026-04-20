(function (globalScope) {
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
        return resolved.origin === window.location.origin
            && resolved.pathname.indexOf('/api/file/download') === 0;
    }

    async function fetchProtectedDownload(url, fileName) {
        var token = window.localStorage ? window.localStorage.getItem('token') : '';
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
        var blob = await response.blob();
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
                return fetchBlobDownload(resolvedSession.downloadUrl, resolvedSession.fileName);
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
        createNativeDownload: createNativeDownload,
        runSingleFileDownload: runSingleFileDownload,
        uploadWithXhr: uploadWithXhr
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.TransferUiHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
