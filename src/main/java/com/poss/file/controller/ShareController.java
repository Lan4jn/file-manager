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

import java.io.File;
import java.io.FileInputStream;
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
        ShareLink link = shareService.getShareInfo(shareCode, null);

        // 如果是目录分享，下载ZIP压缩包
        Boolean isFolder = link.getIsFolder();
        if (isFolder != null && isFolder) {
            try {
                ResponseEntity<InputStreamResource> result = downloadFolderShareLink(shareCode);
                return result;
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage());
            }
        }

        // 文件分享
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
     * 通过分享链接下载目录（ZIP压缩包）
     */
    @GetMapping("/download-folder")
    public ResponseEntity<InputStreamResource> downloadFolderShareLink(
            @RequestParam("shareCode") String shareCode) throws Exception {

        ShareLink link = shareService.getShareInfo(shareCode, null);

        if (!Boolean.TRUE.equals(link.getIsFolder())) {
            throw new RuntimeException("不是目录分享");
        }

        File zipFile;

        // 检查压缩包是否存在，不存在则重新压缩
        if (!shareService.isZipFileExists(shareCode)) {
            // 重新压缩
            shareService.recompressShareFolder(shareCode);
        }
        zipFile = shareService.getZipFile(shareCode);

        if (zipFile == null || !zipFile.exists()) {
            throw new RuntimeException("分享文件不存在");
        }

        InputStream stream = new FileInputStream(zipFile);
        String filename = URLEncoder.encode(link.getFileName() + ".zip", "UTF-8");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(zipFile.length())
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
