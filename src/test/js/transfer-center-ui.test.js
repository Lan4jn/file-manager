const test = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');
const vm = require('node:vm');

const root = join(__dirname, '../../main/resources/static');
const read = (name) => readFileSync(join(root, name), 'utf8');
const html = read('index.html');
const app = read('js/app.js');
const css = read('css/workspace-theme.css');
const tokens = read('css/tokens.css');

function setupApp({ fetch, api = {}, pickFile } = {}) {
    const noop = () => {};
    const window = {
        __APP_CONTEXT_PATH__: '/work',
        location: { origin: 'https://files.example', protocol: 'https:', pathname: '/work/' },
        localStorage: { getItem: (key) => key === 'token' ? 'active-token' : null },
        isSecureContext: true,
        showSaveFilePicker: pickFile
    };
    const context = vm.createContext({ window, fetch, URL, Headers, AbortController, WritableStream, setTimeout, clearTimeout });
    vm.runInContext(read('js/vue.global.js'), context);
    vm.runInContext(read('js/upload-manager.js'), context);
    vm.runInContext(read('js/download-manager.js'), context);
    const apiStub = {
        interceptors: { request: { use: noop }, response: { use: noop } },
        ...api
    };
    let setup;
    Object.assign(context, {
        document: {},
        axios: { create: () => apiStub },
        createClientCertificateHelpers: () => ({}),
        StandardCertUi: { component: {} },
        ElementPlusIconsVue: {},
        ElementPlus: { ElMessage: { warning: noop, success: noop, error: noop } }
    });
    context.Vue.createApp = (component) => {
        setup = component.setup;
        return { component: noop, use: noop, mount: noop };
    };
    context.Vue.onMounted = noop;
    context.Vue.onUnmounted = noop;
    vm.runInContext(app, context);
    return setup();
}

const nextEvent = () => new Promise(resolve => setImmediate(resolve));

test('已确认的大文件在旧弹窗关闭后继续上传，且面板反映真实引擎状态', { timeout: 2000 }, async () => {
    const requests = [];
    const listRequests = [];
    let releasePart;
    const part = new Promise(resolve => { releasePart = resolve; });
    const response = data => ({ ok: true, status: 200, json: async () => ({ code: 200, data }) });
    const size = 50 * 1024 * 1024;
    const state = setupApp({
        api: { get: async (path) => { listRequests.push(path); return { code: 200, data: [] }; } },
        fetch: async (url, options) => {
            requests.push({ url, options });
            if (url.endsWith('/init')) return response({ sessionId: 's1', partSize: size, totalParts: 1, uploadedParts: [] });
            if (options.method === 'PUT') { await part; return response({ eTag: 'tag' }); }
            if (url.endsWith('/complete')) return response({ objectName: 'draft/renamed.bin' });
            assert.fail(url);
        }
    });
    state.isLoggedIn.value = true;
    state.currentMenu.value = 'files';
    state.currentBucket.value = 'source';
    state.currentFolder.value = 'draft/';
    state.showUploadDialog.value = true;
    state.pendingUploadFiles.value = [{ raw: { name: 'original.bin', size, lastModified: 22, slice: () => 'part' }, targetName: 'renamed.bin' }];
    try {
        await state.submitUpload();
        await nextEvent();
        assert.equal(state.showUploadDialog.value, false);
        assert.equal(state.transferCenterVisible.value, true);
        assert.equal(state.activeTransferCount.value, 1);
        assert.equal(state.unifiedTransferTasks.value[0].name, 'renamed.bin');
        assert.equal(requests[0].url, '/work/api/transfer/upload/multipart/init');
        assert.equal(new Headers(requests[0].options.headers).get('Authorization'), 'Bearer active-token');
        const data = JSON.parse(requests[0].options.body);
        assert.deepEqual([data.bucket, data.folder, data.targetFileName], ['source', 'draft/', 'renamed.bin']);
        assert.equal(requests[1].options.method, 'PUT');
        state.currentFolder.value = 'another/';
    } finally {
        releasePart();
    }
    await nextEvent();
    assert.equal(state.activeTransferCount.value, 0);
    assert.equal(state.unifiedTransferTasks.value[0].status, 'completed');
    assert.deepEqual(listRequests, []);
    state.clearCompletedTransfers();
    assert.equal(state.unifiedTransferTasks.value.length, 0);
});

