(function (globalFactory) {
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = globalFactory();
        return;
    }
    window.FileManagerDownloads = globalFactory();
})(function () {
    const DEFAULT_CHUNK_SIZE = 8 * 1024 * 1024;
    const BATCH_DOWNLOAD_STEPS = [
        '创建归档任务',
        '等待归档完成',
        '开始下载'
    ];
    const ARCHIVE_TASK_TERMINAL_STATUSES = ['COMPLETED', 'FAILED', 'EXPIRED'];
    const DEFAULT_ARCHIVE_TASK_POLL_INTERVAL = 1000;

    const shouldUseChunkedDownload = (sizeBytes, thresholdMB) => {
        const thresholdBytes = Math.max(1, Number(thresholdMB || 100)) * 1024 * 1024;
        return Number(sizeBytes || 0) >= thresholdBytes;
    };

    const buildRangeHeader = (start, chunkSize, totalSize) => {
        const end = Math.min(start + chunkSize - 1, totalSize - 1);
        return 'bytes=' + start + '-' + end;
    };

    const parseContentRange = (value) => {
        if (!value) {
            return null;
        }
        const match = /^bytes\s+(\d+)-(\d+)\/(\d+|\*)$/i.exec(String(value).trim());
        if (!match) {
            return null;
        }
        return {
            start: Number(match[1]),
            end: Number(match[2]),
            total: match[3] === '*' ? null : Number(match[3])
        };
    };

    const saveBlob = (blob, fileName) => {
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', fileName);
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => {
            window.URL.revokeObjectURL(url);
        }, 1000);
    };

    const supportsStreamToDisk = () => {
        return typeof window !== 'undefined'
            && window.isSecureContext
            && typeof window.showSaveFilePicker === 'function'
            && typeof WritableStream !== 'undefined';
    };

    const streamDownloadToDisk = async ({ url, fileName, headers, totalSize, onProgress, fetchImpl }) => {
        if (!supportsStreamToDisk()) {
            throw new Error('当前浏览器不支持边读边写磁盘');
        }
        const handle = await window.showSaveFilePicker({
            suggestedName: fileName || 'download.bin'
        });
        const writable = await handle.createWritable();
        const effectiveFetch = fetchImpl || fetch;
        const startedAt = Date.now();
        let downloadedBytes = 0;
        try {
            const response = await effectiveFetch(url, {
                method: 'GET',
                headers: headers || {}
            });
            if (!response.ok || !response.body) {
                throw new Error('流式下载失败: HTTP ' + response.status);
            }
            const reader = response.body.getReader();
            while (true) {
                const result = await reader.read();
                if (result.done) {
                    break;
                }
                const chunk = result.value;
                await writable.write(chunk);
                downloadedBytes += chunk.byteLength || chunk.length || 0;
                if (typeof onProgress === 'function') {
                    const elapsedSeconds = Math.max((Date.now() - startedAt) / 1000, 0.001);
                    onProgress({
                        downloadedBytes,
                        totalBytes: totalSize || Number(response.headers.get('Content-Length') || 0),
                        progress: totalSize > 0 ? Math.min(100, downloadedBytes / totalSize * 100) : 0,
                        speedBytesPerSecond: downloadedBytes / elapsedSeconds
                    });
                }
            }
            await writable.close();
            return downloadedBytes;
        } catch (error) {
            try {
                await writable.abort();
            } catch (abortError) {
                // ignore abort failure
            }
            throw error;
        }
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
                    const expectedEnd = Math.min(rangeStart + resolvedChunkSize - 1, totalSize - 1);
                    const expectedLength = expectedEnd - rangeStart + 1;
                    const response = await effectiveFetch(url, {
                        method: 'GET',
                        headers: {
                            ...(headers || {}),
                            Range: buildRangeHeader(rangeStart, resolvedChunkSize, totalSize)
                        }
                    });

                    if (response.status !== 206) {
                        throw new Error('分块下载失败，服务端未返回 206');
                    }

                    const buffer = await response.arrayBuffer();
                    if (status === 'cancelled') {
                        return null;
                    }

                    const contentRange = parseContentRange(response.headers.get('Content-Range'));
                    if (!contentRange || contentRange.start !== rangeStart || contentRange.end !== expectedEnd) {
                        throw new Error('分块下载失败，Content-Range 不匹配');
                    }
                    if (contentRange.total !== null && contentRange.total !== totalSize) {
                        throw new Error('分块下载失败，文件大小与预期不一致');
                    }
                    if (buffer.byteLength !== expectedLength) {
                        throw new Error('分块下载失败，分块大小不正确');
                    }

                    chunks.push(buffer);
                    downloadedBytes += buffer.byteLength;
                    nextStart = rangeStart + buffer.byteLength;

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

    const createNativeDownload = (url) => {
        if (!url) {
            throw new Error('当前任务未返回可用下载地址');
        }
        if (typeof document === 'undefined') {
            return url;
        }
        const link = document.createElement('a');
        link.href = url;
        link.rel = 'noopener';
        document.body.appendChild(link);
        link.click();
        link.remove();
        return url;
    };

    const normalizeApiData = (response) => {
        if (response && typeof response === 'object' && Object.prototype.hasOwnProperty.call(response, 'code')
            && Object.prototype.hasOwnProperty.call(response, 'data')) {
            return response.data;
        }
        if (response && typeof response === 'object' && Object.prototype.hasOwnProperty.call(response, 'data')) {
            const payload = response.data;
            if (payload && typeof payload === 'object' && Object.prototype.hasOwnProperty.call(payload, 'code')
                && Object.prototype.hasOwnProperty.call(payload, 'data')) {
                return payload.data;
            }
            return payload;
        }
        return response;
    };

    const sleep = (delayMs) => new Promise((resolve) => {
        setTimeout(resolve, Math.max(0, Number(delayMs || 0)));
    });

    const toArchiveStatus = (task) => String(task && task.status ? task.status : '').toUpperCase();

    const extractArchiveTaskId = (response) => {
        const payload = normalizeApiData(response) || {};
        const taskId = payload.taskId !== undefined && payload.taskId !== null ? payload.taskId : payload.id;
        if (taskId === undefined || taskId === null || taskId === '') {
            throw new Error('创建归档任务失败：缺少 taskId');
        }
        return Number(taskId);
    };

    const resolveArchiveDownloadUrl = (task, taskId) => {
        const payload = task || {};
        if (payload.downloadUrl) {
            return payload.downloadUrl;
        }
        if (payload.id !== undefined && payload.id !== null) {
            return '/api/transfer/archive-tasks/' + payload.id + '/download';
        }
        return '/api/transfer/archive-tasks/' + taskId + '/download';
    };

    const pollArchiveTask = async ({
        taskId,
        getTaskRequest,
        onUpdate,
        wait,
        pollIntervalMs
    }) => {
        if (typeof getTaskRequest !== 'function') {
            throw new Error('缺少归档任务查询方法');
        }
        const waitNext = typeof wait === 'function' ? wait : sleep;
        const delayMs = pollIntervalMs || DEFAULT_ARCHIVE_TASK_POLL_INTERVAL;
        while (true) {
            const response = await getTaskRequest(taskId);
            const task = normalizeApiData(response) || {};
            if (typeof onUpdate === 'function') {
                onUpdate(task);
            }
            if (ARCHIVE_TASK_TERMINAL_STATUSES.includes(toArchiveStatus(task))) {
                return task;
            }
            await waitNext(delayMs);
        }
    };

    const runArchiveTaskDownload = async ({
        payload,
        createTaskRequest,
        getTaskRequest,
        onUpdate,
        wait,
        pollIntervalMs,
        openDownload
    }) => {
        if (typeof createTaskRequest !== 'function') {
            throw new Error('缺少归档任务创建方法');
        }
        const taskId = extractArchiveTaskId(await createTaskRequest(payload));
        const task = await pollArchiveTask({
            taskId,
            getTaskRequest,
            onUpdate,
            wait,
            pollIntervalMs
        });
        const status = toArchiveStatus(task);
        if (status === 'COMPLETED') {
            const downloadUrl = resolveArchiveDownloadUrl(task, taskId);
            const openDownloadFn = typeof openDownload === 'function' ? openDownload : createNativeDownload;
            openDownloadFn(downloadUrl);
            return { taskId, task, downloadUrl };
        }
        if (status === 'FAILED') {
            throw new Error(task.errorMessage || '归档任务执行失败');
        }
        if (status === 'EXPIRED') {
            throw new Error('归档任务已过期，请重新发起下载');
        }
        throw new Error('归档任务状态异常');
    };

    return {
        DEFAULT_CHUNK_SIZE,
        BATCH_DOWNLOAD_STEPS,
        ARCHIVE_TASK_TERMINAL_STATUSES,
        DEFAULT_ARCHIVE_TASK_POLL_INTERVAL,
        shouldUseChunkedDownload,
        buildRangeHeader,
        saveBlob,
        supportsStreamToDisk,
        streamDownloadToDisk,
        createNativeDownload,
        normalizeApiData,
        pollArchiveTask,
        runArchiveTaskDownload,
        resolveArchiveDownloadUrl,
        createChunkedDownloader,
        downloadInChunks,
        downloadBlob
    };
});
