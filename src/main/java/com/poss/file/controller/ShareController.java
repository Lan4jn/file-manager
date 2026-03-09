package com.poss.file.controller;

import com.poss.file.common.Result;
import com.poss.file.dto.CreateShareRequest;
import com.poss.file.model.FileInfo;
import com.poss.file.model.ShareLink;
import com.poss.file.service.ShareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/share")
@CrossOrigin(origins = "*")
public class ShareController {

    private static final Logger log = LoggerFactory.getLogger(ShareController.class);

    private final ShareService shareService;

    public ShareController(ShareService shareService) {
        this.shareService = shareService;
    }

    /**
     * 创建分享链接
     */
    @PostMapping("/create")
    public Result<ShareLink> createShare(
            @RequestBody CreateShareRequest request,
            @RequestParam(value = "createBy", required = false, defaultValue = "anonymous") String createBy) {
        log.info("Creating share link for: {}", request.getObjectName());
        ShareLink shareLink = shareService.createShareLink(request, createBy);
        return Result.success(shareLink);
    }

    /**
     * 获取分享信息
     */
    @GetMapping("/info")
    public Result<ShareLink> getShareInfo(
            @RequestParam("shareCode") String shareCode,
            @RequestParam(value = "password", required = false) String password) {
        ShareLink shareLink = shareService.getShareInfo(shareCode, password);
        return Result.success(shareLink);
    }

    /**
     * 通过分享链接获取文件
     */
    @GetMapping("/file")
    public Result<FileInfo> getFileByShareCode(@RequestParam("shareCode") String shareCode) {
        FileInfo fileInfo = shareService.getFileByShareCode(shareCode);
        return Result.success(fileInfo);
    }

    /**
     * 验证分享链接
     */
    @GetMapping("/validate")
    public Result<Boolean> validateShareCode(@RequestParam("shareCode") String shareCode) {
        boolean valid = shareService.validateShareCode(shareCode);
        return Result.success(valid);
    }

    /**
     * 删除分享链接
     */
    @DeleteMapping("/delete")
    public Result<Void> deleteShare(@RequestParam("shareCode") String shareCode) {
        log.info("Deleting share link: {}", shareCode);
        shareService.deleteShareLink(shareCode);
        return Result.success();
    }

    /**
     * 获取用户的所有分享链接
     */
    @GetMapping("/list")
    public Result<List<ShareLink>> getShareLinks(
            @RequestParam(value = "createBy", required = false, defaultValue = "anonymous") String createBy) {
        List<ShareLink> shareLinks = shareService.getShareLinksByUser(createBy);
        return Result.success(shareLinks);
    }
}
