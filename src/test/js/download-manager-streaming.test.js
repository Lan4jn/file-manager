const test = require('node:test');
const assert = require('node:assert/strict');
const downloads = require('../../main/resources/static/js/download-manager.js');

const MB = 1024 * 1024;
const bytes = (...values) => Uint8Array.of(...values);
const deferred = () => {
    let resolve;
    const promise = new Promise(done => { resolve = done; });
    return { promise, resolve };
};

function disk(overrides) {
    const writes = [];
    const state = { writes, closed: 0, aborted: 0 };
    const writable = {
        async write(chunk) { writes.push(new Uint8Array(chunk)); },
        async close() { state.closed++; },
        async abort() { state.aborted++; },
        ...overrides
    };
    return { state, fileHandle: { createWritable: async () => writable } };
}

function streamed(range, chunks, hooks = {}) {
    const reader = {
        async read() {
            const value = chunks.shift();
            if (value instanceof Error) throw value;
            return value ? { value, done: false } : { done: true };
        },
        async cancel() {},
        releaseLock() {},
        ...hooks
    };
    return {
        status: 206,
        headers: new Headers({ 'Content-Range': range }),
        body: { getReader: () => reader },
        async arrayBuffer() { assert.fail('流式响应不应读取整个 ArrayBuffer'); }
    };
}

function downloader(fileHandle, totalSize, fetchImpl, options = {}) {
    return downloads.createChunkedDownloader({
        url: '/original', fileHandle, totalSize, fetchImpl, retryDelays: [0], ...options
    });
}

function recordFetch(responses) {
    const calls = [];
    return {
        calls,
        async fetchImpl(url, { headers, signal }) {
            calls.push([url, headers.Range, signal]);
            const response = responses.shift();
            assert.ok(response, `意外的 Range 请求：${headers.Range}`);
            if (response instanceof Error) throw response;
            return response;
        }
    };
}

const requested = calls => calls.map(([url, range]) => [url, range]);
const writeLengths = state => state.writes.map(chunk => chunk.byteLength);
const written = state => state.writes.flatMap(chunk => Array.from(chunk));

// 固定 Range 与流内小块彼此独立，且下一次 read 必须等本次 write 确认。
test('默认按 16 MiB 分段并等待每次写盘后才继续读取', async () => {
    const entered = deferred();
    const release = deferred();
    let reads = 0;
    const writes = [];
    const { state, fileHandle } = disk({ async write(chunk) {
        writes.push(chunk.byteLength);
        if (writes.length === 1) {
            entered.resolve();
            await release.promise;
        }
    } });
    const first = [new Uint8Array(7 * MB), new Uint8Array(9 * MB)];
    const { calls, fetchImpl } = recordFetch([
        streamed('bytes 0-16777215/16777217', [], { async read() {
            reads++;
            const value = first.shift();
            return value ? { value, done: false } : { done: true };
        } }),
        streamed('bytes 16777216-16777216/16777217', [bytes(5)])
    ]);
    const controller = downloader(fileHandle, 16 * MB + 1, fetchImpl);
    const work = controller.start();
    await Promise.race([entered.promise, work.then(() => assert.fail('尚未写盘就结束了'))]);
    try {
        assert.equal(reads, 1);
        assert.equal(controller.getState().downloadedBytes, 0);
    } finally {
        release.resolve();
    }
    await work;
    assert.equal(downloads.STREAM_SEGMENT_SIZE, 16 * MB);
    assert.deepEqual(requested(calls), [['/original', 'bytes=0-16777215'], ['/original', 'bytes=16777216-16777216']]);
    assert.deepEqual(writes, [7 * MB, 9 * MB, 1]);
    assert.equal(state.closed, 1);
});

test('无 reader 时 ArrayBuffer 回退仍尊重显式写盘段大小', async () => {
    const { state, fileHandle } = disk();
    const total = 10 * MB;
    const { calls, fetchImpl } = recordFetch([{
        status: 206,
        headers: new Headers({ 'Content-Range': 'bytes 0-10485759/10485760' }),
        arrayBuffer: async () => new ArrayBuffer(total)
    }]);
    await downloader(fileHandle, total, fetchImpl, { chunkSize: total }).start();
    assert.deepEqual(requested(calls), [['/original', 'bytes=0-10485759']]);
    assert.deepEqual(writeLengths(state), [total]);
    assert.equal(state.closed, 1);
});

