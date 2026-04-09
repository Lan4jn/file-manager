const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');

const downloads = require('../../main/resources/static/js/download-manager.js');

test('should create archive task for file page batch download instead of calling batch-download blob endpoint', () => {
    const appJs = fs.readFileSync('src/main/resources/static/js/app.js', 'utf8');

    assert.match(appJs, /api\.post\('\/transfer\/archive-tasks'/);
    assert.doesNotMatch(appJs, /\/file\/batch-download/);
});

test('should poll archive task until terminal status', async () => {
    const updates = [];
    const statuses = ['RUNNING', 'RUNNING', 'COMPLETED'];
    let index = 0;

    const result = await downloads.pollArchiveTask({
        taskId: 99,
        getTaskRequest: async () => ({ data: { id: 99, status: statuses[index++] } }),
        onUpdate: (task) => updates.push(task.status),
        wait: async () => {}
    });

    assert.equal(result.status, 'COMPLETED');
    assert.deepEqual(updates, ['RUNNING', 'RUNNING', 'COMPLETED']);
});

test('should stop polling when status becomes FAILED or EXPIRED', async () => {
    const failedResult = await downloads.pollArchiveTask({
        taskId: 100,
        getTaskRequest: async () => ({ data: { id: 100, status: 'FAILED' } }),
        wait: async () => {
            throw new Error('FAILED 状态不应继续等待');
        }
    });

    const expiredResult = await downloads.pollArchiveTask({
        taskId: 101,
        getTaskRequest: async () => ({ data: { id: 101, status: 'EXPIRED' } }),
        wait: async () => {
            throw new Error('EXPIRED 状态不应继续等待');
        }
    });

    assert.equal(failedResult.status, 'FAILED');
    assert.equal(expiredResult.status, 'EXPIRED');
});

test('should trigger native download after archive task completed instead of blob/objectURL workflow', async () => {
    const nativeDownloads = [];

    const result = await downloads.runArchiveTaskDownload({
        payload: {
            scene: 'FILE',
            taskType: 'BATCH'
        },
        createTaskRequest: async () => ({ data: { taskId: 88 } }),
        getTaskRequest: async () => ({ data: { id: 88, status: 'COMPLETED' } }),
        wait: async () => {
            throw new Error('已完成任务不应进入等待');
        },
        openDownload: (url) => {
            nativeDownloads.push(url);
        }
    });

    assert.equal(result.taskId, 88);
    assert.deepEqual(nativeDownloads, ['/api/transfer/archive-tasks/88/download']);
});
