(function (globalFactory) {
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = globalFactory();
        return;
    }
    window.FileManagerDownloads = globalFactory();
})(function () {
    const DEFAULT_CHUNK_SIZE = 8 * 1024 * 1024;
    const BATCH_DOWNLOAD_STEPS = [
        '校验文件',
        '请求服务端打包',
        '接收压缩包',
        '保存文件'
    ];

    const shouldUseChunkedDownload = (sizeBytes, thresholdMB) => {
        const thresholdBytes = Math.max(1, Number(thresholdMB || 100)) * 1024 * 1024;
        return Number(sizeBytes || 0) >= thresholdBytes;
    };

    const buildRangeHeader = (start, chunkSize, totalSize) => {
        const end = Math.min(start + chunkSize - 1, totalSize - 1);
        return 'bytes=' + start + '-' + end;
    };

    const saveBlob = (blob, fileName) => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', fileName);
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
    };

    const downloadInChunks = async ({ url, fileName, totalSize, headers, chunkSize, onProgress }) => {
        const resolvedChunkSize = chunkSize || DEFAULT_CHUNK_SIZE;
        const chunks = [];
        let downloadedBytes = 0;
        const startedAt = Date.now();

        for (let start = 0; start < totalSize; start += resolvedChunkSize) {
            const response = await fetch(url, {
                method: 'GET',
                headers: {
                    ...(headers || {}),
                    Range: buildRangeHeader(start, resolvedChunkSize, totalSize)
                }
            });
            if (!response.ok && response.status !== 206) {
                throw new Error('分块下载失败，状态码：' + response.status);
            }
            const buffer = await response.arrayBuffer();
            chunks.push(buffer);
            downloadedBytes += buffer.byteLength;
            if (typeof onProgress === 'function') {
                const elapsedSeconds = Math.max((Date.now() - startedAt) / 1000, 0.001);
                onProgress({
                    downloadedBytes,
                    totalBytes: totalSize,
                    progress: totalSize > 0 ? Math.min(100, downloadedBytes / totalSize * 100) : 0,
                    speedBytesPerSecond: downloadedBytes / elapsedSeconds
                });
            }
        }

        const blob = new Blob(chunks);
        saveBlob(blob, fileName);
        return blob;
    };

    const downloadBlob = async ({ request, fileName, onProgress }) => {
        const startedAt = Date.now();
        const response = await request((event) => {
            if (typeof onProgress !== 'function') {
                return;
            }
            const loaded = event.loaded || 0;
            const total = event.total || 0;
            const elapsedSeconds = Math.max((Date.now() - startedAt) / 1000, 0.001);
            onProgress({
                downloadedBytes: loaded,
                totalBytes: total,
                progress: total > 0 ? Math.min(100, loaded / total * 100) : 0,
                speedBytesPerSecond: loaded / elapsedSeconds
            });
        });
        const blob = response.data instanceof Blob ? response.data : new Blob([response.data]);
        saveBlob(blob, fileName);
        return blob;
    };

    return {
        DEFAULT_CHUNK_SIZE,
        BATCH_DOWNLOAD_STEPS,
        shouldUseChunkedDownload,
        buildRangeHeader,
        saveBlob,
        downloadInChunks,
        downloadBlob
    };
});