test('拖入大文件后切换目录，延迟返回的上传配置不会改变原上传目标', { timeout: 2000 }, async () => {
    const size = 50 * 1024 * 1024;
    const requests = [];
    let finishConfig;
    const config = new Promise(resolve => { finishConfig = resolve; });
    const state = setupApp({
        api: { get: (path) => { assert.equal(path, '/file/upload-config'); return config; } },
        fetch: async (url, options) => {
            if (url.endsWith('/init')) requests.push(JSON.parse(options.body));
            return { ok: true, status: 200, json: async () => ({ code: 200,
                data: url.endsWith('/init') ? { sessionId: 'drop-session', partSize: size, totalParts: 1 }
                    : options.method === 'PUT' ? { eTag: 'tag' } : {} }) };
        }
    });
    state.isLoggedIn.value = true;
    state.permissionInfo.value = { superAdmin: true };
    state.currentBucket.value = 'source';
    state.currentFolder.value = 'draft/';
    const dropping = state.handleFileDrop({ dataTransfer: { files: [{ name: 'dropped.bin', size, slice: () => 'part' }] } });
    state.currentBucket.value = 'new-bucket';
    state.currentFolder.value = 'another/';
    finishConfig({ code: 200, data: { fileDropMode: 'INSTANT' } });
    await dropping;
    await nextEvent();
    assert.deepEqual(requests.map(({ bucket, folder, targetFileName }) => [bucket, folder, targetFileName]), [
        ['source', 'draft/', 'dropped.bin']
    ]);
});

test('退出并换账号后，旧拖放配置响应不会启动上传', { timeout: 2000 }, async () => {
    let finishConfig;
    const config = new Promise(resolve => { finishConfig = resolve; });
    const requests = [];
    const state = setupApp({
        api: { get: () => config },
        fetch: async (_, options) => { requests.push(options); throw new Error('unexpected upload'); }
    });
    state.isLoggedIn.value = true;
    state.permissionInfo.value = { superAdmin: true };
    state.currentBucket.value = 'old-bucket';
    const dropping = state.handleFileDrop({ dataTransfer: { files: [{ name: 'large.bin', size: 50 * 1024 * 1024 }] } });
    state.logout();
    state.token.value = 'new-token';
    state.isLoggedIn.value = true;
    state.currentBucket.value = 'new-bucket';
    finishConfig({ code: 200, data: { fileDropMode: 'INSTANT' } });
    await dropping;
    await nextEvent();
    assert.equal(requests.length, 0);
    assert.equal(state.unifiedTransferTasks.value.length, 0);
    assert.equal(state.showUploadDialog.value, false);
});

test('同页已有同目标的分片任务时不再次初始化上传会话', { timeout: 2000 }, async () => {
    const size = 50 * 1024 * 1024;
    let releasePart;
    const part = new Promise(resolve => { releasePart = resolve; });
    let inits = 0;
    const state = setupApp({ fetch: async (url, options) => {
        let data = {};
        if (url.endsWith('/init')) data = { sessionId: 'target-' + ++inits, partSize: size, totalParts: 1 };
        if (options.method === 'PUT') { await part; data = { eTag: 'tag' }; }
        return { ok: true, status: 200, json: async () => ({ code: 200, data }) };
    } });
    state.isLoggedIn.value = true;
    state.currentBucket.value = 'source';
    state.currentFolder.value = 'draft/';
    const submit = (name) => {
        state.pendingUploadFiles.value = [{ raw: { name, size, slice: () => 'part' }, targetName: 'same.bin' }];
        return state.submitUpload();
    };
    try {
        await submit('first.bin');
        await nextEvent();
        await submit('other.bin');
        await nextEvent();
        assert.equal(inits, 1);
        assert.equal(state.activeTransferCount.value, 1);
    } finally {
        releasePart();
    }
});

