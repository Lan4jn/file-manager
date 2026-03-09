package com.poss.file.controller;

import com.poss.file.common.Result;
import com.poss.file.dto.UploadResponse;
import com.poss.file.model.BucketInfo;
import com.poss.file.model.FileInfo;
import com.poss.file.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@RestController
@RequestMapping("/api/file")
@CrossOrigin(origins = "*")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    // ==================== Bucket Management ====================

    @GetMapping("/buckets")
    public Result<List<BucketInfo>> listBuckets() {
        return Result.success(fileService.listBuckets());
    }

    @PostMapping("/buckets")
    public Result<BucketInfo> createBucket(@RequestBody java.util.Map<String, String> request) {
        String bucketName = request.get("bucketName");
        if (bucketName == null || bucketName.isEmpty()) {
            return Result.error("Bucket名称不能为空");
        }
        return Result.success(fileService.createBucket(bucketName));
    }

    @DeleteMapping("/buckets")
    public Result<Void> deleteBucket(@RequestParam("bucketName") String bucketName) {
        fileService.deleteBucket(bucketName);
        return Result.success();
    }

    @GetMapping("/buckets/default")
    public Result<String> getDefaultBucket() {
        return Result.success(fileService.getDefaultBucket());
    }

    // ==================== File Management ====================

    @PostMapping("/upload")
    public Result<UploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false, defaultValue = "") String folder,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket) {
        log.info("Uploading file: {}, folder: {}, bucket: {}", file.getOriginalFilename(), folder, bucket);
        UploadResponse response = fileService.uploadFile(file, folder, bucket);
        return Result.success(response);
    }

    @GetMapping("/list")
    public Result<List<FileInfo>> list(
            @RequestParam(value = "folder", required = false, defaultValue = "") String folder,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket) {
        log.info("Listing files, folder: {}, bucket: {}", folder, bucket);
        List<FileInfo> files = fileService.listFiles(folder, bucket);
        return Result.success(files);
    }

    @GetMapping("/info")
    public Result<FileInfo> info(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket) {
        FileInfo fileInfo = fileService.getFileInfo(objectName, bucket);
        return Result.success(fileInfo);
    }

    @DeleteMapping("/delete")
    public Result<Void> delete(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket) {
        log.info("Deleting file: {}, bucket: {}", objectName, bucket);
        fileService.deleteFile(objectName, bucket);
        return Result.success();
    }

    @PostMapping("/folder")
    public Result<Void> createFolder(
            @RequestParam("path") String path,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket) {
        log.info("Creating folder: {}, bucket: {}", path, bucket);
        fileService.createFolder(path, bucket);
        return Result.success();
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket) {
        log.info("Downloading file: {}, bucket: {}", objectName, bucket);
        FileInfo fileInfo = fileService.getFileInfo(objectName, bucket);
        InputStream stream = fileService.getFileStream(objectName, bucket);

        String filename = URLEncoder.encode(fileInfo.getFileName(), StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(fileInfo.getContentType()))
                .contentLength(fileInfo.getSize())
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/preview")
    public ResponseEntity<InputStreamResource> preview(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket) {
        log.info("Previewing file: {}, bucket: {}", objectName, bucket);
        FileInfo fileInfo = fileService.getFileInfo(objectName, bucket);
        InputStream stream = fileService.getFileStream(objectName, bucket);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(fileInfo.getContentType()))
                .contentLength(fileInfo.getSize())
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/preview-url")
    public Result<String> getPreviewUrl(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket) {
        String url = fileService.getPreviewUrl(objectName, bucket);
        return Result.success(url);
    }

    @PostMapping("/copy")
    public Result<Void> copy(
            @RequestParam("source") String sourceObjectName,
            @RequestParam("dest") String destObjectName,
            @RequestParam(value = "sourceBucket", required = false, defaultValue = "") String sourceBucket,
            @RequestParam(value = "destBucket", required = false, defaultValue = "") String destBucket) {
        log.info("Copying file from {} to {}", sourceObjectName, destObjectName);
        fileService.copyFile(sourceObjectName, destObjectName, sourceBucket, destBucket);
        return Result.success();
    }

    @PostMapping("/move")
    public Result<Void> move(
            @RequestParam("source") String sourceObjectName,
            @RequestParam("dest") String destObjectName,
            @RequestParam(value = "sourceBucket", required = false, defaultValue = "") String sourceBucket,
            @RequestParam(value = "destBucket", required = false, defaultValue = "") String destBucket) {
        log.info("Moving file from {} to {}", sourceObjectName, destObjectName);
        fileService.moveFile(sourceObjectName, destObjectName, sourceBucket, destBucket);
        return Result.success();
    }
}