test('内存模式保留 8 MiB 默认值与上限，并继续保存 Blob', async () => {
    for (const chunkSize of [undefined, 16 * MB]) {
        const total = 8 * MB + 1;
        const { calls, fetchImpl } = recordFetch([
            { status: 206, headers: new Headers({ 'Content-Range': 'bytes 0-8388607/8388609' }), arrayBuffer: async () => new ArrayBuffer(8 * MB) },
            { status: 206, headers: new Headers({ 'Content-Range': 'bytes 8388608-8388608/8388609' }), arrayBuffer: async () => bytes(42).buffer }
        ]);
        let saved;
        const controller = downloader(undefined, total, fetchImpl, { chunkSize, saveBlob: blob => { saved = blob; } });
        assert.equal(await controller.start(), saved);
        assert.equal(saved.size, total);
        assert.deepEqual(requested(calls), [['/original', 'bytes=0-8388607'], ['/original', 'bytes=8388608-8388608']]);
    }
    assert.equal(downloads.DEFAULT_CHUNK_SIZE, 8 * MB);
    assert.throws(() => downloader(undefined, 100 * MB + 1, undefined, { saveBlob() {} }), /文件过大/);
});

test('读取异常、网络失败、5xx 和短读从已写偏移重试至同一段末尾', async () => {
    const { state, fileHandle } = disk();
    let unusedCancelled = 0;
    const { calls, fetchImpl } = recordFetch([
        streamed('bytes 0-4/6', [bytes(1, 2), new Error('读取中断')]),
        new TypeError('网络中断'),
        { status: 503, body: { async cancel() { unusedCancelled++; } }, headers: new Headers() },
        streamed('bytes 2-4/6', [bytes(3)]),
        streamed('bytes 3-4/6', [bytes(4, 5)]),
        streamed('bytes 5-5/6', [bytes(6)])
    ]);
    await downloader(fileHandle, 6, fetchImpl, { chunkSize: 5, maxRetries: 4 }).start();
    assert.deepEqual(requested(calls), [
        ['/original', 'bytes=0-4'], ['/original', 'bytes=2-4'], ['/original', 'bytes=2-4'],
        ['/original', 'bytes=2-4'], ['/original', 'bytes=3-4'], ['/original', 'bytes=5-5']
    ]);
    assert.deepEqual(written(state), [1, 2, 3, 4, 5, 6]);
    assert.equal(unusedCancelled, 1);
    assert.equal(state.aborted, 0);
});

for (const status of [401, 403, 410]) {
    test(`HTTP ${status} 回收响应并换签，从已写偏移续传`, async () => {
        const { state, fileHandle } = disk();
        let unusedCancelled = 0;
        const unusedBody = status === 401
            ? streamed('bytes 2-3/4', [], { async cancel() { unusedCancelled++; } }).body
            : { async cancel() { unusedCancelled++; } };
        const { calls, fetchImpl } = recordFetch([
            streamed('bytes 0-3/4', [bytes(1, 2), new Error('读取中断')]),
            { status, body: unusedBody, headers: new Headers() },
            streamed('bytes 2-3/4', [bytes(3, 4)])
        ]);
        await downloader(fileHandle, 4, fetchImpl, { async refreshUrl() {
            assert.equal(unusedCancelled, 1);
            return '/refreshed';
        } }).start();
        assert.deepEqual(requested(calls), [['/original', 'bytes=0-3'], ['/original', 'bytes=2-3'], ['/refreshed', 'bytes=2-3']]);
        assert.deepEqual(written(state), [1, 2, 3, 4]);
    });
}

test('本轮换签次数有上限，耗尽后保留数据以便手动续传', async () => {
    const { state, fileHandle } = disk();
    let refreshes = 0;
    let unusedCancelled = 0;
    const unsigned = () => ({ status: 403, body: { async cancel() { unusedCancelled++; } }, headers: new Headers() });
    const { calls, fetchImpl } = recordFetch([
        streamed('bytes 0-3/4', [bytes(1, 2), new Error('读取中断')]),
        unsigned(), unsigned(), unsigned(), unsigned(),
        streamed('bytes 2-3/4', [bytes(3, 4)])
    ]);
    const controller = downloader(fileHandle, 4, fetchImpl, {
        maxRetries: 3,
        async refreshUrl() { return `/refreshed-${++refreshes}`; }
    });
    await assert.rejects(controller.start(), /403/);
    assert.equal(refreshes, 3);
    assert.equal(unusedCancelled, 4);
    assert.equal(calls.length, 5);
    assert.equal(state.aborted, 0);
    assert.deepEqual(written(state), [1, 2]);
    await controller.resume();
    assert.equal(controller.getState().status, 'completed');
    assert.deepEqual(requested(calls).at(-1), ['/refreshed-4', 'bytes=2-3']);
    assert.deepEqual(written(state), [1, 2, 3, 4]);
});