test('同目录完成后静默刷新，但过时的列表响应不会覆盖新目录', { timeout: 2000 }, async () => {
    const size = 50 * 1024 * 1024;
    let finishList;
    const listing = new Promise(resolve => { finishList = resolve; });
    const listRequests = [];
    const state = setupApp({
        api: { get: (path, { params }) => { listRequests.push([path, params.bucket, params.folder]); return listing; } },
        fetch: async (url, options) => ({ ok: true, status: 200, json: async () => ({
            code: 200,
            data: url.endsWith('/init') ? { sessionId: 'refresh-session', partSize: size, totalParts: 1 }
                : options.method === 'PUT' ? { eTag: 'tag' } : { objectName: 'draft/large.bin' }
        }) })
    });
    state.isLoggedIn.value = true;
    state.currentMenu.value = 'files';
    state.currentBucket.value = 'source';
    state.currentFolder.value = 'draft/';
    state.pendingUploadFiles.value = [{ raw: { name: 'large.bin', size, slice: () => 'part' }, targetName: 'large.bin' }];
    await state.submitUpload();
    await nextEvent();
    assert.deepEqual(listRequests, [['/file/list', 'source', 'draft/']]);
    assert.equal(state.fileLoading.value, false);
    state.currentFolder.value = 'other/';
    state.fileList.value = [{ objectName: 'other/current.bin' }];
    finishList({ code: 200, data: { items: [{ objectName: 'draft/large.bin' }], total: 1 } });
    await nextEvent();
    assert.equal(state.fileList.value[0].objectName, 'other/current.bin');
});

test('上传取消清理失败时在面板给出原因，允许再次取消以清理会话', { timeout: 2000 }, async () => {
    let cleanups = 0;
    const size = 50 * 1024 * 1024;
    const state = setupApp({ fetch: async (url, options) => {
        if (options.method === 'DELETE') {
            if (++cleanups === 1) return { ok: false, status: 503 };
            return { ok: true, status: 200, json: async () => ({ code: 200, data: null }) };
        }
        if (url.endsWith('/init')) return { ok: true, status: 200, json: async () => ({
            code: 200, data: { sessionId: 'cancel-session', partSize: size, totalParts: 1 }
        }) };
        return new Promise((resolve, reject) => options.signal.addEventListener('abort', () => reject(options.signal.reason), { once: true }));
    } });
    state.isLoggedIn.value = true;
    state.currentBucket.value = 'source';
    state.pendingUploadFiles.value = [{ raw: { name: 'large.bin', size, slice: () => 'part' }, targetName: 'large.bin' }];
    await state.submitUpload();
    await nextEvent();
    const task = () => state.unifiedTransferTasks.value[0];
    await state.handleTransferTaskAction(task(), 'cancel');
    assert.equal(task().status, 'cancelled');
    assert.match(task().error, /HTTP 503/);
    assert.equal(task().canCancel, true);
    await state.handleTransferTaskAction(task(), 'cancel');
    assert.equal(cleanups, 2);
    assert.equal(task().error, '');
    assert.equal(task().canCancel, false);
});

