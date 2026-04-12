package com.lan4jn.file.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.lan4jn.file.dto.CreateShareRequest;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.model.ShareAccessInfo;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.model.UserShareMessage;
import com.lan4jn.file.repository.ShareFileRecordRepository;
import com.lan4jn.file.repository.ShareLinkRepository;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.repository.UserShareMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

final class ShareLinkService {

    private static final Logger log = LoggerFactory.getLogger(ShareLinkService.class);
    private static final String TARGET_TYPE_LINK = "LINK";
    private static final String TARGET_TYPE_USER = "USER";

    private final FileService fileService;
    private final ShareLinkRepository shareLinkRepository;
    private final ShareFileRecordRepository shareFileRecordRepository;
    private final UserRepository userRepository;
    private final UserShareMessageRepository userShareMessageRepository;
    private final ShareSupport shareSupport;
    private final Integer defaultExpiration;
    private final MessagePushService messagePushService;

    ShareLinkService(FileService fileService,
                     ShareLinkRepository shareLinkRepository,
                     ShareFileRecordRepository shareFileRecordRepository,
                     UserRepository userRepository,
                     UserShareMessageRepository userShareMessageRepository,
                     ShareSupport shareSupport,
                     Integer defaultExpiration,
                     MessagePushService messagePushService) {
        this.fileService = fileService;
        this.shareLinkRepository = shareLinkRepository;
        this.shareFileRecordRepository = shareFileRecordRepository;
        this.userRepository = userRepository;
        this.userShareMessageRepository = userShareMessageRepository;
        this.shareSupport = shareSupport;
        this.defaultExpiration = defaultExpiration;
        this.messagePushService = messagePushService;
    }

    ShareLinkService(FileService fileService,
                     ShareLinkRepository shareLinkRepository,
                     ShareFileRecordRepository shareFileRecordRepository,
                     ShareSupport shareSupport,
                     Integer defaultExpiration) {
        this(fileService, shareLinkRepository, shareFileRecordRepository, null, null,
                shareSupport, defaultExpiration, null);
    }

    ShareLinkService(FileService fileService,
                     ShareLinkRepository shareLinkRepository,
                     ShareFileRecordRepository shareFileRecordRepository,
                     UserRepository userRepository,
                     UserShareMessageRepository userShareMessageRepository,
                     ShareSupport shareSupport,
                     Integer defaultExpiration) {
        this(fileService, shareLinkRepository, shareFileRecordRepository, userRepository,
                userShareMessageRepository, shareSupport, defaultExpiration, null);
    }

    ShareLink createShareLink(CreateShareRequest request, String createBy) {
        return createShareLink(request, createBy, null);
    }

    ShareLink createShareLink(CreateShareRequest request, String createBy,
                              ShareService.UserShareMessageDispatchContext messageDispatchContext) {
        String shareCode = generateShareCode();
        Integer expirationHours = request.getExpirationHours();
        if (expirationHours == null || expirationHours <= 0) {
            expirationHours = defaultExpiration;
        }

        String bucket = request.getBucket();
        String objectName = request.getObjectName();
        boolean isFolder = objectName.endsWith("/");
        String targetType = normalizeTargetType(request.getTargetType());
        String password = resolvePassword(targetType, request.getPassword());

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode(shareCode);
        shareLink.setObjectName(objectName);
        shareLink.setBucket(bucket);
        shareLink.setFileName(isFolder
                ? shareSupport.extractFolderName(objectName)
                : fileService.getFileInfo(objectName, bucket).getFileName());
        shareLink.setExpireTime(LocalDateTime.now().plusHours(expirationHours));
        shareLink.setCreateTime(LocalDateTime.now());
        shareLink.setCreateBy(createBy);
        shareLink.setViewCount(0L);
        shareLink.setHasPassword(StrUtil.isNotEmpty(password));
        shareLink.setPassword(password);
        shareLink.setTargetType(targetType);
        shareLink.setIsFolder(isFolder);
        shareLink.setZipFileName(null);

        ShareLink saved = shareLinkRepository.save(shareLink);
        if (TARGET_TYPE_USER.equals(targetType)) {
            dispatchUserShareMessages(saved, request.getRecipientUserIds(), messageDispatchContext);
        }
        log.info("Share link created: {}", shareCode);
        return saved;
    }

    ShareLink createBatchShareLink(List<String> objectNames, String bucket, String createBy,
                                   Integer expirationHours, String password,
                                   String targetType, List<Long> recipientUserIds) {
        return createBatchShareLink(objectNames, bucket, createBy, expirationHours, password, targetType, recipientUserIds, null);
    }

