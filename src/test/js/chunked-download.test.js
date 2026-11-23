const test = require('node:test');
const assert = require('node:assert/strict');

const downloads = require('../../main/resources/static/js/download-manager.js');

test('should use chunked download when file size reaches threshold', () => {
    assert.equal(downloads.shouldUseChunkedDownload(104857600, 100), true);
    assert.equal(downloads.shouldUseChunkedDownload(104857599, 100), false);
});

test('should build inclusive range header', () => {
    assert.equal(downloads.buildRangeHeader(0, 8, 20), 'bytes=0-7');
    assert.equal(downloads.buildRangeHeader(16, 8, 20), 'bytes=16-19');
});

test('should pause and resume chunked download from the next pending chunk', async () => {
    const originalFetch = global.fetch;
    let requestCount = 0;
    const savedBlobs = [];

    global.fetch = async (url, options) => {
        requestCount += 1;
        const start = (requestCount - 1) * 4;
        const end = start + 3;
        return {
            ok: true,
            status: 206,
            headers: {
                get: (name) => name === 'Content-Range' ? 'bytes ' + start + '-' + end + '/12' : null
            },
            arrayBuffer: async () => new Uint8Array([requestCount, requestCount, requestCount, requestCount]).buffer
        };
    };

    try {
        let downloader;
        const progressSnapshots = [];
        downloader = downloads.createChunkedDownloader({
            url: 'http://example.com/file',
            fileName: 'demo.bin',
            totalSize: 12,
            chunkSize: 4,
            saveBlob: (blob, fileName) => {
                savedBlobs.push({ blob, fileName });
            },
            onProgress: (progress) => {
                progressSnapshots.push(progress);
                if (progressSnapshots.length === 1) {
                    downloader.pause();
                }
            }
        });

        await downloader.start();
        assert.equal(downloader.getState().status, 'paused');
        assert.equal(requestCount, 1);
        assert.equal(savedBlobs.length, 0);

        await downloader.resume();
        assert.equal(downloader.getState().status, 'completed');
        assert.equal(requestCount, 3);
        assert.equal(savedBlobs.length, 1);
        assert.equal(savedBlobs[0].fileName, 'demo.bin');
    } finally {
        global.fetch = originalFetch;
    }
});

test('should cancel chunked download before saving file', async () => {
    const originalFetch = global.fetch;
    let requestCount = 0;
    const savedBlobs = [];

    global.fetch = async () => {
        requestCount += 1;
        return {
            ok: true,
            status: 206,
            headers: {
                get: (name) => name === 'Content-Range' ? 'bytes 0-3/12' : null
            },
            arrayBuffer: async () => new Uint8Array([1, 2, 3, 4]).buffer
        };
    };

    try {
        let downloader;
        downloader = downloads.createChunkedDownloader({
            url: 'http://example.com/file',
            fileName: 'demo.bin',
            totalSize: 12,
            chunkSize: 4,
            saveBlob: (blob, fileName) => {
                savedBlobs.push({ blob, fileName });
            },
            onProgress: () => {
                downloader.cancel();
            }
        });

        await downloader.start();
        assert.equal(downloader.getState().status, 'cancelled');
        assert.equal(requestCount, 1);
        assert.equal(savedBlobs.length, 0);
    } finally {
        global.fetch = originalFetch;
    }
});
