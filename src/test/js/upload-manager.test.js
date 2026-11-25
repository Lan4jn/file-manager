const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const modulePath = '../../main/resources/static/js/upload-manager.js';
const uploads = () => require(modulePath);
const root = '/api/transfer/upload/multipart';
const progressFields = [
    'completedParts', 'error', 'progress', 'resumed', 'speedBytesPerSecond',
    'status', 'totalBytes', 'totalParts', 'uploadedBytes'
];

function fileOf(contents = 'abcdefghij') {
    return new File([contents], 'source.bin', { type: 'application/example', lastModified: 12345 });
}

function result(data, code = 200, message) {
    return new Response(JSON.stringify({ code, data, message }));
}

function session(sessionId, totalParts, uploadedParts = [], partSize = 4) {
    return {
        sessionId, bucket: 'docs', objectName: 'imports/target.bin', fileName: 'target.bin',
        partSize, totalParts, resumed: uploadedParts.length > 0, uploadedParts
    };
}

function deferred() {
    let resolve;
    let reject;
    const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
    return { promise, resolve, reject };
}

function upload(fetchImpl, options = {}) {
    return uploads().createMultipartUploader({
        file: fileOf(), bucket: 'docs', folder: 'imports/', targetFileName: 'target.bin',
        fetchImpl, retryDelays: [0, 0, 0], ...options
    });
}

function assertProgressSnapshot(snapshot) {
    assert.deepEqual(Object.keys(snapshot).sort(), progressFields);
    for (const value of Object.values(snapshot)) {
        assert.ok(value === null || ['string', 'number', 'boolean'].includes(typeof value));
    }
}

test('浏览器脚本以 FileManagerUploads 导出', () => {
    const context = { window: {} };
    vm.runInNewContext(fs.readFileSync(require.resolve(modulePath), 'utf8'), context);
    assert.equal(typeof context.window.FileManagerUploads.createMultipartUploader, 'function');
});

test('分片阈值包含临界值，并允许自定义字节数', () => {
    const api = uploads();
    assert.equal(api.MULTIPART_THRESHOLD_BYTES, 52_428_800);
    assert.equal(api.DEFAULT_PART_SIZE, 10_485_760);
    assert.equal(api.MAX_GLOBAL_CONCURRENT_PARTS, 3);
    assert.equal(api.shouldUseMultipartUpload(52_428_799), false);
    assert.equal(api.shouldUseMultipartUpload(52_428_800), true);
    assert.equal(api.shouldUseMultipartUpload(52_428_801), true);
    assert.equal(api.shouldUseMultipartUpload(7, 8), false);
    assert.equal(api.shouldUseMultipartUpload(8, 8), true);
});

test('指纹只依赖五个元数据字段，不读取文件内容或附加字段', () => {
    const { buildFileFingerprint } = uploads();
    const meta = {
        bucket: 'docs', folder: 'imports/', targetFileName: 'target.bin', size: 10, lastModified: 12345
    };
    const fingerprint = buildFileFingerprint(meta);
    assert.equal(typeof fingerprint, 'string');
    assert.ok(fingerprint.length > 0);
    assert.equal(buildFileFingerprint({
        ...meta,
        get file() { assert.fail('不应读取文件'); },
        get content() { assert.fail('不应读取内容'); },
        slice() { assert.fail('不应分片'); }
    }), fingerprint);
    for (const [key, value] of Object.entries({
        bucket: 'other', folder: 'backup/', targetFileName: 'other.bin', size: 11, lastModified: 54321
    })) {
        assert.notEqual(buildFileFingerprint({ ...meta, [key]: value }), fingerprint);
    }
    assert.notEqual(buildFileFingerprint({ ...meta, bucket: 'a:b', folder: 'c' }),
        buildFileFingerprint({ ...meta, bucket: 'a', folder: 'b:c' }));
});

