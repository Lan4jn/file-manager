package com.lan4jn.file.controller;

import com.lan4jn.file.common.Result;
import com.lan4jn.file.dto.CreateShareRequest;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.model.ShareAccessInfo;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.service.FileService;
import com.lan4jn.file.service.ShareService;
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
     * 鍒涘缓鍒嗕韩閾炬帴
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
     * 鍒涘缓鎵归噺鍒嗕韩閾炬帴
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
     * 鑾峰彇鍒嗕韩淇℃伅
     */
    @GetMapping("/info")
    public Result<ShareLink> getShareInfo(
            @RequestParam("shareCode") String shareCode,
            @RequestParam(value = "password", required = false) String password) {
        ShareLink shareLink = shareService.getShareInfo(shareCode, password);
        return Result.success(shareLink);
    }

    /**
     * 閫氳繃鍒嗕韩閾炬帴鑾峰彇鏂囦欢淇℃伅
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
            throw new RuntimeException("鐩綍璇疯繘鍏ュ悗涓嬭浇鏂囦欢");
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
            throw new RuntimeException("褰撳墠鍒嗕韩涓嶆槸鐩綍鎴栨壒閲忓垎浜?");
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
     * 閫氳繃鍒嗕韩閾炬帴涓嬭浇鏂囦欢
     */
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> downloadByShareCode(
            @RequestParam("shareCode") String shareCode,
            @RequestParam(value = "password", required = false) String password) throws UnsupportedEncodingException {
        log.info("Processing download request for share: {}", shareCode);
        ShareLink link = shareService.getShareInfo(shareCode, password);

        if (Boolean.TRUE.equals(link.getIsFolder())) {
            throw new RuntimeException("鐩綍鎴栨壒閲忓垎浜鍦ㄥ垎浜〉涓祻瑙堝悗鍗曠嫭涓嬭浇鏂囦欢");
        }

        // 鏂囦欢鍒嗕韩
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
     * 淇濈暀鏃ф帴鍙ｏ紝鏄惧紡鎻愮ず浣跨敤鏂扮殑娴忚寮忓垎浜〉
     */
    @GetMapping("/download-folder")
    public ResponseEntity<InputStreamResource> downloadFolderShareLink(
            @RequestParam("shareCode") String shareCode,
            @RequestParam(value = "password", required = false) String password) throws Exception {
        throw new RuntimeException("鐩綍鍒嗕韩宸插崌绾т负鍦ㄧ嚎娴忚妯″紡锛岃鍦ㄥ垎浜〉涓€夋嫨鏂囦欢涓嬭浇");
    }

    /**
     * 楠岃瘉鍒嗕韩閾炬帴
     */
    @GetMapping("/validate")
    public Result<Boolean> validateShareCode(@RequestParam("shareCode") String shareCode) {
        boolean valid = shareService.validateShareCode(shareCode);
        return Result.success(valid);
    }

    /**
     * 鍒犻櫎鍒嗕韩閾炬帴
     */
    @DeleteMapping("/delete")
    public Result<Void> deleteShare(@RequestParam("shareCode") String shareCode, Authentication authentication) {
        log.info("Deleting share link: {}", shareCode);
        shareService.deleteShareLink(shareCode, authentication.getName());
        return Result.success();
    }

    /**
     * 鑾峰彇鐢ㄦ埛鐨勬墍鏈夊垎浜摼鎺?
     */
    @GetMapping("/list")
    public Result<List<ShareLink>> getShareLinks(Authentication authentication) {
        List<ShareLink> shareLinks = shareService.getShareLinksByUser(authentication.getName());
        return Result.success(shareLinks);
    }
}

