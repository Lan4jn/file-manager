(function (globalScope) {
    function resolveDownloadSessionUrl() {
        return '/api/transfer/download/session';
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

    async function runSingleFileDownload(session, navigate) {
        var resolvedSession = session || {};
        if (!resolvedSession.downloadUrl) {
            throw new Error('当前会话未返回可用下载地址');
        }
        var effectiveNavigate = typeof navigate === 'function' ? navigate : createNativeDownload;
        effectiveNavigate(resolvedSession.downloadUrl);
        return resolvedSession.downloadUrl;
    }

    var exported = {
        resolveDownloadSessionUrl: resolveDownloadSessionUrl,
        createNativeDownload: createNativeDownload,
        runSingleFileDownload: runSingleFileDownload
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.TransferUiHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