test('长路径的公开指纹保留完整元数据字符串', () => {
    const { buildFileFingerprint } = uploads();
    const folder = 'nested/'.repeat(50);
    const targetFileName = '长'.repeat(200);
    const metadata = { bucket: 'docs', folder, targetFileName, size: 10, lastModified: 12345 };
    assert.equal(buildFileFingerprint(metadata), JSON.stringify(['docs', folder, targetFileName, 10, 12345]));
    assert.notEqual(buildFileFingerprint({ ...metadata, folder: `${folder}other/` }), buildFileFingerprint(metadata));
});

test('初始化长路径时发送可复用、符合 255 字符限制的元数据短指纹', async () => {
    const options = { folder: 'nested/'.repeat(50), targetFileName: '长'.repeat(200) };
    let previousFingerprint;
    const sent = [];
    const fetchImpl = async (url, request) => {
        if (url.endsWith('/init')) {
            const { bucket, folder, targetFileName, fileFingerprint } = JSON.parse(request.body);
            assert.equal(bucket, 'docs');
            assert.equal(folder, options.folder);
            assert.equal(targetFileName, options.targetFileName);
            assert.equal(fileFingerprint, 'metadata:10:12345');
            assert.ok(fileFingerprint.length <= 255);
            const uploadedParts = previousFingerprint === fileFingerprint ? [{ partNumber: 1, eTag: 'saved' }] : [];
            previousFingerprint = fileFingerprint;
            return result(session('long-path', 3, uploadedParts));
        }
        if (url.endsWith('/complete')) return result('done');
        sent.push(Number(url.split('/').at(-1)));
        return result({ eTag: 'saved' });
    };
    assert.equal(await upload(fetchImpl, options).start(), 'done');
    assert.deepEqual(sent, [1, 2, 3]);
    const second = upload(fetchImpl, options);
    assert.equal(await second.start(), 'done');
    assert.equal(second.getState().resumed, true);
    assert.deepEqual(sent, [1, 2, 3, 2, 3]);
});

test('初始化、鉴权分片与升序完成请求，并返回 Result.data', { timeout: 2000 }, async () => {
    const progress = [];
    const statuses = [];
    const sent = [];
    const completed = [];
    const firstPart = deferred();
    const base = '/context/api/transfer/upload/multipart';
    const response = { objectName: 'imports/target.bin' };
    const uploader = upload(async (url, request) => {
        const headers = new Headers(request.headers);
        assert.equal(headers.get('Authorization'), 'Bearer example');
        if (url === `${base}/init`) {
            assert.equal(request.method, 'POST');
            assert.equal(headers.get('Content-Type'), 'application/json');
            const payload = JSON.parse(request.body);
            assert.deepEqual(payload, {
                fileName: 'source.bin', targetFileName: 'target.bin', folder: 'imports/', bucket: 'docs',
                contentType: 'application/override', size: 10,
                fileFingerprint: uploads().buildFileFingerprint({
                    bucket: 'docs', folder: 'imports/', targetFileName: 'target.bin', size: 10, lastModified: 12345
                })
            });
            return result(session('new', 3));
        }
        if (url === `${base}/new/complete`) {
            assert.equal(request.method, 'POST');
            assert.deepEqual(completed, [2, 3, 1]);
            assert.deepEqual(JSON.parse(request.body), {
                parts: [1, 2, 3].map(partNumber => ({ partNumber, eTag: `tag-${partNumber}` }))
            });
            return result(response);
        }
        const partNumber = Number(url.match(/\/new\/parts\/(\d+)$/)?.[1]);
        assert.equal(request.method, 'PUT');
        assert.equal(headers.get('Content-Type'), 'application/octet-stream');
        assert.ok(request.signal instanceof AbortSignal);
        sent.push([partNumber, await request.body.text()]);
        if (partNumber === 1) await firstPart.promise;
        completed.push(partNumber);
        if (partNumber === 3) firstPart.resolve();
        return result({ partNumber, eTag: `tag-${partNumber}` });
    }, {
        apiBasePath: '/context/api', contentType: 'application/override',
        getAuthHeaders: () => ({ Authorization: 'Bearer example' }),
        onProgress: snapshot => progress.push(snapshot),
        onStateChange: snapshot => statuses.push(snapshot.status)
    });
    assert.equal(uploader.getState().status, 'idle');
    const pending = uploader.start();
    assert.equal(progress[0].status, 'running');
    assert.equal(progress[0].uploadedBytes, 0);
    assert.deepEqual(await pending, response);
    assert.deepEqual(sent, [[1, 'abcd'], [2, 'efgh'], [3, 'ij']]);
    assert.deepEqual(progress.map(snapshot => snapshot.status), ['running', 'completed']);
    progress.forEach(assertProgressSnapshot);
    assert.deepEqual(statuses, ['running', 'completed']);
    const final = progress.at(-1);
    assert.equal(final.uploadedBytes, 10);
    assert.equal(final.completedParts, 3);
    assert.equal(final.totalParts, 3);
    assert.equal(final.progress, 100);
    assert.equal(uploader.getState().sessionId, 'new');
});

