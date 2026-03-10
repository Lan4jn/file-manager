package com.poss.file.controller;

import com.poss.file.common.Result;
import com.poss.file.dto.CreateShareRequest;
import com.poss.file.model.FileInfo;
import com.poss.file.model.ShareLink;
import com.poss.file.service.FileService;
import com.poss.file.service.ShareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/share")

public class ShareController {

    private static final Logger log = LoggerFactory.getLogger(ShareController.class);

    private final ShareService shareService;
    private final FileService fileService;

    public ShareController(ShareService shareService, FileService fileService) {
        this.shareService = shareService;
        this.fileService = fileService;
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
     * 通过分享链接获取文件信息
     */
    @GetMapping("/file")
    public Result<FileInfo> getFileByShareCode(@RequestParam("shareCode") String shareCode) {
        FileInfo fileInfo = shareService.getFileByShareCode(shareCode);
        return Result.success(fileInfo);
    }

    /**
     * 通过分享链接下载文件
     */
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> downloadByShareCode(@RequestParam("shareCode") String shareCode) throws UnsupportedEncodingException {
        ShareLink shareLink = shareService.getShareInfo(shareCode, null);
        FileInfo fileInfo = fileService.getFileInfo(shareLink.getObjectName(), shareLink.getBucket());
        InputStream stream = fileService.getFileStream(shareLink.getObjectName(), shareLink.getBucket());

        String filename = URLEncoder.encode(fileInfo.getFileName(), "UTF-8");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(fileInfo.getContentType()))
                .contentLength(fileInfo.getSize())
                .body(new InputStreamResource(stream));
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
