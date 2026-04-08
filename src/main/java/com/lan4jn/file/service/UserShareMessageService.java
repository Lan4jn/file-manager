package com.lan4jn.file.service;

import com.lan4jn.file.common.PageResult;
import com.lan4jn.file.dto.CreateShareRequest;
import com.lan4jn.file.dto.ShareRecipientDTO;
import com.lan4jn.file.entity.Role;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.model.UserShareMessage;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.repository.UserShareMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class UserShareMessageService {
    private static final String MESSAGE_TYPE_SHARE = "SHARE";
    private static final String MESSAGE_TYPE_FEEDBACK = "FEEDBACK";
    private static final String TARGET_TYPE_USER = "USER";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String DEFAULT_FEEDBACK_CATEGORY = "GENERAL";
    private static final String DEFAULT_FEEDBACK_TITLE = "用户反馈";


    private final UserRepository userRepository;
    private final UserShareMessageRepository userShareMessageRepository;
    private final ShareService shareService;
    private final MessagePushService messagePushService;

    public UserShareMessageService(UserRepository userRepository,
                                   UserShareMessageRepository userShareMessageRepository,
                                   ShareService shareService) {
        this(userRepository, userShareMessageRepository, shareService, null);
    }

    @Autowired
    public UserShareMessageService(UserRepository userRepository,
                                   UserShareMessageRepository userShareMessageRepository,
                                   ShareService shareService,
                                   MessagePushService messagePushService) {
        this.userRepository = userRepository;
        this.userShareMessageRepository = userShareMessageRepository;
        this.shareService = shareService;
        this.messagePushService = messagePushService;
    }

    public PageResult<UserShareMessage> listInbox(String username, int page, int size) {
        User user = requireUser(username);
        List<UserShareMessage> messages = userShareMessageRepository
                .findByRecipientUserIdAndDeletedFalseOrderByCreateTimeDesc(user.getId());
        int normalizedPage = page <= 0 ? 1 : page;
        int normalizedSize = size <= 0 ? 20 : size;
        int fromIndex = Math.min((normalizedPage - 1) * normalizedSize, messages.size());
        int toIndex = Math.min(fromIndex + normalizedSize, messages.size());
        return new PageResult<>(messages.subList(fromIndex, toIndex), messages.size(), normalizedPage, normalizedSize);
    }

    public List<ShareRecipientDTO> listRecipients(String username) {
        User currentUser = requireUser(username);
        return userRepository.findAll().stream()
                .filter(user -> Boolean.TRUE.equals(user.getEnabled()))
                .filter(user -> !user.getId().equals(currentUser.getId()))
                .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(user -> {
                    ShareRecipientDTO dto = new ShareRecipientDTO();
                    dto.setId(user.getId());
                    dto.setUsername(user.getUsername());
                    dto.setNickname(user.getNickname());
                    return dto;
                })
                .toList();
    }

    public void markRead(Long id, String username) {
        UserShareMessage message = requireOwnedMessage(id, username);
        markMessageRead(message, username);
    }

    public void delete(Long id, String username) {
        UserShareMessage message = requireOwnedMessage(id, username);
        message.setDeleted(true);
        userShareMessageRepository.save(message);
        pushIfAvailable(username, "deleted", message);
    }

    public ShareLink resolveShareForMessage(Long id, String username) {
        UserShareMessage message = requireOwnedMessage(id, username);
        if (!isShareMessage(message)) {
            throw new IllegalArgumentException("仅支持打开分享消息");
        }
        String targetShareCode = resolveLinkedShareCode(message);
        if (targetShareCode == null || targetShareCode.trim().isEmpty()) {
            throw new IllegalArgumentException("消息未关联可打开分享");
        }
        ShareLink shareLink = shareService.peekShareInfo(targetShareCode, null);
        markMessageRead(message, username);
        return shareLink;
    }

    public ShareLink forwardMessage(Long id, String username, List<Long> recipientUserIds) {
        User currentUser = requireUser(username);
        UserShareMessage message = requireOwnedMessage(id, currentUser);
        if (!isShareMessage(message)) {
            throw new IllegalArgumentException("仅支持转发分享消息");
        }
        String targetShareCode = resolveLinkedShareCode(message);
        if (targetShareCode == null || targetShareCode.trim().isEmpty()) {
            throw new IllegalArgumentException("消息未关联可转发分享");
        }
        List<Long> normalizedRecipientUserIds = normalizeRecipientUserIds(recipientUserIds, currentUser.getId());
        if (normalizedRecipientUserIds.isEmpty()) {
            throw new IllegalArgumentException("recipientUserIds不能为空");
        }
        ShareLink sourceShare = shareService.peekShareInfo(targetShareCode, null);

        ShareService.UserShareMessageDispatchContext context = new ShareService.UserShareMessageDispatchContext();
        context.setMessageType(MESSAGE_TYPE_SHARE);
        context.setOriginSenderUsername(resolveOriginSenderUsername(message));
        context.setForwardedByUsername(username);
        context.setRelatedShareCode(null);
        context.setConversationKey(targetShareCode);

        if (isBatchShare(sourceShare)) {
            List<String> objectNames = new ShareSupport().parseBatchRoots(sourceShare.getObjectName());
            return shareService.createBatchShareLink(
                    objectNames,
                    sourceShare.getBucket(),
                    username,
                    null,
                    null,
                    TARGET_TYPE_USER,
                    normalizedRecipientUserIds,
                    context
            );
        }

        CreateShareRequest request = new CreateShareRequest();
        request.setObjectName(sourceShare.getObjectName());
        request.setBucket(sourceShare.getBucket());
        request.setTargetType(TARGET_TYPE_USER);
        request.setRecipientUserIds(normalizedRecipientUserIds);
        request.setExpirationHours(null);
        request.setPassword(null);
        return shareService.createShareLink(request, username, context);
    }

    public void submitFeedback(String username, String category, String title, String body) {
        requireUser(username);
        List<UserShareMessage> messages = new ArrayList<>();
        List<String> adminUsernames = new ArrayList<>();
        for (User user : userRepository.findAll()) {
            if (!Boolean.TRUE.equals(user.getEnabled()) || !isAdmin(user)) {
                continue;
            }
            messages.add(buildFeedbackMessage(user, username, category, title, body));
            adminUsernames.add(user.getUsername());
        }
        if (!messages.isEmpty()) {
            userShareMessageRepository.saveAll(messages);
            if (messagePushService != null) {
                for (int index = 0; index < messages.size(); index++) {
                    UserShareMessage message = messages.get(index);
                    messagePushService.pushFeedbackReceived(
                            adminUsernames.get(index),
                            buildFeedbackPayload(message)
                    );
                }
            }
        }
    }

    private String resolveLinkedShareCode(UserShareMessage message) {
        String shareCode = message.getShareCode();
        if (shareCode != null) {
            String trimmed = shareCode.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        String relatedShareCode = message.getRelatedShareCode();
        if (relatedShareCode != null) {
            String trimmed = relatedShareCode.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    private void markMessageRead(UserShareMessage message, String username) {
        if (!Boolean.TRUE.equals(message.getRead())) {
            message.setRead(true);
            message.setReadTime(LocalDateTime.now());
            userShareMessageRepository.save(message);
            pushIfAvailable(username, "read", message);
        }
    }

    private boolean isAdmin(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return false;
        }
        return user.getRoles().stream()
                .map(Role::getCode)
                .filter(Objects::nonNull)
                .anyMatch(code -> ROLE_ADMIN.equalsIgnoreCase(code.trim()));
    }

    private UserShareMessage buildFeedbackMessage(User admin, String senderUsername,
                                                  String category, String title, String body) {
        UserShareMessage message = new UserShareMessage();
        message.setRecipientUserId(admin.getId());
        message.setSenderUsername(senderUsername);
        message.setShareCode(null);
        message.setMessageType(MESSAGE_TYPE_FEEDBACK);
        message.setBody(body);
        message.setOriginSenderUsername(null);
        message.setForwardedByUsername(null);
        message.setRelatedShareCode(null);
        message.setConversationKey(null);
        message.setTitle(formatFeedbackTitle(category, title));
        message.setRead(false);
        message.setReadTime(null);
        message.setCreateTime(LocalDateTime.now());
        message.setDeleted(false);
        return message;
    }

    private String resolveOriginSenderUsername(UserShareMessage message) {
        String originSenderUsername = message.getOriginSenderUsername();
        if (originSenderUsername != null) {
            String trimmed = originSenderUsername.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return message.getSenderUsername();
    }

    private boolean isBatchShare(ShareLink sourceShare) {
        if (sourceShare == null || sourceShare.getObjectName() == null) {
            return false;
        }
        String objectName = sourceShare.getObjectName();
        return objectName.startsWith(ShareSupport.BATCH_PREFIX)
                || objectName.startsWith(ShareSupport.LEGACY_BATCH_PREFIX);
    }

    private String formatFeedbackTitle(String category, String title) {
        String normalizedCategory = normalizeValue(category, DEFAULT_FEEDBACK_CATEGORY);
        String normalizedTitle = normalizeValue(title, DEFAULT_FEEDBACK_TITLE);
        return "[" + normalizedCategory + "] " + normalizedTitle;
    }

    private String normalizeValue(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    private boolean isShareMessage(UserShareMessage message) {
        String messageType = message.getMessageType();
        if (messageType == null || messageType.trim().isEmpty()) {
            return true;
        }
        return MESSAGE_TYPE_SHARE.equalsIgnoreCase(messageType.trim());
    }

    private List<Long> normalizeRecipientUserIds(List<Long> recipientUserIds, Long currentUserId) {
        List<Long> normalized = new ArrayList<>();
        if (recipientUserIds == null) {
            return normalized;
        }
        for (Long recipientUserId : recipientUserIds) {
            if (recipientUserId == null) {
                continue;
            }
            if (Objects.equals(recipientUserId, currentUserId)) {
                continue;
            }
            if (!normalized.contains(recipientUserId)) {
                normalized.add(recipientUserId);
            }
        }
        return normalized;
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    private UserShareMessage requireOwnedMessage(Long id, String username) {
        User user = requireUser(username);
        return requireOwnedMessage(id, user);
    }

    private UserShareMessage requireOwnedMessage(Long id, User user) {
        return userShareMessageRepository.findByIdAndRecipientUserIdAndDeletedFalse(id, user.getId())
                .orElseThrow(() -> new RuntimeException("站内信不存在"));
    }

    private void pushIfAvailable(String username, String eventType, UserShareMessage message) {
        if (messagePushService == null) {
            return;
        }
        if ("read".equals(eventType)) {
            messagePushService.pushMessageRead(username, buildStatePayload(message));
            return;
        }
        if ("deleted".equals(eventType)) {
            messagePushService.pushMessageDeleted(username, buildStatePayload(message));
        }
    }

    private Map<String, Object> buildStatePayload(UserShareMessage message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", message.getId());
        payload.put("read", message.getRead());
        payload.put("deleted", message.getDeleted());
        return payload;
    }

    private Map<String, Object> buildFeedbackPayload(UserShareMessage message) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", message.getId());
        payload.put("title", message.getTitle());
        payload.put("messageType", message.getMessageType());
        payload.put("senderUsername", message.getSenderUsername());
        return payload;
    }
}
