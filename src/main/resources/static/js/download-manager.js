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

    const createChunkedDownloader = ({ url, fileName, totalSize, headers, chunkSize, onProgress, saveBlob: saveBlobOverride, fetchImpl }) => {
        const resolvedChunkSize = chunkSize || DEFAULT_CHUNK_SIZE;
        const chunks = [];
        const effectiveFetch = fetchImpl || fetch;
        const effectiveSaveBlob = typeof saveBlobOverride === 'function' ? saveBlobOverride : saveBlob;
        let downloadedBytes = 0;
        let nextStart = 0;
        let startedAt = 0;
        let status = 'idle';
        let error = '';
        let activePromise = null;

        const buildProgressSnapshot = () => {
            const elapsedSeconds = Math.max((Date.now() - startedAt) / 1000, 0.001);
            return {
                downloadedBytes,
                totalBytes: totalSize,
                progress: totalSize > 0 ? Math.min(100, downloadedBytes / totalSize * 100) : 0,
                speedBytesPerSecond: downloadedBytes / elapsedSeconds
            };
        };

        const getState = () => ({
            status,
            error,
            downloadedBytes,
            totalBytes: totalSize,
            progress: totalSize > 0 ? Math.min(100, downloadedBytes / totalSize * 100) : 0
        });

        const run = async () => {
            if (status === 'running') {
                return activePromise;
            }
            if (status === 'completed' || status === 'cancelled') {
                return null;
            }
            if (!startedAt) {
                startedAt = Date.now();
            }
            status = 'running';
            error = '';

            activePromise = (async () => {
                while (nextStart < totalSize) {
                    if (status === 'paused' || status === 'cancelled') {
                        return null;
                    }
                    const rangeStart = nextStart;
                    const response = await effectiveFetch(url, {
                        method: 'GET',
                        headers: {
                            ...(headers || {}),
                            Range: buildRangeHeader(rangeStart, resolvedChunkSize, totalSize)
                        }
                    });
                    if (!response.ok && response.status !== 206) {
                        throw new Error('分块下载失败，状态码：' + response.status);
                    }
                    const buffer = await response.arrayBuffer();
                    if (status === 'cancelled') {
                        return null;
                    }
                    chunks.push(buffer);
                    downloadedBytes += buffer.byteLength;
                    nextStart = rangeStart + resolvedChunkSize;
                    if (typeof onProgress === 'function') {
                        onProgress(buildProgressSnapshot());
                    }
                    if (status === 'paused' || status === 'cancelled') {
                        return null;
                    }
                }

                const blob = new Blob(chunks);
                effectiveSaveBlob(blob, fileName);
                status = 'completed';
                return blob;
            })().catch((downloadError) => {
                if (status === 'paused' || status === 'cancelled') {
                    return null;
                }
                status = 'failed';
                error = downloadError && downloadError.message ? downloadError.message : '下载失败';
                throw downloadError;
            }).finally(() => {
                activePromise = null;
            });

            return activePromise;
        };

        return {
            start: run,
            resume: async () => {
                if (status !== 'paused') {
                    return null;
                }
                return run();
            },
            pause: () => {
                if (status === 'running') {
                    status = 'paused';
                }
            },
            cancel: () => {
                if (status === 'completed') {
                    return;
                }
                status = 'cancelled';
                error = '';
            },
            getState
        };
    };

    const downloadInChunks = async ({ url, fileName, totalSize, headers, chunkSize, onProgress, saveBlob: saveBlobOverride, fetchImpl }) => {
        const downloader = createChunkedDownloader({
            url,
            fileName,
            totalSize,
            headers,
            chunkSize,
            onProgress,
            saveBlob: saveBlobOverride,
            fetchImpl
        });
        return downloader.start();
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
        createChunkedDownloader,
        downloadInChunks,
        downloadBlob
    };
});