    ShareLink createBatchShareLink(List<String> objectNames, String bucket, String createBy,
                                   Integer expirationHours, String password,
                                   String targetType, List<Long> recipientUserIds,
                                   ShareService.UserShareMessageDispatchContext messageDispatchContext) {
        if (objectNames == null || objectNames.isEmpty()) {
            throw new RuntimeException("分享对象不能为空");
        }

        String shareCode = generateShareCode();
        if (expirationHours == null || expirationHours <= 0) {
            expirationHours = defaultExpiration;
        }
        String normalizedTargetType = normalizeTargetType(targetType);
        String resolvedPassword = resolvePassword(normalizedTargetType, password);

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode(shareCode);
        shareLink.setObjectName(ShareSupport.BATCH_PREFIX + JSONUtil.toJsonStr(objectNames));
        shareLink.setBucket(bucket);
        shareLink.setFileName("批量分享_" + objectNames.size() + "项");
        shareLink.setExpireTime(LocalDateTime.now().plusHours(expirationHours));
        shareLink.setCreateTime(LocalDateTime.now());
        shareLink.setCreateBy(createBy);
        shareLink.setViewCount(0L);
        shareLink.setHasPassword(StrUtil.isNotEmpty(resolvedPassword));
        shareLink.setPassword(resolvedPassword);
        shareLink.setTargetType(normalizedTargetType);
        shareLink.setIsFolder(true);
        shareLink.setZipFileName(null);

        ShareLink saved = shareLinkRepository.save(shareLink);
        if (TARGET_TYPE_USER.equals(normalizedTargetType)) {
            dispatchUserShareMessages(saved, recipientUserIds, messageDispatchContext);
        }
        log.info("Batch share link created: {}", shareCode);
        return saved;
    }

    ShareAccessInfo getShareAccessInfo(String shareCode) {
        ShareLink shareLink = findValidShare(shareCode);
        ShareAccessInfo info = new ShareAccessInfo();
        info.setShareCode(shareLink.getShareCode());
        info.setFileName(shareLink.getFileName());
        info.setHasPassword(Boolean.TRUE.equals(shareLink.getHasPassword()));
        info.setIsFolder(Boolean.TRUE.equals(shareLink.getIsFolder()));
        info.setExpireTime(shareLink.getExpireTime());
        info.setDisplayPath(shareSupport.getShareDisplayPath(shareLink));
        return info;
    }

    ShareLink getShareInfo(String shareCode, String password) {
        return resolveShare(shareCode, password, true);
    }

    ShareLink peekShareInfo(String shareCode, String password) {
        return resolveShare(shareCode, password, false);
    }

    boolean validateShareCode(String shareCode) {
        Optional<ShareLink> shareLink = shareLinkRepository.findByShareCode(shareCode);
        return shareLink.isPresent() && !LocalDateTime.now().isAfter(shareLink.get().getExpireTime());
    }