test('进度合并在 200ms 发出，结束立刻上报并清除待发回调', { timeout: 2000 }, async t => {
    t.mock.timers.enable({ apis: ['Date', 'setTimeout'] });
    const first = deferred();
    const second = deferred();
    const waitingForSecond = deferred();
    const completing = deferred();
    const allowComplete = deferred();
    const emitted = [];
    const uploader = upload(async url => {
        if (url.endsWith('/init')) return result(session('progress', 2));
        if (url.endsWith('/complete')) {
            completing.resolve();
            await allowComplete.promise;
            return result('done');
        }
        if (url.endsWith('/1')) {
            await first.promise;
        } else {
            waitingForSecond.resolve();
            await second.promise;
        }
        return result({ eTag: 'saved' });
    }, {
        file: fileOf('abcdefgh'),
        onProgress: state => emitted.push([Date.now(), state.status, state.uploadedBytes])
    });
    const pending = uploader.start();
    await waitingForSecond.promise;
    first.resolve();
    await new Promise(resolve => setImmediate(resolve));
    t.mock.timers.tick(199);
    assert.deepEqual(emitted, [[0, 'running', 0]]);
    t.mock.timers.tick(1);
    assert.deepEqual(emitted, [[0, 'running', 0], [200, 'running', 4]]);
    second.resolve();
    await completing.promise;
    t.mock.timers.tick(50);
    allowComplete.resolve();
    assert.equal(await pending, 'done');
    t.mock.timers.tick(200);
    assert.deepEqual(emitted, [[0, 'running', 0], [200, 'running', 4], [250, 'completed', 8]]);
});

test('续传跳过已有分片并准确累计最后一个短片', async () => {
    const uploaded = [];
    const uploader = upload(async (url, request) => {
        if (url === `${root}/init`) {
            return result(session('existing', 3, [
                { partNumber: 3, eTag: 'short-tag' }, { partNumber: 1, eTag: 'first-tag' }
            ]));
        }
        if (url.endsWith('/complete')) {
            assert.deepEqual(JSON.parse(request.body).parts, [
                { partNumber: 1, eTag: 'first-tag' }, { partNumber: 2, eTag: 'new-tag' },
                { partNumber: 3, eTag: 'short-tag' }
            ]);
            return result('done');
        }
        uploaded.push(url);
        const state = uploader.getState();
        assert.equal(state.resumed, true);
        assert.equal(state.uploadedBytes, 6);
        assert.equal(state.completedParts, 2);
        assert.equal(state.progress, 60);
        return result({ partNumber: 2, eTag: 'new-tag' });
    });
    assert.equal(await uploader.start(), 'done');
    assert.deepEqual(uploaded, [`${root}/existing/parts/2`]);
});