test('写盘下载续签时保留原桶，按新的 URL 重新计算鉴权并使用 16 MiB 段', { timeout: 2000 }, async () => {
    const requests = [];
    const sessions = [];
    const bytesWritten = [];
    const segmentSize = 16 * 1024 * 1024;
    const total = segmentSize + 1;
    let closed = false;
    let state;
    state = setupApp({
        pickFile: async () => ({ createWritable: async () => ({
            write: async value => bytesWritten.push(value.byteLength),
            close: async () => { closed = true; },
            abort: async () => assert.fail('成功续签不应丢弃写盘流')
        }) }),
        api: { post: async (path, body) => {
            sessions.push({ path, bucket: body.bucket, objectName: body.objectName });
            return { code: 200, data: { downloadUrl: sessions.length === 1
                ? 'https://objects.example/signed' : '/work/api/file/download?file=archive.bin' } };
        } },
        fetch: async (url, options) => {
            const headers = new Headers(options.headers);
            const range = headers.get('Range');
            requests.push([url, range, headers.get('Authorization')]);
            if (requests.length === 2) {
                state.currentBucket.value = 'other-bucket';
                return { status: 403 };
            }
            const [, start, end] = /^bytes=(\d+)-(\d+)$/.exec(range);
            let done = false;
            return { status: 206, headers: new Headers({ 'Content-Range': `bytes ${start}-${end}/${total}` }), body: {
                getReader: () => ({ read: async () => done ? { done: true } : (done = true, { value: new Uint8Array(Number(end) - Number(start) + 1) }) })
            } };
        }
    });
    state.isLoggedIn.value = true;
    state.currentBucket.value = 'source';
    state.fileConfig.value.frontendDownloadThresholdMB = 1;
    await state.downloadFile({ size: total, objectName: 'dir/archive.bin', fileName: 'archive.bin' });
    assert.deepEqual(sessions, Array.from({ length: 2 }, () => ({
        path: '/transfer/download/session', bucket: 'source', objectName: 'dir/archive.bin'
    })));
    assert.deepEqual(requests, [
        ['https://objects.example/signed', 'bytes=0-16777215', null],
        ['https://objects.example/signed', 'bytes=16777216-16777216', null],
        ['/work/api/file/download?file=archive.bin', 'bytes=16777216-16777216', 'Bearer active-token']
    ]);
    assert.deepEqual(bytesWritten, [segmentSize, 1]);
    assert.equal(closed, true);
    const task = state.filteredTransferTasks.value.find(item => item.direction === 'download');
    assert.equal(task.status, 'completed');
    assert.equal(state.downloadTasks.value[0].status, 'completed');
    await state.retryDownloadTask(state.downloadTasks.value[0]);
    assert.equal(sessions.length, 3);
    assert.equal(sessions[2].bucket, 'source');
    assert.deepEqual(bytesWritten, [segmentSize, 1, segmentSize, 1]);
});

test('写盘已传非零字节时换签失败不降级，面板重试使用同一 writer', { timeout: 2000 }, async () => {
    const segment = 16 * 1024 * 1024;
    const total = segment + 1;
    const ranges = [];
    let sessions = 0;
    let opens = 0;
    let closed = 0;
    let aborted = 0;
    const state = setupApp({
        pickFile: async () => ({ createWritable: async () => {
            opens++;
            return { write: async () => {}, close: async () => { closed++; }, abort: async () => { aborted++; } };
        } }),
        api: { post: async () => {
            if (++sessions === 2) throw new Error('续签请求暂时不可用');
            return { code: 200, data: {
                downloadUrl: `https://objects.example/signed-${sessions}`, fallbackUrl: '/work/api/transfer/download/stream/fallback'
            } };
        } },
        fetch: async (url, options) => {
            const requested = new Headers(options.headers).get('Range');
            ranges.push(requested);
            if (ranges.length === 2) return { status: 403 };
            const [, start, end] = /^bytes=(\d+)-(\d+)$/.exec(requested);
            return { status: 206, headers: new Headers({ 'Content-Range': `bytes ${start}-${end}/${total}` }),
                arrayBuffer: async () => new ArrayBuffer(Number(end) - Number(start) + 1) };
        }
    });
    state.isLoggedIn.value = true;
    state.currentBucket.value = 'source';
    state.fileConfig.value.frontendDownloadThresholdMB = 1;
    await state.downloadFile({ size: total, objectName: 'archive.bin', fileName: 'archive.bin' });
    const task = () => state.filteredTransferTasks.value[0];
    assert.equal(task().status, 'failed');
    assert.equal(task().transferredBytes, segment);
    assert.equal(aborted, 0);
    await state.handleTransferTaskAction(task(), 'retry');
    assert.equal(task().status, 'completed');
    assert.equal(opens, 1);
    assert.equal(closed, 1);
    assert.deepEqual(ranges, ['bytes=0-16777215', 'bytes=16777216-16777216', 'bytes=16777216-16777216']);
});

function sourceRegion(source, start, end) {
    const from = source.indexOf(start);
    assert.ok(from >= 0, `缺少 ${start}`);
    const to = source.indexOf(end, from + start.length);
    assert.ok(to > from, `缺少 ${end}`);
    return source.slice(from, to);
}

