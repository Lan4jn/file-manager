package com.poss.file.controller;

import com.poss.file.common.Result;
import com.poss.file.dto.CreateShareRequest;
import com.poss.file.model.FileInfo;
import com.poss.file.model.ShareAccessInfo;
import com.poss.file.model.ShareLink;
import com.poss.file.service.FileService;
import com.poss.file.service.ShareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

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
            Authentication authentication) {
        log.info("Creating share link for: {}", request.getObjectName());
        ShareLink shareLink = shareService.createShareLink(request, authentication.getName());
        return Result.success(shareLink);
    }

    /**
     * 创建批量分享链接
     */
    @PostMapping("/batch-create")
    public Result<ShareLink> createBatchShare(
            @RequestBody Map<String, Object> params,
            Authentication authentication) {
        log.info("Creating batch share");
        List<String> objectNames = (List<String>) params.get("objectNames");
        String bucket = (String) params.get("bucket");
        Integer expirationHours = params.get("expirationHours") != null
            ? Integer.valueOf(params.get("expirationHours").toString())
            : null;
        String password = params.get("password") != null
            ? params.get("password").toString()
            : null;

        ShareLink shareLink = shareService.createBatchShareLink(objectNames, bucket, authentication.getName(), expirationHours, password);
        return Result.success(shareLink);
    }

    @GetMapping("/access-info")
    public Result<ShareAccessInfo> getShareAccessInfo(@RequestParam("shareCode") String shareCode) {
        return Result.success(shareService.getShareAccessInfo(shareCode));
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
    public Result<FileInfo> getFileByShareCode(
            @RequestParam("shareCode") String shareCode,
            @RequestParam(value = "password", required = false) String password) {
        FileInfo fileInfo = shareService.getFileByShareCode(shareCode, password);
        return Result.success(fileInfo);
    }

    @GetMapping("/items")
    public Result<List<FileInfo>> listSharedItems(
            @RequestParam("shareCode") String shareCode,
            @RequestParam(value = "password", required = false) String password,
            @RequestParam(value = "path", required = false) String path) {
        return Result.success(shareService.listSharedItems(shareCode, password, path));
    }

    @GetMapping("/download-item")
    public ResponseEntity<InputStreamResource> downloadSharedItem(
            @RequestParam("shareCode") String shareCode,
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "password", required = false) String password) throws UnsupportedEncodingException {
        FileInfo fileInfo = shareService.getSharedItemInfo(shareCode, password, objectName);
        if (Boolean.TRUE.equals(fileInfo.getIsFolder())) {
            throw new RuntimeException("目录请进入后下载文件");
        }

        InputStream stream = fileService.getFileStream(fileInfo.getObjectName(), shareService.getShareInfo(shareCode, password).getBucket());
        String filename = URLEncoder.encode(fileInfo.getFileName(), "UTF-8");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(fileInfo.getContentType()))
                .contentLength(fileInfo.getSize())
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/download-all")
    public ResponseEntity<InputStreamResource> downloadAllSharedItems(
            @RequestParam("shareCode") String shareCode,
            @RequestParam(value = "password", required = false) String password) throws Exception {
        ShareLink shareLink = shareService.getShareInfo(shareCode, password);
        if (!Boolean.TRUE.equals(shareLink.getIsFolder())) {
            throw new RuntimeException("当前分享不是目录或批量分享");
        }

        java.io.PipedInputStream stream = new java.io.PipedInputStream();
        java.io.PipedOutputStream outputStream = new java.io.PipedOutputStream(stream);
        new Thread(() -> {
            try {
                shareService.downloadShareAsZip(shareCode, password, outputStream);
            } catch (Exception e) {
                log.error("Failed to stream share archive: {}", shareCode, e);
            } finally {
                try {
                    outputStream.close();
                } catch (Exception ignored) {
                }
            }
        }).start();

        String filename = URLEncoder.encode(shareService.getShareArchiveName(shareCode, password), "UTF-8");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(new InputStreamResource(stream));
    }

    /**
     * 通过分享链接下载文件
     */
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> downloadByShareCode(
            @RequestParam("shareCode") String shareCode,
            @RequestParam(value = "password", required = false) String password) throws UnsupportedEncodingException {
        log.info("Processing download request for share: {}", shareCode);
        ShareLink link = shareService.getShareInfo(shareCode, password);

        if (Boolean.TRUE.equals(link.getIsFolder())) {
            throw new RuntimeException("目录或批量分享请在分享页中浏览后单独下载文件");
        }

        // 文件分享
        log.info("Downloading file {} from bucket {}", link.getObjectName(), link.getBucket());
        FileInfo fileInfo = fileService.getFileInfo(link.getObjectName(), link.getBucket());
        InputStream stream = fileService.getFileStream(link.getObjectName(), link.getBucket());

        String filename = URLEncoder.encode(fileInfo.getFileName(), "UTF-8");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(fileInfo.getContentType()))
                .contentLength(fileInfo.getSize())
                .body(new InputStreamResource(stream));
    }

    /**
     * 保留旧接口，显式提示使用新的浏览式分享页
     */
    @GetMapping("/download-folder")
    public ResponseEntity<InputStreamResource> downloadFolderShareLink(
            @RequestParam("shareCode") String shareCode,
            @RequestParam(value = "password", required = false) String password) throws Exception {
        throw new RuntimeException("目录分享已升级为在线浏览模式，请在分享页中选择文件下载");
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
    public Result<Void> deleteShare(@RequestParam("shareCode") String shareCode, Authentication authentication) {
        log.info("Deleting share link: {}", shareCode);
        shareService.deleteShareLink(shareCode, authentication.getName());
        return Result.success();
    }

    /**
     * 获取用户的所有分享链接
     */
    @GetMapping("/list")
    public Result<List<ShareLink>> getShareLinks(Authentication authentication) {
        List<ShareLink> shareLinks = shareService.getShareLinksByUser(authentication.getName());
        return Result.success(shareLinks);
    }
}
