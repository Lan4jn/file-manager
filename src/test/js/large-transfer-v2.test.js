const test = require('node:test');
const assert = require('node:assert/strict');
const downloads = require('../../main/resources/static/js/download-manager.js');

test('should throw error for very large files if stream-to-disk is not supported', async () => {
    const originalSupportsStream = global.supportsStreamToDisk;
    // mock it to be false
    // Since supportsStreamToDisk is a function, I need to mock the global context if possible, 
    // but here it is defined inside the factory. 
    // Wait, the factory defines supportsStreamToDisk as a local function.
    // I can't mock the internal function. I must mock its dependencies.
    
    // Original supportsStreamToDisk: 
    // return typeof window !== 'undefined' && window.isSecureContext && ...
    
    const originalWindow = global.window;
    global.window = { isSecureContext: false }; // Disable secure context
    
    try {
        assert.throws(() => {
            downloads.createChunkedDownloader({
                url: 'http://example.com/file',
                totalSize: 200 * 1024 * 1024, // 200MB > 100MB
                chunkSize: 4
            });
        }, /文件过大/);
    } finally {
        global.window = originalWindow;
    }
});