test('多个文件默认共用三个并发分片槽位', { timeout: 2000 }, async () => {
    let active = 0;
    let maxActive = 0;
    let total = 0;
    let nextSession = 0;
    const threeActive = deferred();
    const release = deferred();
    const fetchImpl = async (url, request) => {
        if (url.endsWith('/init')) return result(session(`file-${++nextSession}`, 3));
        if (url.endsWith('/complete')) return result('done');
        assert.equal(request.method, 'PUT');
        total++;
        maxActive = Math.max(maxActive, ++active);
        if (active === 3) threeActive.resolve();
        await release.promise;
        active--;
        return result({ eTag: 'etag' });
    };
    const pending = Promise.all([upload(fetchImpl).start(), upload(fetchImpl).start()]);
    await threeActive.promise;
    await new Promise(resolve => setImmediate(resolve));
    release.resolve();
    await pending;
    assert.equal(maxActive, 3);
    assert.equal(total, 6);
});

test('网络、HTTP 503 与 429 只重试失败的分片', async () => {
    const attempts = { 1: 0, 2: 0, 3: 0 };
    const uploader = upload(async url => {
        if (url.endsWith('/init')) return result(session('retry', 3));
        if (url.endsWith('/complete')) return result('done');
        const part = Number(url.split('/').at(-1));
        const attempt = ++attempts[part];
        if (part === 2 && attempt === 1) throw new TypeError('network unavailable');
        if (part === 2 && attempt === 2) return new Response(null, { status: 503 });
        if (part === 2 && attempt === 3) return new Response(null, { status: 429 });
        return result({ partNumber: part, eTag: `etag-${part}` });
    });
    assert.equal(await uploader.start(), 'done');
    assert.deepEqual(attempts, { 1: 1, 2: 4, 3: 1 });
    assert.equal(uploader.getState().uploadedBytes, 10);
});

test('HTTP 200 下的数值 5xx/429 结果码可重试，普通业务错误不重试', async () => {
    let attempts = 0;
    const uploader = upload(async url => {
        if (url.endsWith('/init')) return result(session('server-code', 1));
        const code = [500, 429, 409][attempts++];
        return result(null, code, `业务结果 ${code}`);
    }, { file: fileOf('abcd') });
    await assert.rejects(uploader.start(), /业务结果 409/);
    assert.equal(attempts, 3);
    assert.equal(uploader.getState().status, 'failed');
});

test('退避不占用共享分片槽，且遵循配置的延迟', { timeout: 2000 }, async t => {
    t.mock.timers.enable({ apis: ['setTimeout'] });
    const limiter = uploads().createConcurrencyLimiter(1);
    const failed = deferred();
    const retried = deferred();
    let attempts = 0;
    const a = upload(async url => {
        if (url.endsWith('/init')) return result(session('a', 1));
        if (url.endsWith('/complete')) return result('a');
        if (++attempts === 1) {
            failed.resolve();
            return new Response(null, { status: 500 });
        }
        retried.resolve();
        return result({ eTag: 'a' });
    }, { file: fileOf('abcd'), limiter, retryDelays: [80] });
    const b = upload(async url => {
        if (url.endsWith('/init')) return result(session('b', 1));
        if (url.endsWith('/complete')) return result('b');
        return result({ eTag: 'b' });
    }, { file: fileOf('abcd'), limiter });
    const pending = a.start();
    await failed.promise;
    assert.equal(await b.start(), 'b');
    t.mock.timers.tick(79);
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(attempts, 1);
    t.mock.timers.tick(1);
    await retried.promise;
    assert.equal(await pending, 'a');
});

test('重试达到上限转为 failed，允许恢复并完成', async () => {
    let attempts = 0;
    let inits = 0;
    const uploader = upload(async url => {
        if (url.endsWith('/init')) {
            inits++;
            return result(session('failed', 1));
        }
        if (url.endsWith('/complete')) return result('done');
        if (++attempts <= 2) return new Response(null, { status: 500 });
        return result({ eTag: 'ok' });
    }, { file: fileOf('abcd'), maxRetries: 1 });
    await assert.rejects(uploader.start(), /500/);
    assert.equal(attempts, 2);
    assert.equal(uploader.getState().status, 'failed');
    assert.equal(typeof uploader.getState().error, 'string');
    assert.equal(await uploader.resume(), 'done');
    assert.equal(inits, 1);
});

