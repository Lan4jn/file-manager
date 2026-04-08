(function (globalScope) {
    function isShareMessage(message) {
        const messageType = (message?.messageType || '').toString().trim().toUpperCase();
        return !messageType || messageType === 'SHARE';
    }

    function normalizeSender(senderUsername) {
        return (senderUsername || '').toString().trim();
    }

    function countUnreadShareSources(messages) {
        if (!Array.isArray(messages)) {
            return 0;
        }
        const senderSet = new Set();
        messages.forEach(message => {
            if (!message || message.read || !isShareMessage(message)) {
                return;
            }
            const senderUsername = normalizeSender(message.senderUsername);
            if (senderUsername) {
                senderSet.add(senderUsername);
            }
        });
        return senderSet.size;
    }

    function buildMessageSubtitle(message) {
        const originSenderUsername = normalizeSender(message?.originSenderUsername);
        const forwardedByUsername = normalizeSender(message?.forwardedByUsername);
        if (originSenderUsername && forwardedByUsername) {
            return '初始分享人 ' + originSenderUsername + '，由 ' + forwardedByUsername + ' 转存';
        }
        const senderUsername = normalizeSender(message?.senderUsername);
        if (senderUsername) {
            return senderUsername + ' 分享给你';
        }
        return '系统消息';
    }

    function mergeIncomingMessage(existingMessages, incomingMessage) {
        const normalizedExisting = Array.isArray(existingMessages) ? existingMessages : [];
        if (!incomingMessage || incomingMessage.id === null || incomingMessage.id === undefined) {
            return normalizedExisting.slice();
        }
        const incomingId = incomingMessage.id;
        const nextMessages = normalizedExisting.filter(item => item && item.id !== incomingId);
        if (!incomingMessage.deleted) {
            nextMessages.unshift(incomingMessage);
        }
        return nextMessages;
    }

    const exported = {
        countUnreadShareSources,
        buildMessageSubtitle,
        mergeIncomingMessage
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.MessageCenterHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
