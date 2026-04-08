const test = require('node:test');
const assert = require('node:assert/strict');

const {
    countUnreadShareSources,
    buildMessageSubtitle,
    mergeIncomingMessage
} = require('../../main/resources/static/js/message-center-helpers.js');

test('should count unread share sender sources only once', () => {
    const messages = [
        { id: 1, senderUsername: 'alice', read: false, messageType: 'SHARE' },
        { id: 2, senderUsername: 'alice', read: false, messageType: 'SHARE' },
        { id: 3, senderUsername: 'bob', read: false, messageType: 'SHARE' },
        { id: 4, senderUsername: 'charlie', read: true, messageType: 'SHARE' },
        { id: 5, senderUsername: 'david', read: false, messageType: 'FEEDBACK' }
    ];

    assert.equal(countUnreadShareSources(messages), 2);
});

test('should build subtitle for forwarded share message', () => {
    assert.equal(
        buildMessageSubtitle({
            originSenderUsername: 'alice',
            forwardedByUsername: 'bob'
        }),
        '初始分享人 alice，由 bob 转存'
    );
});

test('should merge incoming message by replacing existing id', () => {
    const existingMessages = [
        { id: 10, title: 'old', read: false },
        { id: 20, title: 'keep', read: false }
    ];

    const merged = mergeIncomingMessage(existingMessages, { id: 10, title: 'new', read: true });

    assert.deepEqual(merged, [
        { id: 10, title: 'new', read: true },
        { id: 20, title: 'keep', read: false }
    ]);
});
