const test = require('node:test');
const assert = require('node:assert/strict');

const helpers = require('../../main/resources/static/js/share-banner-helpers.js');

test('should build sender banner for user share', () => {
    const banner = helpers.buildShareSentBanner({
        targetType: 'USER',
        fileName: '报告.pdf',
        recipientNames: ['alice', 'bob']
    });

    assert.equal(banner.title, '分享已发送');
    assert.match(banner.content, /报告\.pdf/);
    assert.match(banner.content, /alice、bob/);
});

test('should build sender banner for link share', () => {
    const banner = helpers.buildShareSentBanner({
        targetType: 'LINK',
        fileName: '照片',
        hasPassword: true
    });

    assert.equal(banner.title, '分享链接已创建');
    assert.match(banner.content, /提取码/);
});

test('should build received banner from inbox message', () => {
    const banner = helpers.buildShareReceivedBanner({
        senderUsername: 'alice',
        title: 'alice 向你分享了文件',
        body: '请查收最新版本'
    });

    assert.equal(banner.title, 'alice 向你分享了文件');
    assert.equal(banner.content, '请查收最新版本');
});

test('should derive only share reminders from inbox', () => {
    const reminders = helpers.deriveShareRemindersFromInbox([
        { id: 1, messageType: 'SHARE', title: '分享1', body: '内容1' },
        { id: 2, messageType: 'FEEDBACK', title: '反馈', body: '内容2' },
        { id: 3, title: '分享2', body: '内容3' }
    ], 5);

    assert.equal(reminders.length, 2);
    assert.equal(reminders[0].title, '分享1');
    assert.equal(reminders[1].title, '分享2');
});

test('should derive unread state and share content summary from inbox', () => {
    const reminders = helpers.deriveShareRemindersFromInbox([
        {
            id: 8,
            messageType: 'SHARE',
            title: 'alice 分享给你',
            body: '请查收',
            read: false,
            shareItems: [
                { fileName: '报告.pdf', isFolder: false },
                { fileName: '资料', isFolder: true },
                { fileName: '预算.xlsx', isFolder: false }
            ]
        }
    ], 5);

    assert.equal(reminders[0].unread, true);
    assert.equal(reminders[0].shareContentText, '文件 / 报告.pdf、目录 / 资料 等 3 项');
});
