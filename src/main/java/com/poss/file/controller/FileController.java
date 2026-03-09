package com.poss.file.controller;

import com.poss.file.common.Result;
import com.poss.file.dto.UploadResponse;
import com.poss.file.model.FileInfo;
import com.poss.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FileController {

    private final FileService fileService;

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    public Result<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "") String folder) {
        log.info("Uploading file: {}, folder: {}", file.getOriginalFilename(), folder);
        UploadResponse response = fileService.uploadFile(file, folder);
        return Result.success(response);
    }

    /**
     * 获取文件列表
     */
    @GetMapping("/list")
    public Result<List<FileInfo>> list(@RequestParam(value = "folder", required = false, defaultValue = "") String folder) {
        log.info("Listing files, folder: {}", folder);
        List<FileInfo> files = fileService.listFiles(folder);
        return Result.success(files);
    }

    /**
     * 获取文件信息
     */
    @GetMapping("/info")
    public Result<FileInfo> info(@RequestParam("objectName") String objectName) {
        FileInfo fileInfo = fileService.getFileInfo(objectName);
        return Result.success(fileInfo);
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestParam("objectName") String objectName) {
        log.info("Deleting file: {}", objectName);
        fileService.deleteFile(objectName);
        return Result.success();
    }

    /**
     * 创建文件夹
     */
    @PostMapping("/folder")
    public Result<Void> createFolder(@RequestParam("path") String path) {
        log.info("Creating folder: {}", path);
        fileService.createFolder(path);
        return Result.success();
    }

    /**
     * 下载文件
     */
    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(@RequestParam("objectName") String objectName) {
        log.info("Downloading file: {}", objectName);
        FileInfo fileInfo = fileService.getFileInfo(objectName);
        InputStream stream = fileService.getFileStream(objectName);

        String filename = URLEncoder.encode(fileInfo.getFileName(), StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(fileInfo.getContentType()))
                .contentLength(fileInfo.getSize())
                .body(new InputStreamResource(stream));
    }

    /**
     * 预览文件
     */
    @GetMapping("/preview")
    public ResponseEntity<InputStreamResource> preview(@RequestParam("objectName") String objectName) {
        log.info("Previewing file: {}", objectName);
        FileInfo fileInfo = fileService.getFileInfo(objectName);
        InputStream stream = fileService.getFileStream(objectName);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fileInfo.getContentType()))
                .contentLength(fileInfo.getSize())
                .body(new InputStreamResource(stream));
    }

    /**
     * 获取文件预览URL
     */
    @GetMapping("/preview-url")
    public Result<String> getPreviewUrl(@RequestParam("objectName") String objectName) {
        String url = fileService.getPreviewUrl(objectName);
        return Result.success(url);
    }

    /**
     * 复制文件
     */
    @PostMapping("/copy")
    public Result<Void> copy(
            @RequestParam("source") String sourceObjectName,
            @RequestParam("dest") String destObjectName) {
        log.info("Copying file from {} to {}", sourceObjectName, destObjectName);
        fileService.copyFile(sourceObjectName, destObjectName);
        return Result.success();
    }

    /**
     * 移动文件
     */
    @PostMapping("/move")
    public Result<Void> move(
            @RequestParam("source") String sourceObjectName,
            @RequestParam("dest") String destObjectName) {
        log.info("Moving file from {} to {}", sourceObjectName, destObjectName);
        fileService.moveFile(sourceObjectName, destObjectName);
        return Result.success();
    }
}
