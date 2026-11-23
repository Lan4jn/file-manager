const test = require('node:test');
const assert = require('node:assert/strict');
const downloads = require('../../main/resources/static/js/download-manager.js');

test('should use stream-to-disk when supported instead of caching chunks', async () => {
    const originalShowSaveFilePicker = global.showSaveFilePicker;
    const originalWritableStream = global.WritableStream;
    
    let writeCount = 0;
    let closed = false;
    
    global.window = {
        URL: { createObjectURL: () => '', revokeObjectURL: () => {} },
        showSaveFilePicker: async () => ({
            createWritable: async () => ({
                write: async (chunk) => {
                    writeCount++;
                },
                close: async () => {
                    closed = true;
                },
                abort: async () => {}
            })
        }),
        isSecureContext: true
    };
    // 覆盖全局 supportsStreamToDisk 的检测依赖
    // 在下载管理器中，它检测的是 window.isSecureContext, window.showSaveFilePicker, WritableStream
    global.window.showSaveFilePicker = global.window.showSaveFilePicker;
    global.WritableStream = class {};



    global.fetch = async (url, options) => {
        const range = options.headers.Range;
        // bytes=start-end
        const match = /bytes=(\d+)-(\d+)/.exec(range);
        const start = parseInt(match[1]);
        const end = parseInt(match[2]);
        const length = end - start + 1;
        
        return {
            ok: true,
            status: 206,
            headers: {
                get: (name) => name === 'Content-Range' ? `bytes ${start}-${end}/12` : null
            },
            arrayBuffer: async () => new Uint8Array(new Array(length).fill(1)).buffer
        };
    };

    // 假设修改后的 downloader 会识别这个并使用
    const downloader = downloads.createChunkedDownloader({
        url: 'http://example.com/file',
        totalSize: 12,
        chunkSize: 4,
        // 这里需要传递一个参数表明希望流式写入
        streamToDisk: true 
    });

    try {
        await downloader.start();
        assert.equal(closed, true);
        assert.equal(writeCount, 3); // 12 bytes / 4 = 3 chunks
    } finally {
        global.showSaveFilePicker = originalShowSaveFilePicker;
        global.WritableStream = originalWritableStream;
        global.isSecureContext = false;
    }
});

test('should abort writable stream when download is cancelled', async () => {
    let aborted = false;
    global.window = {
        URL: { createObjectURL: () => '', revokeObjectURL: () => {} },
        showSaveFilePicker: async () => ({
            createWritable: async () => ({
                write: async () => {},
                close: async () => {},
                abort: async () => {
                    aborted = true;
                }
            })
        }),
        isSecureContext: true
    };
    global.WritableStream = class {};

    global.fetch = async () => ({
        ok: true,
        status: 206,
        headers: {
            get: (name) => name === 'Content-Range' ? 'bytes 0-3/12' : null
        },
        arrayBuffer: async () => new Uint8Array([1, 2, 3, 4]).buffer
    });

    let downloader;
    downloader = downloads.createChunkedDownloader({
        url: 'http://example.com/file',
        totalSize: 12,
        chunkSize: 4,
        onProgress: () => {
            downloader.cancel();
        }
    });

    await downloader.start();
    assert.equal(aborted, true);
});