test('在应用之前加载分片引擎，同时保留旧上传和下载弹窗', () => {
    const upload = html.indexOf('<script src="./js/upload-manager.js?v=build152"></script>');
    assert.ok(upload >= 0 && upload < html.indexOf('<script src="./js/app.js?'));
    assert.match(html, /<el-dialog\s+v-model="showUploadDialog"/);
    assert.match(html, /v-if="downloadCenterVisible" class="download-task-center"/);
    assert.match(html, /class="message-center-trigger"/);
});

test('独立传输面板提供状态、任务分组、详情及操作，不创建遮罩', () => {
    const panel = sourceRegion(html, 'class="transfer-center-panel"', '</aside>');
    assert.match(html, /class="transfer-center-trigger"[\s\S]{0,750}activeTransferCount/);
    assert.match(panel, /传输中心/);
    assert.match(panel, /清空已完成/);
    assert.match(panel, /收起/);
    assert.match(panel, /关闭/);
    assert.match(panel, /transferCenterTab/);
    assert.match(panel, /filteredTransferTasks/);
    for (const text of ['暂停', '继续', '重试', '取消', '断点续传', '分片']) assert.ok(panel.includes(text), text);
    for (const field of ['progress', 'totalBytes', 'speedBytesPerSecond', 'error']) assert.ok(panel.includes(`task.${field}`), field);
    assert.doesNotMatch(panel, /backdrop|aria-modal="true"/);
});

test('大文件进入后台分片流程，小文件仍使用旧上传链路', () => {
    assert.match(app, /window\.FileManagerUploads/);
    assert.match(app, /\.shouldUseMultipartUpload\??\.?\s*\([^)]*\.size\b/);
    assert.match(app, /\.createMultipartUploader\s*\(/);
    assert.match(app, /apiBasePath:\s*contextPathHelpers\.joinContextPath\(appContextPath, '\/api'\)/);
    assert.match(app, /getAuthHeaders:[\s\S]{0,400}Bearer/);
    const multipart = sourceRegion(app, '.createMultipartUploader', 'const runUploadBatch');
    assert.match(multipart, /onProgress:\s*\w+/);
    assert.match(sourceRegion(app, 'const runUploadBatch', 'const toQueryUrl'), /await uploadFileRequest\(/);
    for (const name of ['transferCenterVisible', 'transferCenterTab', 'transferUploadTasks', 'unifiedTransferTasks', 'activeTransferCount', 'filteredTransferTasks', 'clearCompletedTransfers']) {
        assert.match(app, new RegExp(`^\\s+${name},?\\s*$`, 'm'));
    }
});

test('写盘下载使用流式段大小并为同一对象重新申请下载会话', () => {
    const chunked = sourceRegion(app, 'const runChunkedFileDownload', 'const resolveArchiveTaskStep');
    assert.match(chunked, /chunkSize:\s*[^,\n]*STREAM_SEGMENT_SIZE/);
    assert.match(chunked, /refreshUrl\b/);
    assert.match(app, /\/transfer\/download\/session/);
    assert.match(chunked, /createChunkedDownloader\(/);
});

test('传输样式基于主题 token，悬浮入口错位且 700px 以下无横向溢出', () => {
    const rules = [...css.matchAll(/([^{}]+)\{([^{}]*)\}/g)]
        .filter(([, selector]) => /\.transfer-center-/.test(selector));
    assert.ok(rules.some(([, selector]) => /\.transfer-center-trigger\b/.test(selector)));
    assert.ok(rules.some(([, selector]) => /\.transfer-center-panel\b/.test(selector)));
    const declarations = rules.map(([, , body]) => body).join('\n');
    assert.doesNotMatch(declarations, /backdrop-filter\s*:\s*blur|(?:linear|radial)-gradient\(/i);
    assert.match(declarations, /var\(--surface-panel\)/);
    for (const [, token] of declarations.matchAll(/var\((--[\w-]+)/g)) assert.ok(tokens.includes(`${token}:`), token);
    assert.match(css, /@media\s*\(max-width:\s*700px\)[\s\S]*?\.transfer-center-panel\b/);
    assert.match(declarations, /(?:inset-inline(?:-end)?|inset-block-end|bottom|right):/);
    assert.match(declarations, /(?:overflow(?:-x)?|min-inline-size|min-width):/);
});