test('严格拒绝字符串形式的 Result.code 且不发送分片', async () => {
    let calls = 0;
    const uploader = upload(async () => {
        calls++;
        return result(session('invalid', 3), '200', '错误的结果码');
    });
    await assert.rejects(uploader.start(), /错误的结果码/);
    assert.equal(calls, 1);
    assert.equal(uploader.getState().status, 'failed');
});

test('暂停时中止在途分片，回调内恢复也必须等待旧轮结束', { timeout: 2000 }, async () => {
    const inFlight = deferred();
    const oldRound = deferred();
    const aborted = [];
    const sent = [];
    let resume;
    let inits = 0;
    const uploader = upload(async (url, request) => {
        if (url.endsWith('/init')) {
            inits++;
            return result(session('pause', 3, [{ partNumber: 1, eTag: 'saved' }]));
        }
        if (url.endsWith('/complete')) return result('done');
        const part = Number(url.split('/').at(-1));
        sent.push(part);
        if (sent.length <= 2) {
            request.signal.addEventListener('abort', () => { aborted.push(part); }, { once: true });
            if (sent.length === 2) inFlight.resolve();
            await oldRound.promise;
            throw new DOMException('paused', 'AbortError');
        }
        return result({ partNumber: part, eTag: `etag-${part}` });
    }, {
        onStateChange: state => {
            if (state.status === 'paused') resume = uploader.resume();
        }
    });
    const start = uploader.start();
    await inFlight.promise;
    uploader.pause();
    assert.equal(uploader.getState().status, 'paused');
    assert.equal(uploader.getState().uploadedBytes, 4);
    assert.deepEqual(aborted, [2, 3]);
    await new Promise(resolve => setImmediate(resolve));
    assert.deepEqual(sent, [2, 3]);
    oldRound.resolve();
    assert.equal(await start, null);
    assert.equal(await resume, 'done');
    assert.equal(inits, 1);
    assert.deepEqual(sent, [2, 3, 2, 3]);
});

test('失效的 limiter 等待项在暂停后不发送请求', { timeout: 2000 }, async () => {
    const firstStarted = deferred();
    const finishFirst = deferred();
    const limiter = uploads().createConcurrencyLimiter(1);
    const sent = [];
    const uploader = upload(async (url, request) => {
        if (url.endsWith('/init')) return result(session('queued', 3));
        const part = Number(url.split('/').at(-1));
        sent.push(part);
        firstStarted.resolve();
        await finishFirst.promise;
        assert.equal(request.signal.aborted, true);
        return result({ partNumber: part, eTag: `etag-${part}` });
    }, { limiter });
    const pending = uploader.start();
    await firstStarted.promise;
    uploader.pause();
    finishFirst.resolve();
    assert.equal(await pending, null);
    assert.deepEqual(sent, [1]);
});

test('取消会先中止分片，再 DELETE 清理已知会话', { timeout: 2000 }, async () => {
    const inFlight = deferred();
    const events = [];
    const uploader = upload(async (url, request) => {
        if (url.endsWith('/init')) return result(session('cancel', 1));
        if (request.method === 'DELETE') {
            assert.equal(url, `${root}/cancel`);
            assert.equal(new Headers(request.headers).get('Authorization'), 'Bearer example');
            events.push('DELETE');
            return result(null);
        }
        return new Promise((resolve, reject) => {
            request.signal.addEventListener('abort', () => {
                events.push('abort');
                reject(new DOMException('cancelled', 'AbortError'));
            }, { once: true });
            inFlight.resolve();
        });
    }, { file: fileOf('abcd'), getAuthHeaders: () => ({ Authorization: 'Bearer example' }) });
    const pending = uploader.start();
    await inFlight.promise;
    await Promise.all([uploader.cancel(), uploader.cancel()]);
    assert.equal(await pending, null);
    assert.equal(uploader.getState().status, 'cancelled');
    assert.deepEqual(events, ['abort', 'DELETE']);
});

