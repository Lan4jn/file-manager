package com.lan4jn.file.service;

import com.lan4jn.file.common.PageResult;
import com.lan4jn.file.dto.ShareRecipientDTO;
import com.lan4jn.file.entity.User;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.model.UserShareMessage;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.repository.UserShareMessageRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class UserShareMessageService {

    private final UserRepository userRepository;
    private final UserShareMessageRepository userShareMessageRepository;
    private final ShareService shareService;

    public UserShareMessageService(UserRepository userRepository,
                                   UserShareMessageRepository userShareMessageRepository,
                                   ShareService shareService) {
        this.userRepository = userRepository;
        this.userShareMessageRepository = userShareMessageRepository;
        this.shareService = shareService;
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
        markMessageRead(message);
    }

    public void delete(Long id, String username) {
        UserShareMessage message = requireOwnedMessage(id, username);
        message.setDeleted(true);
        userShareMessageRepository.save(message);
    }

    public ShareLink resolveShareForMessage(Long id, String username) {
        UserShareMessage message = requireOwnedMessage(id, username);
        String targetShareCode = resolveLinkedShareCode(message);
        ShareLink shareLink = shareService.peekShareInfo(targetShareCode, null);
        markMessageRead(message);
        return shareLink;
    }

    private String resolveLinkedShareCode(UserShareMessage message) {
        String relatedShareCode = message.getRelatedShareCode();
        if (relatedShareCode != null) {
            String trimmed = relatedShareCode.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return message.getShareCode();
    }

    private void markMessageRead(UserShareMessage message) {
        if (!Boolean.TRUE.equals(message.getRead())) {
            message.setRead(true);
            message.setReadTime(LocalDateTime.now());
            userShareMessageRepository.save(message);
        }
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    private UserShareMessage requireOwnedMessage(Long id, String username) {
        User user = requireUser(username);
        return userShareMessageRepository.findByIdAndRecipientUserIdAndDeletedFalse(id, user.getId())
                .orElseThrow(() -> new RuntimeException("站内信不存在"));
    }
}