    void deleteShareLink(String shareCode, String createBy) {
        ShareLink shareLink = shareLinkRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new RuntimeException("分享链接不存在"));
        if (!Objects.equals(shareLink.getCreateBy(), createBy)) {
            throw new RuntimeException("无权删除该分享");
        }
        shareFileRecordRepository.deleteByShareCode(shareCode);
        shareLinkRepository.delete(shareLink);
        log.info("Share link deleted: {}", shareCode);
    }

    List<ShareLink> getShareLinksByUser(String createBy) {
        return shareLinkRepository.findByCreateByOrderByCreateTimeDesc(createBy);
    }

    void cleanupExpiredShares() {
        log.info("Share cleanup skipped: modern share mode no longer stores ZIP archives");
    }

    ShareLink resolveShare(String shareCode, String password, boolean incrementView) {
        ShareLink shareLink = findValidShare(shareCode);
        if (Boolean.TRUE.equals(shareLink.getHasPassword())) {
            if (StrUtil.isBlank(password)) {
                throw new RuntimeException("请输入提取码");
            }
            if (!Objects.equals(password, shareLink.getPassword())) {
                throw new RuntimeException("提取码错误");
            }
        }

        if (incrementView) {
            shareLink.setViewCount((shareLink.getViewCount() == null ? 0L : shareLink.getViewCount()) + 1);
            shareLinkRepository.save(shareLink);
        }
        return shareLink;
    }

    void incrementViewCount(String shareCode) {
        ShareLink shareLink = findValidShare(shareCode);
        shareLink.setViewCount((shareLink.getViewCount() == null ? 0L : shareLink.getViewCount()) + 1);
        shareLinkRepository.save(shareLink);
    }

    private ShareLink findValidShare(String shareCode) {
        ShareLink shareLink = shareLinkRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new RuntimeException("分享链接不存在"));
        if (LocalDateTime.now().isAfter(shareLink.getExpireTime())) {
            throw new RuntimeException("分享链接已过期");
        }
        return shareLink;
    }

    private String generateShareCode() {
        return IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
    }

    private String normalizeTargetType(String targetType) {
        if (StrUtil.isBlank(targetType)) {
            return TARGET_TYPE_LINK;
        }
        String normalized = targetType.trim().toUpperCase();
        if (!TARGET_TYPE_LINK.equals(normalized) && !TARGET_TYPE_USER.equals(normalized)) {
            throw new RuntimeException("不支持的分享类型");
        }
        return normalized;
    }

    private String resolvePassword(String targetType, String password) {
        if (TARGET_TYPE_USER.equals(targetType)) {
            return null;
        }
        String normalized = StrUtil.trimToNull(password);
        if (normalized != null) {
            return normalized;
        }
        return String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private void dispatchUserShareMessages(ShareLink shareLink, List<Long> recipientUserIds,
                                           ShareService.UserShareMessageDispatchContext context) {
        if (userRepository == null || userShareMessageRepository == null) {
            throw new RuntimeException("用户分享依赖未初始化");
        }
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            throw new RuntimeException("请选择至少一个收件人");
        }
        List<UserShareMessage> messages = new ArrayList<>();
        List<String> recipientUsernames = new ArrayList<>();
        for (Long recipientUserId : recipientUserIds) {
            User user = userRepository.findById(recipientUserId)
                    .orElseThrow(() -> new RuntimeException("收件人不存在: " + recipientUserId));
            UserShareMessage message = new UserShareMessage();
            message.setRecipientUserId(user.getId());
            message.setSenderUsername(shareLink.getCreateBy());
            message.setShareCode(shareLink.getShareCode());
            message.setTitle(shareLink.getFileName());
            message.setMessageType(resolveMessageType(context));
            message.setBody(context == null ? null : context.getBody());
            message.setOriginSenderUsername(resolveOriginSenderUsername(shareLink, context));
            message.setForwardedByUsername(context == null ? null : context.getForwardedByUsername());
            message.setRelatedShareCode(resolveRelatedShareCode(shareLink, context));
            message.setConversationKey(resolveConversationKey(shareLink, context));
            message.setRead(false);
            message.setReadTime(null);
            message.setCreateTime(LocalDateTime.now());
            message.setDeleted(false);
            messages.add(message);
            recipientUsernames.add(user.getUsername());
        }
        userShareMessageRepository.saveAll(messages);
        if (messagePushService != null) {
            dispatchMessageCreatedPushes(messages, recipientUsernames, shareLink);
        }
    }

    private void dispatchMessageCreatedPushes(List<UserShareMessage> messages,
                                              List<String> recipientUsernames,
                                              ShareLink shareLink) {
        Runnable pushAction = () -> {
            for (int index = 0; index < messages.size(); index++) {
                messagePushService.pushMessageCreated(
                        recipientUsernames.get(index),
                        buildCreatedPayload(messages.get(index), shareLink)
                );
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    pushAction.run();
                }
            });
            return;
        }
        pushAction.run();
    }

    private Map<String, Object> buildCreatedPayload(UserShareMessage message, ShareLink shareLink) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", message.getId());
        payload.put("shareCode", shareLink.getShareCode());
        payload.put("title", message.getTitle());
        payload.put("messageType", message.getMessageType());
        return payload;
    }

    private String resolveMessageType(ShareService.UserShareMessageDispatchContext context) {
        if (context == null || StrUtil.isBlank(context.getMessageType())) {
            return "SHARE";
        }
        return context.getMessageType().trim().toUpperCase();
    }

    private String resolveOriginSenderUsername(ShareLink shareLink, ShareService.UserShareMessageDispatchContext context) {
        if (context == null || StrUtil.isBlank(context.getOriginSenderUsername())) {
            return shareLink.getCreateBy();
        }
        return context.getOriginSenderUsername().trim();
    }

    private String resolveRelatedShareCode(ShareLink shareLink, ShareService.UserShareMessageDispatchContext context) {
        if (context == null || StrUtil.isBlank(context.getRelatedShareCode())) {
            return shareLink.getShareCode();
        }
        return context.getRelatedShareCode().trim();
    }

    private String resolveConversationKey(ShareLink shareLink, ShareService.UserShareMessageDispatchContext context) {
        if (context == null || StrUtil.isBlank(context.getConversationKey())) {
            return shareLink.getShareCode();
        }
        return context.getConversationKey().trim();
    }
}
