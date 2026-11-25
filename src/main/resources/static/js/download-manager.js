(function (globalFactory) {
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = globalFactory();
        return;
    }
    window.FileManagerDownloads = globalFactory();
})(function () {
    const contextPathHelpers = (typeof window !== 'undefined' && window.ContextPathHelpers)
        ? window.ContextPathHelpers
        : {
            joinContextPath: (contextPath, path) => String(contextPath || '') + (String(path || '').startsWith('/') ? String(path || '') : ('/' + String(path || '')))
        };
    const appContextPath = typeof window !== 'undefined' && typeof window.__APP_CONTEXT_PATH__ === 'string'
        ? window.__APP_CONTEXT_PATH__
        : '';
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

    const MAX_MEMORY_SIZE = 100 * 1024 * 1024;
    const STREAM_SEGMENT_SIZE = 16 * 1024 * 1024;
    const STREAM_PROGRESS_INTERVAL = 200;

    class TemporaryDownloadError extends Error {}

    const cancelResponseStream = async (stream) => {
        try {
            if (typeof stream?.cancel === 'function') await stream.cancel();
            else if (typeof stream?.getReader === 'function') {
                const reader = stream.getReader();
                try { await reader.cancel?.(); } finally { reader.releaseLock?.(); }
            }
        } catch (error) { /* 流可能已关闭或报错 */ }
    };

    const createChunkedDownloader = ({
        url, fileName, totalSize, headers, chunkSize, onProgress, fileHandle,
        saveBlob: saveOverride, fetchImpl = fetch, refreshUrl, maxRetries = 3, retryDelays = [500, 1500, 3000]
    }) => {
        const disk = !saveOverride && (fileHandle || supportsStreamToDisk());
        const requestedSize = chunkSize === undefined ? (disk ? STREAM_SEGMENT_SIZE : DEFAULT_CHUNK_SIZE) : chunkSize;
        if (!Number.isSafeInteger(totalSize) || totalSize < 0 || !Number.isSafeInteger(requestedSize) || requestedSize <= 0) throw new Error('无效的下载长度');
        if (!disk && totalSize > MAX_MEMORY_SIZE) throw new Error('文件过大，当前环境不支持边读边写；请使用浏览器下载或支持写盘的 HTTPS 浏览器');
        if (!Number.isSafeInteger(maxRetries) || maxRetries < 0) throw new Error('无效的下载重试次数');
        const segmentSize = disk ? requestedSize : Math.min(requestedSize, DEFAULT_CHUNK_SIZE);
        const parts = [];
        let currentUrl = url;
        let offset = 0;
        let segmentEnd = -1;
        let expiredStatus;
        let strongETag;
        let waitingForNetwork = false;
        let closing = false;
        let started, lastProgress, pending, writable, request, cancelReader, wakeRetry, cancelCleanup;
        let status = 'idle';
        let error = '';
        let resumableFailure = false;

        const getState = () => ({ status, error, downloadedBytes: offset, totalBytes: totalSize, progress: totalSize ? Math.min(100, offset * 100 / totalSize) : 0 });
        const discard = async () => {
            parts.length = 0;
            const stream = writable;
            writable = null;
            try { await stream?.abort?.(); } catch (abortError) { /* 写入流可能已关闭 */ }
        };
        const commitPart = async (chunk, length) => {
            if (writable) await writable.write(chunk);
            else parts.push(chunk);
            if (status === 'cancelled') return;
            offset += length;
            if (!onProgress) return;
            const now = Date.now();
            if (disk && lastProgress !== undefined && offset !== totalSize && now - lastProgress < STREAM_PROGRESS_INTERVAL) return;
            lastProgress = now;
            onProgress({ ...getState(), speedBytesPerSecond: offset / Math.max(0.001, (now - started) / 1000) });
        };

        const retryWait = async (retry) => {
            const delay = retryDelays[Math.min(retry, retryDelays.length - 1)];
            if (!Number.isFinite(delay) || delay <= 0) return;
            await new Promise(resolve => {
                const timer = setTimeout(resolve, delay);
                wakeRetry = () => { clearTimeout(timer); resolve(); };
            });
            wakeRetry = null;
        };

        const readNetwork = async (operation, message) => {
            waitingForNetwork = true;
            try {
                return await operation();
            } catch (cause) {
                throw new TemporaryDownloadError(cause?.message || message, { cause });
            } finally {
                waitingForNetwork = false;
            }
        };

        const fetchSegment = async () => {
            const start = offset;
            const end = segmentEnd;
            request = new AbortController();
            let response, reader;
            let consumed = false;
            try {
                response = await readNetwork(() => fetchImpl(currentUrl, {
                    method: 'GET', headers: { ...headers, Range: buildRangeHeader(start, end - start + 1, totalSize) }, signal: request.signal
                }), '分块下载网络请求失败');
                if (status === 'cancelled' || (disk && status === 'paused')) return;
                const httpStatus = response.status;
                if (httpStatus !== 206) {
                    const message = '分块下载失败: HTTP ' + httpStatus;
                    if (httpStatus >= 500 && httpStatus < 600) throw new TemporaryDownloadError(message);
                    if ([401, 403, 410].includes(httpStatus) && typeof refreshUrl === 'function') return httpStatus;
                    throw new Error(message);
                }
                const range = parseContentRange(response.headers?.get('Content-Range'));
                if (!range || range.start !== start || range.end !== end || (range.total !== null && range.total !== totalSize)) {
                    throw new Error('分块下载失败，Content-Range 不匹配');
                }
                const etag = response.headers?.get('ETag')?.trim();
                if (etag && /^"[\x21\x23-\x7E\x80-\xFF]*"$/.test(etag)) {
                    if (strongETag !== undefined && etag !== strongETag) throw new Error('分块下载失败，文件 ETag 已改变');
                    strongETag = etag;
                }
                if (disk && typeof response.body?.getReader === 'function') {
                    try {
                        reader = response.body.getReader();
                    } catch (cause) {
                        throw new TemporaryDownloadError(cause?.message || '分块下载读取失败', { cause });
                    }
                    let cancellation;
                    cancelReader = () => (cancellation ||= cancelResponseStream(reader));
                    while (status === 'running') {
                        let result;
                        try {
                            result = await readNetwork(() => reader.read(), '分块下载读取失败');
                        } catch (cause) {
                            // 若本段数据已全部确认写入，不再发出空 Range 重试。
                            if (offset > end) return;
                            throw cause;
                        }
                        if (status !== 'running') return;
                        if (result.done) {
                            consumed = true;
                            if (offset <= end) throw new TemporaryDownloadError('分块下载失败，响应提前结束');
                            return;
                        }
                        const length = result.value?.byteLength;
                        if (!Number.isSafeInteger(length) || length < 0 || length > end - offset + 1) {
                            throw new Error('分块下载失败，分块大小不正确');
                        }
                        if (length) await commitPart(result.value, length);
                    }
                    return;
                }

                const buffer = await readNetwork(() => response.arrayBuffer(), '分块下载读取失败');
                consumed = true;
                if (status === 'cancelled' || (disk && status === 'paused')) return;
                const expectedLength = end - start + 1;
                if (buffer.byteLength < expectedLength) throw new TemporaryDownloadError('分块下载失败，分块大小不正确');
                if (buffer.byteLength !== expectedLength) throw new Error('分块下载失败，分块大小不正确');
                await commitPart(buffer, expectedLength);
            } finally {
                if (reader) {
                    if (!consumed) await cancelReader();
                    cancelReader = null;
                    reader.releaseLock?.();
                } else if (!consumed) {
                    await cancelResponseStream(response?.body);
                }
                request = null;
            }
        };

        const transfer = async () => {
            if (disk && !writable) {
                const handle = fileHandle || await window.showSaveFilePicker({ suggestedName: fileName || 'download.bin' });
                if (status === 'cancelled') return null;
                writable = await handle.createWritable();
            }
            while (status === 'running' && offset < totalSize) {
                if (offset > segmentEnd) segmentEnd = offset + Math.min(segmentSize, totalSize - offset) - 1;
                let retries = 0;
                let refreshes = 0;
                while (status === 'running' && offset <= segmentEnd) {
                    if (expiredStatus) {
                        const message = '分块下载地址失效: HTTP ' + expiredStatus;
                        if (refreshes++ >= Math.max(1, maxRetries)) throw new TemporaryDownloadError(message);
                        let refreshed;
                        try {
                            refreshed = await refreshUrl();
                        } catch (cause) {
                            if (status !== 'running') break;
                            throw new TemporaryDownloadError(cause?.message || message, { cause });
                        }
                        if (status === 'cancelled') return null;
                        if (!(refreshed instanceof URL) && (typeof refreshed !== 'string' || !refreshed.trim())) {
                            if (status !== 'running') break;
                            throw new TemporaryDownloadError(message);
                        }
                        currentUrl = refreshed;
                        expiredStatus = undefined;
                        if (status !== 'running') break;
                    }
                    try {
                        expiredStatus = await fetchSegment();
                    } catch (cause) {
                        if (!(cause instanceof TemporaryDownloadError)) throw cause;
                        if (status !== 'running') break;
                        if (retries >= maxRetries) throw cause;
                        await retryWait(retries++);
                        continue;
                    }
                    if (!expiredStatus) break;
                }
            }
            if (status !== 'running') return null;
            let result = null;
            if (writable) {
                closing = true;
                try {
                    await writable.close();
                    writable = null;
                } finally {
                    closing = false;
                }
            } else {
                result = new Blob(parts);
                (saveOverride || saveBlob)(result, fileName);
                parts.length = 0;
            }
            if (status === 'cancelled') return null;
            status = 'completed';
            return result;
        };

        const canResume = () => status === 'paused' || (status === 'failed' && resumableFailure);
        const run = (fromResume = false) => {
            if (pending) return pending;
            if (status === 'cancelled' || status === 'completed' || (status === 'failed' && !fromResume)) return Promise.resolve(null);
            status = 'running';
            error = '';
            resumableFailure = false;
            started ??= Date.now();
            pending = transfer().catch(async cause => {
                if (status === 'cancelled') return null;
                resumableFailure = cause instanceof TemporaryDownloadError;
                status = 'failed';
                error = cause?.message || '下载失败';
                if (!resumableFailure) await discard();
                if (status === 'cancelled') return null;
                throw cause;
            }).finally(async () => {
                if (status === 'cancelled') {
                    await cancelCleanup;
                    await discard(); // 取消时 createWritable 可能尚未返回。
                }
                pending = null;
            });
            return pending;
        };

        return {
            start: () => run(),
            pause() {
                if (status !== 'running' || closing) return;
                status = 'paused';
                if (disk && waitingForNetwork) {
                    request?.abort();
                    void cancelReader?.();
                }
                wakeRetry?.();
            },
            async resume() {
                if (!canResume()) return null;
                if (pending) {
                    try { await pending; } catch (cause) { if (!resumableFailure) throw cause; }
                }
                return canResume() ? run(true) : null;
            },
            cancel() {
                if (closing || status === 'completed' || status === 'cancelled') return;
                status = 'cancelled';
                error = '';
                request?.abort();
                void cancelReader?.();
                wakeRetry?.();
                cancelCleanup = discard();
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
            return contextPathHelpers.joinContextPath(appContextPath, '/api/transfer/archive-tasks/' + payload.id + '/download');
        }
        return contextPathHelpers.joinContextPath(appContextPath, '/api/transfer/archive-tasks/' + taskId + '/download');
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
        STREAM_SEGMENT_SIZE,
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
