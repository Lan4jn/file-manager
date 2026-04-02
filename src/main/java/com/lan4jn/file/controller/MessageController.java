package com.lan4jn.file.controller;

import com.lan4jn.file.common.PageResult;
import com.lan4jn.file.common.Result;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
                                                      @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        return Result.success(userShareMessageService.listInbox(authentication.getName(), page, size));
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
}