test('完成请求发出后取消只等待提交结果，不中止或发送 DELETE', { timeout: 2000 }, async () => {
    const completeStarted = deferred();
    const completeResponse = deferred();
    let deleteCalls = 0;
    let completeSignal;
    const uploader = upload(async (url, request) => {
        if (url.endsWith('/init')) return result(session('committing', 1));
        if (request.method === 'DELETE') {
            deleteCalls++;
            return result(null);
        }
        if (url.endsWith('/complete')) {
            completeSignal = request.signal;
            completeStarted.resolve();
            return completeResponse.promise;
        }
        return result({ eTag: 'saved' });
    }, { file: fileOf('abcd') });
    const pending = uploader.start();
    await completeStarted.promise;
    const cancellation = uploader.cancel();
    const statusWhileCompleting = uploader.getState().status;
    completeResponse.resolve(result('done'));
    assert.equal(await pending, 'done');
    assert.equal(await cancellation, 'done');
    assert.equal(statusWhileCompleting, 'running');
    assert.equal(uploader.getState().status, 'completed');
    assert.equal(completeSignal.aborted, false);
    assert.equal(deleteCalls, 0);
});

test('取消清理初次返回 503 后可重试，且成功后不重复 DELETE', { timeout: 2000 }, async () => {
    const partStarted = deferred();
    const retryStarted = deferred();
    const retryResponse = deferred();
    let deletes = 0;
    const uploader = upload(async (url, request) => {
        if (url.endsWith('/init')) return result(session('cleanup-retry', 1));
        if (request.method === 'DELETE') {
            if (++deletes === 1) return new Response(null, { status: 503 });
            retryStarted.resolve();
            return retryResponse.promise;
        }
        return new Promise((resolve, reject) => {
            request.signal.addEventListener('abort', () => reject(request.signal.reason), { once: true });
            partStarted.resolve();
        });
    }, { file: fileOf('abcd') });
    const pending = uploader.start();
    await partStarted.promise;
    await assert.rejects(uploader.cancel(), /503/);
    assert.equal(await pending, null);
    assert.equal(uploader.getState().status, 'cancelled');
    const retry = uploader.cancel();
    const joined = uploader.cancel();
    const cleanup = Promise.all([retry, joined]);
    await Promise.race([retryStarted.promise, cleanup]);
    assert.equal(deletes, 2);
    retryResponse.resolve(result(null));
    await cleanup;
    await uploader.cancel();
    assert.equal(deletes, 2);
    assert.equal(uploader.getState().status, 'cancelled');
});

test('取消与初始化响应体竞争时仍会清理返回的会话', { timeout: 2000 }, async () => {
    const parsing = deferred();
    const response = deferred();
    const calls = [];
    const uploader = upload(async (url, request) => {
        calls.push([request.method, url]);
        if (url.endsWith('/init')) {
            return {
                ok: true,
                json() {
                    parsing.resolve();
                    return response.promise;
                }
            };
        }
        return result(null);
    });
    const pending = uploader.start();
    await parsing.promise;
    const cancellation = uploader.cancel();
    response.resolve({ code: 200, data: session('late', 3) });
    await cancellation;
    assert.equal(await pending, null);
    assert.deepEqual(calls, [['POST', `${root}/init`], ['DELETE', `${root}/late`]]);
});

test('初始状态回调中同步取消不发送初始化请求', async () => {
    let requests = 0;
    let cancellation;
    const uploader = upload(async () => {
        requests++;
        return result(session('unused', 3));
    }, {
        onStateChange: state => {
            if (state.status === 'running') cancellation = uploader.cancel();
        }
    });
    assert.equal(await uploader.start(), null);
    await cancellation;
    assert.equal(requests, 0);
    assert.equal(uploader.getState().status, 'cancelled');
});