test('首次写盘回调可暂停，续传保留数据、同一段末尾及写入句柄', async () => {
    const { state, fileHandle } = disk();
    const { calls, fetchImpl } = recordFetch([
        streamed('bytes 0-3/5', [bytes(1, 2), bytes(3, 4)]),
        streamed('bytes 2-3/5', [bytes(3, 4)]),
        streamed('bytes 4-4/5', [bytes(5)])
    ]);
    let first = true;
    const controller = downloader(fileHandle, 5, fetchImpl, { chunkSize: 4, onProgress() {
        if (first) {
            first = false;
            controller.pause();
        }
    } });
    await controller.start();
    assert.equal(controller.getState().status, 'paused');
    assert.equal(controller.getState().downloadedBytes, 2);
    assert.deepEqual(written(state), [1, 2]);
    assert.equal(calls[0][2].aborted, false);
    assert.equal(state.aborted, 0);
    assert.equal(state.closed, 0);
    await controller.resume();
    assert.equal(controller.getState().status, 'completed');
    assert.deepEqual(requested(calls), [['/original', 'bytes=0-3'], ['/original', 'bytes=2-3'], ['/original', 'bytes=4-4']]);
    assert.deepEqual(written(state), [1, 2, 3, 4, 5]);
    assert.equal(state.closed, 1);
});

test('暂态错误耗尽重试仍可从已确认偏移恢复', async () => {
    const { state, fileHandle } = disk();
    const { calls, fetchImpl } = recordFetch([
        streamed('bytes 0-3/4', [bytes(1, 2), new Error('读取中断')]),
        new Error('网络中断'),
        streamed('bytes 2-3/4', [bytes(3, 4)])
    ]);
    const controller = downloader(fileHandle, 4, fetchImpl, { maxRetries: 1 });
    await assert.rejects(controller.start(), /网络中断/);
    assert.equal(controller.getState().downloadedBytes, 2);
    assert.equal(state.aborted, 0);
    await controller.resume();
    assert.equal(controller.getState().error, '');
    assert.equal(controller.getState().status, 'completed');
    assert.deepEqual(requested(calls), [['/original', 'bytes=0-3'], ['/original', 'bytes=2-3'], ['/original', 'bytes=2-3']]);
    assert.deepEqual(written(state), [1, 2, 3, 4]);
});

test('换签请求临时故障不丢弃已写数据，恢复后换签并续传', async () => {
    const { state, fileHandle } = disk();
    const { calls, fetchImpl } = recordFetch([
        streamed('bytes 0-3/4', [bytes(1, 2), new Error('读取中断')]),
        { status: 401, headers: new Headers() },
        streamed('bytes 2-3/4', [bytes(3, 4)])
    ]);
    let refreshes = 0;
    const controller = downloader(fileHandle, 4, fetchImpl, { async refreshUrl() {
        if (++refreshes === 1) throw new Error('换签网络暂时中断');
        return '/refreshed';
    } });
    await assert.rejects(controller.start(), /换签网络暂时中断/);
    assert.equal(controller.getState().downloadedBytes, 2);
    assert.equal(state.aborted, 0);
    await controller.resume();
    assert.equal(controller.getState().status, 'completed');
    assert.equal(refreshes, 2);
    assert.deepEqual(requested(calls), [['/original', 'bytes=0-3'], ['/original', 'bytes=2-3'], ['/refreshed', 'bytes=2-3']]);
    assert.deepEqual(written(state), [1, 2, 3, 4]);
});

test('换签返回同一地址时仍允许有限次数的新请求', async () => {
    const { state, fileHandle } = disk();
    const { calls, fetchImpl } = recordFetch([
        { status: 401, headers: new Headers() },
        streamed('bytes 0-1/2', [bytes(1, 2)])
    ]);
    await downloader(fileHandle, 2, fetchImpl, { async refreshUrl() { return '/original'; } }).start();
    assert.deepEqual(requested(calls), [['/original', 'bytes=0-1'], ['/original', 'bytes=0-1']]);
    assert.deepEqual(written(state), [1, 2]);
});

test('文件进入提交后，暂停或取消均不会覆盖真实提交结果', async () => {
    const entered = deferred();
    const release = deferred();
    const { state, fileHandle } = disk({ async close() {
        entered.resolve();
        await release.promise;
    } });
    const { fetchImpl } = recordFetch([streamed('bytes 0-1/2', [bytes(1, 2)])]);
    const controller = downloader(fileHandle, 2, fetchImpl);
    const work = controller.start();
    await Promise.race([entered.promise, work.then(() => assert.fail('尚未提交文件就结束了'))]);
    try {
        controller.pause();
        assert.equal(controller.getState().status, 'running');
        controller.cancel();
        assert.equal(controller.getState().status, 'running');
    } finally {
        release.resolve();
    }
    await work;
    assert.equal(controller.getState().status, 'completed');
    assert.equal(state.aborted, 0);
});

