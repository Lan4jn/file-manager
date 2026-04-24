(function (globalScope) {
    function normalizeText(value) {
        return String(value || '').trim();
    }

    function isShareMessage(message) {
        var type = normalizeText(message && message.messageType).toUpperCase();
        return !type || type === 'SHARE';
    }

    function buildShareSentBanner(payload) {
        var targetType = normalizeText(payload && payload.targetType).toUpperCase();
        var fileName = normalizeText(payload && payload.fileName) || '当前项目';
        var recipients = Array.isArray(payload && payload.recipientNames) ? payload.recipientNames.filter(Boolean) : [];
        if (targetType === 'USER') {
            return {
                title: '分享已发送',
                content: '已将“' + fileName + '”分享给 ' + (recipients.length ? recipients.join('、') : '指定用户')
            };
        }
        return {
            title: '分享链接已创建',
            content: '“' + fileName + '”的分享链接已生成' + (payload && payload.hasPassword ? '，已附带提取码' : '')
        };
    }

    function buildShareReceivedBanner(message) {
        var sender = normalizeText(message && message.senderUsername) || '有人';
        var title = normalizeText(message && message.title) || '收到新的分享';
        var body = normalizeText(message && message.body) || (sender + ' 分享给你');
        return {
            title: title,
            content: body
        };
    }

    function deriveShareRemindersFromInbox(messages, limit) {
        var max = Number(limit || 5);
        return (Array.isArray(messages) ? messages : [])
            .filter(function (item) {
                return !!item && isShareMessage(item);
            })
            .slice(0, max)
            .map(function (item) {
                var banner = buildShareReceivedBanner(item);
                return {
                    title: banner.title,
                    content: banner.content,
                    createTime: item.createTime || item.createTimeText || ''
                };
            });
    }

    var exported = {
        isShareMessage: isShareMessage,
        buildShareSentBanner: buildShareSentBanner,
        buildShareReceivedBanner: buildShareReceivedBanner,
        deriveShareRemindersFromInbox: deriveShareRemindersFromInbox
    };

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = exported;
    }

    if (globalScope) {
        globalScope.ShareBannerHelpers = exported;
    }
})(typeof window !== 'undefined' ? window : globalThis);
