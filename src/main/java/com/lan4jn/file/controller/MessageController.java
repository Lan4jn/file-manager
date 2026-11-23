package com.lan4jn.file.controller;

import com.lan4jn.file.common.PageResult;
import com.lan4jn.file.common.Result;
import com.lan4jn.file.dto.FeedbackMessageRequest;
import com.lan4jn.file.dto.ForwardShareMessageRequest;
import com.lan4jn.file.dto.ShareRecipientDTO;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.model.UserShareMessage;
import com.lan4jn.file.service.UserShareMessageService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final UserShareMessageService userShareMessageService;

    public MessageController(UserShareMessageService userShareMessageService) {
        this.userShareMessageService = userShareMessageService;
    }

    @GetMapping("/inbox")
    public Result<PageResult<UserShareMessage>> inbox(Authentication authentication,
                                                      @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                                                      @RequestParam(value = "size", required = false, defaultValue = "20") int size,
                                                      @RequestParam(value = "validOnly", required = false, defaultValue = "false") boolean validOnly) {
        return Result.success(userShareMessageService.listInbox(authentication.getName(), page, size, validOnly));
    }

    @GetMapping("/recipients")
    public Result<List<ShareRecipientDTO>> recipients(Authentication authentication) {
        return Result.success(userShareMessageService.listRecipients(authentication.getName()));
    }

    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable("id") Long id, Authentication authentication) {
        userShareMessageService.markRead(id, authentication.getName());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id, Authentication authentication) {
        userShareMessageService.delete(id, authentication.getName());
        return Result.success();
    }

    @GetMapping("/{id}/share")
    public Result<ShareLink> openShare(@PathVariable("id") Long id, Authentication authentication) {
        return Result.success(userShareMessageService.resolveShareForMessage(id, authentication.getName()));
    }

    @PostMapping("/{id}/forward")
    public Result<ShareLink> forwardMessage(@PathVariable("id") Long id,
                                            @RequestBody ForwardShareMessageRequest request,
                                            Authentication authentication) {
        List<Long> recipientUserIds = normalizeRecipientUserIds(request == null ? null : request.getRecipientUserIds());
        if (recipientUserIds.isEmpty()) {
            throw new IllegalArgumentException("recipientUserIds不能为空");
        }
        return Result.success(userShareMessageService.forwardMessage(id, authentication.getName(), recipientUserIds));
    }

    @PostMapping("/feedback")
    public Result<Void> submitFeedback(@RequestBody FeedbackMessageRequest request, Authentication authentication) {
        String body = request == null ? null : request.getBody();
        if (body == null || body.trim().isEmpty()) {
            throw new IllegalArgumentException("body不能为空");
        }
        userShareMessageService.submitFeedback(
                authentication.getName(),
                request == null ? null : request.getCategory(),
                request == null ? null : request.getTitle(),
                body.trim());
        return Result.success();
    }

    private List<Long> normalizeRecipientUserIds(List<Long> recipientUserIds) {
        if (recipientUserIds == null) {
            return List.of();
        }
        List<Long> normalized = new ArrayList<>();
        for (Long recipientUserId : recipientUserIds) {
            if (recipientUserId != null && !normalized.contains(recipientUserId)) {
                normalized.add(recipientUserId);
            }
        }
        return normalized;
    }
}