test('非法 Content-Range 不消费响应、不写盘、不重试', async () => {
    const { state, fileHandle } = disk();
    let cancelled = 0;
    const { calls, fetchImpl } = recordFetch([{
        status: 206,
        headers: new Headers({ 'Content-Range': 'bytes 1-4/4' }),
        body: { async cancel() { cancelled++; } }
    }]);
    const controller = downloader(fileHandle, 4, fetchImpl);
    await assert.rejects(controller.start(), /Content-Range/);
    assert.equal(cancelled, 1);
    assert.equal(await controller.resume(), null);
    assert.equal(calls.length, 1);
    assert.equal(state.writes.length, 0);
});

test('写盘失败不作为网络错误重试或恢复', async () => {
    const { state, fileHandle } = disk({ async write() { throw new Error('磁盘写入失败'); } });
    const { calls, fetchImpl } = recordFetch([streamed('bytes 0-3/4', [bytes(1, 2), bytes(3, 4)])]);
    const controller = downloader(fileHandle, 4, fetchImpl);
    await assert.rejects(controller.start(), /磁盘写入失败/);
    assert.equal(controller.getState().downloadedBytes, 0);
    assert.equal(await controller.resume(), null);
    assert.equal(calls.length, 1);
    assert.equal(state.closed, 0);
});

for (const phase of ['读取', '写盘']) {
    test(`取消阻止尚未完成的${phase}更新进度或提交文件`, async () => {
        const entered = deferred();
        const release = deferred();
        let readerCancelled = 0;
        let progress = 0;
        const overrides = phase === '写盘' ? { async write() {
            entered.resolve();
            await release.promise;
        } } : {};
        const { state, fileHandle } = disk(overrides);
        const hooks = { async cancel() { readerCancelled++; } };
        if (phase === '读取') hooks.read = async () => {
            entered.resolve();
            await release.promise;
            return { value: bytes(1, 2), done: false };
        };
        const { calls, fetchImpl } = recordFetch([streamed('bytes 0-3/4', [bytes(1, 2)], hooks)]);
        const controller = downloader(fileHandle, 4, fetchImpl, { onProgress() { progress++; } });
        const work = controller.start();
        await Promise.race([entered.promise, work.then(() => assert.fail('没有开始' + phase))]);
        controller.cancel();
        try {
            assert.equal(calls[0][2].aborted, true);
            assert.equal(readerCancelled, 1);
        } finally {
            release.resolve();
        }
        await work;
        assert.equal(controller.getState().status, 'cancelled');
        assert.equal(controller.getState().downloadedBytes, 0);
        assert.equal(progress, 0);
        assert.equal(state.writes.length, 0);
        assert.equal(state.closed, 0);
        assert.equal(state.aborted, 1);
    });
}

test('写盘等待 fetch 时暂停会释放请求，恢复不会等待旧轮', async () => {
    const entered = deferred();
    const release = deferred();
    const { state, fileHandle } = disk();
    let requests = 0;
    let firstSignal;
    const controller = downloader(fileHandle, 2, async (url, { headers, signal }) => {
        assert.equal(headers.Range, 'bytes=0-1');
        const response = streamed('bytes 0-1/2', [bytes(1, 2)]);
        if (++requests !== 1) return response;
        firstSignal = signal;
        return new Promise((resolve, reject) => {
            signal.addEventListener('abort', () => reject(signal.reason), { once: true });
            release.promise.then(() => resolve(response));
            entered.resolve();
        });
    });
    const work = controller.start();
    await Promise.race([entered.promise, work.then(() => assert.fail('尚未请求就退出'))]);
    try {
        controller.pause();
        assert.equal(firstSignal.aborted, true);
        await controller.resume();
    } finally {
        release.resolve();
        await work;
    }
    assert.equal(requests, 2);
    assert.equal(controller.getState().status, 'completed');
    assert.equal(state.aborted, 0);
    assert.deepEqual(written(state), [1, 2]);
});

