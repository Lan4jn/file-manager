(function (factory) {
    if (typeof module !== 'undefined' && module.exports) {
        module.exports = factory();
    } else {
        window.FileManagerUploads = factory();
    }
})(function () {
    'use strict';

    const MULTIPART_THRESHOLD_BYTES = 50 * 1024 * 1024;
    const DEFAULT_PART_SIZE = 10 * 1024 * 1024;
    const MAX_GLOBAL_CONCURRENT_PARTS = 3;
    const PROGRESS_INTERVAL_MS = 200;

    function shouldUseMultipartUpload(fileSize, thresholdBytes = MULTIPART_THRESHOLD_BYTES) {
        return fileSize >= thresholdBytes;
    }

    function buildFileFingerprint({ bucket, folder, targetFileName, size, lastModified }) {
        return JSON.stringify([bucket, folder, targetFileName, size, lastModified]);
    }

    function createConcurrencyLimiter(limit) {
        if (!Number.isInteger(limit) || limit < 1) throw new RangeError('并发限制必须为正整数');
        const queue = [];
        let active = 0;

        function drain() {
            while (active < limit && queue.length) {
                const { task, signal, resolve, reject, abort } = queue.shift();
                signal?.removeEventListener('abort', abort);
                active++;
                Promise.resolve().then(() => {
                    if (signal?.aborted) throw signal.reason;
                    return task();
                }).then(resolve, reject).finally(() => {
                    active--;
                    drain();
                });
            }
        }

        return (task, signal) => new Promise((resolve, reject) => {
            if (signal?.aborted) {
                reject(signal.reason);
                return;
            }
            const job = { task, signal, resolve, reject, abort() {
                queue.splice(queue.indexOf(job), 1);
                reject(signal.reason);
            } };
            queue.push(job);
            signal?.addEventListener('abort', job.abort, { once: true });
            drain();
        });
    }

    const globalLimiter = createConcurrencyLimiter(MAX_GLOBAL_CONCURRENT_PARTS);

    function waitForRetry(delay, signal) {
        if (signal.aborted) return Promise.reject(signal.reason);
        if (delay <= 0) return Promise.resolve();
        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => {
                signal.removeEventListener('abort', abort);
                resolve();
            }, delay);
            function abort() {
                clearTimeout(timer);
                reject(signal.reason);
            }
            signal.addEventListener('abort', abort, { once: true });
        });
    }

    class RetryableRequestError extends Error {}
    const isRetryableCode = code => Number.isInteger(code) && (code === 429 || (code >= 500 && code < 600));

    function createMultipartUploader({
        file, bucket, folder = '', targetFileName = file.name, contentType = file.type || 'application/octet-stream',
        apiBasePath = '/api', getAuthHeaders, onProgress, onStateChange, fetchImpl = fetch,
        maxRetries = 3, retryDelays = [500, 1500, 3000], limiter = globalLimiter
    }) {
        const base = `${apiBasePath.replace(/\/+$/, '')}/transfer/upload/multipart`;
        const totalBytes = file.size;
        const parts = new Map();
        let status = 'idle';
        let error = null;
        let sessionId = null;
        let partSize = 0;
        let totalParts = 0;
        let uploadedBytes = 0;
        let speedBytesPerSecond = 0;
        let resumed = false;
        let current;
        let cancellation;
        let progressTimer;
        let lastProgressAt = 0;

        function snapshot() {
            return {
                status, uploadedBytes, totalBytes,
                progress: totalBytes ? uploadedBytes * 100 / totalBytes : 0,
                speedBytesPerSecond, completedParts: parts.size, totalParts, resumed, error
            };
        }

        function notify(callback, value) {
            try {
                callback?.(value);
            } catch (_) {
                // 界面回调不应中断上传或清理。
            }
        }

        function emitProgress() {
            lastProgressAt = Date.now();
            notify(onProgress, snapshot());
        }

        function scheduleProgress() {
            if (!onProgress || progressTimer !== undefined) return;
            const remaining = PROGRESS_INTERVAL_MS - (Date.now() - lastProgressAt);
            if (remaining <= 0) {
                emitProgress();
            } else {
                progressTimer = setTimeout(() => {
                    progressTimer = undefined;
                    if (status === 'running') emitProgress();
                }, remaining);
            }
        }

        function setStatus(next, { message = null, abort = false } = {}) {
            status = next;
            error = message;
            clearTimeout(progressTimer);
            progressTimer = undefined;
            if (abort) current?.controller.abort();
            if (status !== next) return;
            notify(onStateChange, snapshot());
            if (status === next) emitProgress();
        }

        const sessionPath = () => `/${encodeURIComponent(sessionId)}`;
        const partStart = partNumber => (partNumber - 1) * partSize;
        const partEnd = partNumber => Math.min(totalBytes, partNumber * partSize);
        function recordPart(partNumber, eTag) {
            if (!parts.has(partNumber)) uploadedBytes += partEnd(partNumber) - partStart(partNumber);
            parts.set(partNumber, eTag);
        }

        async function request(path, method, body, type, signal) {
            const auth = getAuthHeaders?.();
            const headers = typeof Headers !== 'undefined' && auth instanceof Headers
                ? Object.fromEntries(auth) : { ...auth };
            if (type) {
                for (const key of Object.keys(headers)) {
                    if (key.toLowerCase() === 'content-type') delete headers[key];
                }
                headers['Content-Type'] = type;
            }
            if (signal?.aborted) throw signal.reason;

            let response;
            try {
                response = await fetchImpl(`${base}${path}`, { method, headers, body, signal });
            } catch (failure) {
                if (signal?.aborted) throw signal.reason;
                if (method !== 'PUT' || failure?.name === 'AbortError') throw failure;
                throw new RetryableRequestError(failure?.message || '分片上传网络错误');
            }
            if (response.ok === false || response.status >= 400) {
                const RequestError = method === 'PUT' && isRetryableCode(response.status) ? RetryableRequestError : Error;
                throw new RequestError(`上传请求失败：HTTP ${response.status}`);
            }
            let result;
            try {
                result = await response.json();
            } catch (failure) {
                if (method === 'PUT' && !signal?.aborted && failure instanceof TypeError) {
                    throw new RetryableRequestError(failure.message);
                }
                throw failure;
            }
            if (result?.code !== 200) {
                const RequestError = method === 'PUT' && isRetryableCode(result?.code) ? RetryableRequestError : Error;
                throw new RequestError(result?.message || `上传请求失败：${result?.code ?? '无效响应'}`);
            }
            return result.data;
        }

        async function init() {
            const { lastModified } = file;
            const fingerprint = buildFileFingerprint({ bucket, folder, targetFileName, size: totalBytes, lastModified });
            // 后端查找续传会话时也匹配桶、对象路径及大小，可省略超长指纹中的路径。
            const fileFingerprint = fingerprint.length <= 255 ? fingerprint : `metadata:${totalBytes}:${lastModified}`;
            // 已发送的初始化不能丢掉响应，否则取消时可能拿不到需清理的会话 ID。
            const data = await request('/init', 'POST', JSON.stringify({
                fileName: file.name, targetFileName, folder, bucket, contentType, size: totalBytes, fileFingerprint
            }), 'application/json');
            if (typeof data?.sessionId !== 'string' || !data.sessionId) throw new Error('无效的上传会话');
            sessionId = data.sessionId;
            if (status === 'cancelled') return;
            if (!Number.isSafeInteger(data.partSize) || data.partSize < 1
                    || !Number.isSafeInteger(data.totalParts) || data.totalParts !== Math.ceil(totalBytes / data.partSize)
                    || data.totalParts < 1) {
                throw new Error('无效的上传分片配置');
            }
            partSize = data.partSize;
            totalParts = data.totalParts;
            resumed = data.resumed === true;
            for (const { partNumber, eTag } of data.uploadedParts || []) {
                if (Number.isInteger(partNumber) && partNumber >= 1 && partNumber <= totalParts
                        && typeof eTag === 'string' && eTag) {
                    recordPart(partNumber, eTag);
                }
            }
            if (status === 'running') scheduleProgress();
        }

        async function putPart(partNumber, round) {
            const signal = round.controller.signal;
            const path = `${sessionPath()}/parts/${partNumber}`;
            for (let attempt = 0; ; attempt++) {
                try {
                    const data = await limiter(() => {
                        if (signal.aborted) throw signal.reason;
                        return request(path, 'PUT', file.slice(partStart(partNumber), partEnd(partNumber)),
                            'application/octet-stream', signal);
                    }, signal);
                    if (signal.aborted) return;
                    if (typeof data?.eTag !== 'string' || !data.eTag) throw new Error('上传分片响应缺少 eTag');
                    recordPart(partNumber, data.eTag);
                    round.bytes += partEnd(partNumber) - partStart(partNumber);
                    speedBytesPerSecond = round.bytes * 1000 / Math.max(1, Date.now() - round.startedAt);
                    scheduleProgress();
                    return;
                } catch (failure) {
                    if (signal.aborted || !(failure instanceof RetryableRequestError) || attempt >= maxRetries) throw failure;
                }
                // 限制器只包住单次请求，退避时其他分片仍可使用该槽。
                await waitForRetry(retryDelays[attempt] ?? retryDelays[retryDelays.length - 1] ?? 0, signal);
            }
        }

        async function transfer(round) {
            const { controller } = round;
            if (status !== 'running') return null;
            if (!totalParts) await init();
            if (status !== 'running') return null;

            let nextPart = 1;
            async function worker() {
                while (!controller.signal.aborted && nextPart <= totalParts) {
                    const partNumber = nextPart++;
                    if (!parts.has(partNumber)) await putPart(partNumber, round);
                }
            }
            const workers = Array.from({ length: Math.min(totalParts, MAX_GLOBAL_CONCURRENT_PARTS) }, worker);
            try {
                await Promise.all(workers);
            } catch (failure) {
                controller.abort();
                await Promise.allSettled(workers);
                throw failure;
            }
            if (status !== 'running') return null;

            const sorted = [...parts].sort(([a], [b]) => a - b).map(([partNumber, eTag]) => ({ partNumber, eTag }));
            round.completing = true;
            const data = await request(`${sessionPath()}/complete`, 'POST', JSON.stringify({ parts: sorted }),
                'application/json', controller.signal);
            if (status !== 'running') return null;
            setStatus('completed');
            return data;
        }

        function run() {
            const round = { controller: new AbortController(), startedAt: Date.now(), bytes: 0 };
            current = round;
            speedBytesPerSecond = 0;
            // 必须先登记整轮 Promise，状态回调才能安全地同步调用控制方法。
            round.pending = Promise.resolve().then(() => transfer(round)).catch(failure => {
                if (status !== 'running') return null;
                setStatus('failed', { message: failure?.message || String(failure) });
                throw failure;
            });
            setStatus('running');
            return round.pending;
        }

        const canResume = () => status === 'paused' || status === 'failed';
        return {
            start() {
                if (status === 'idle') return run();
                return status === 'running' ? current.pending : Promise.resolve(null);
            },
            pause() {
                // 提交已经发出时不能暂停后重发完成请求。
                if (status === 'running' && !current.completing) setStatus('paused', { abort: true });
            },
            resume() {
                if (!canResume()) return Promise.resolve(null);
                const previous = current;
                const restart = () => {
                    if (previous === current && canResume()) return run();
                    return status === 'running' ? current.pending : null;
                };
                return previous.pending.then(restart, restart);
            },
            cancel() {
                if (status === 'running' && current.completing) return current.pending;
                if (cancellation) return cancellation;
                if (status === 'completed') return Promise.resolve();
                const previous = current?.pending;
                cancellation = Promise.resolve().then(async () => {
                    if (!sessionId && previous) await previous.catch(() => {});
                    if (sessionId) await request(sessionPath(), 'DELETE');
                }).catch(failure => {
                    cancellation = undefined;
                    throw failure;
                });
                if (status !== 'cancelled') setStatus('cancelled', { abort: true });
                return cancellation;
            },
            getState() {
                return { ...snapshot(), sessionId };
            }
        };
    }

    return {
        MULTIPART_THRESHOLD_BYTES, DEFAULT_PART_SIZE, MAX_GLOBAL_CONCURRENT_PARTS,
        shouldUseMultipartUpload, buildFileFingerprint, createConcurrencyLimiter, createMultipartUploader
    };
});