test('写盘等待 reader 时暂停会释放读取，恢复仍从已写偏移续传', async () => {
    const entered = deferred();
    const release = deferred();
    const { state, fileHandle } = disk();
    let reads = 0;
    let cancelled = 0;
    const { calls, fetchImpl } = recordFetch([
        streamed('bytes 0-3/4', [], {
            async read() {
                if (++reads === 1) return { value: bytes(1, 2), done: false };
                entered.resolve();
                return release.promise;
            },
            async cancel() { cancelled++; release.resolve({ done: true }); }
        }),
        streamed('bytes 2-3/4', [bytes(3, 4)])
    ]);
    const controller = downloader(fileHandle, 4, fetchImpl);
    const work = controller.start();
    await Promise.race([entered.promise, work.then(() => assert.fail('尚未等待读取就退出'))]);
    try {
        controller.pause();
        assert.equal(cancelled, 1);
        await controller.resume();
    } finally {
        release.resolve({ done: true });
        await work;
    }
    assert.deepEqual(requested(calls), [['/original', 'bytes=0-3'], ['/original', 'bytes=2-3']]);
    assert.deepEqual(written(state), [1, 2, 3, 4]);
    assert.equal(state.aborted, 0);
});

test('写盘等待 write 时暂停不撤销在途写入，恢复从确认后的偏移继续', async () => {
    const entered = deferred();
    const release = deferred();
    const writes = [];
    const { state, fileHandle } = disk({ async write(chunk) {
        if (writes.length === 0) {
            entered.resolve();
            await release.promise;
        }
        writes.push(...chunk);
    } });
    const { calls, fetchImpl } = recordFetch([
        streamed('bytes 0-3/4', [bytes(1, 2), bytes(3, 4)]),
        streamed('bytes 2-3/4', [bytes(3, 4)])
    ]);
    const controller = downloader(fileHandle, 4, fetchImpl);
    const work = controller.start();
    await Promise.race([entered.promise, work.then(() => assert.fail('尚未写入就退出'))]);
    let resumed;
    try {
        controller.pause();
        assert.equal(controller.getState().downloadedBytes, 0);
        assert.equal(calls[0][2].aborted, false);
        resumed = controller.resume();
    } finally {
        release.resolve();
        await work;
    }
    await resumed;
    assert.deepEqual(requested(calls), [['/original', 'bytes=0-3'], ['/original', 'bytes=2-3']]);
    assert.deepEqual(writes, [1, 2, 3, 4]);
    assert.equal(state.aborted, 0);
});

test('重试响应的强 ETag 改变时不写入新版本也不提交文件', async () => {
    const { state, fileHandle } = disk();
    const first = streamed('bytes 0-3/4', [bytes(1, 2), new Error('读取中断')]);
    first.headers.set('ETag', '"first"');
    const second = streamed('bytes 2-3/4', [bytes(3, 4)]);
    second.headers.set('ETag', '"second"');
    let requests = 0;
    const controller = downloader(fileHandle, 4, async (url, { headers }) => {
        assert.equal(new Headers(headers).has('If-Range'), false);
        return ++requests === 1 ? first : second;
    });
    await assert.rejects(controller.start(), /ETag/);
    assert.equal(requests, 2);
    assert.deepEqual(written(state), [1, 2]);
    assert.equal(state.closed, 0);
    assert.equal(await controller.resume(), null);
});

test('缺少可见强 ETag 的响应仍可兼容续传', async () => {
    const { state, fileHandle } = disk();
    const first = streamed('bytes 0-1/6', [bytes(1, 2)]);
    first.headers.set('ETag', '"first"');
    const middle = streamed('bytes 2-3/6', [bytes(3, 4)]);
    middle.headers.set('ETag', 'W/"weak"');
    const last = streamed('bytes 4-5/6', [bytes(5, 6)]);
    const { fetchImpl } = recordFetch([first, middle, last]);
    await downloader(fileHandle, 6, fetchImpl, { chunkSize: 2 }).start();
    assert.deepEqual(written(state), [1, 2, 3, 4, 5, 6]);
    assert.equal(state.closed, 1);
});

test('写盘进度立即报首尾，中间按 200 毫秒节流', async t => {
    let now = 1000;
    t.mock.method(Date, 'now', () => now);
    const reports = [];
    const times = [1000, 1100, 1200, 1250, 1251];
    const { fileHandle } = disk({ async write() { now = times.shift(); } });
    const { fetchImpl } = recordFetch([streamed('bytes 0-4/5', [bytes(1), bytes(2), bytes(3), bytes(4), bytes(5)])]);
    await downloader(fileHandle, 5, fetchImpl, { onProgress: ({ downloadedBytes }) => reports.push([now, downloadedBytes]) }).start();
    assert.deepEqual(reports, [[1000, 1], [1200, 3], [1251, 5]]);
});
