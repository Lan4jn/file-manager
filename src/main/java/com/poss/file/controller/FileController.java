package com.poss.file.controller;

import com.poss.file.common.PageResult;
import com.poss.file.common.Result;
import com.poss.file.dto.UploadResponse;
import com.poss.file.model.BucketInfo;
import com.poss.file.model.FileInfo;
import com.poss.file.service.ConfigService;
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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/file")

public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    private final FileService fileService;
    private final ConfigService configService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public FileController(FileService fileService, ConfigService configService) {
        this.fileService = fileService;
        this.configService = configService;
    }

    // ==================== Bucket Management ====================

    @GetMapping("/buckets")
    public Result<List<BucketInfo>> listBuckets(
            @RequestParam(value = "sortBy", required = false, defaultValue = "name") String sortBy,
            @RequestParam(value = "order", required = false, defaultValue = "asc") String order) {
        return Result.success(fileService.listBuckets(sortBy, order));
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
    public Result<?> list(
            @RequestParam(value = "folder", required = false, defaultValue = "") String folder,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket,
            @RequestParam(value = "sortBy", required = false, defaultValue = "name") String sortBy,
            @RequestParam(value = "order", required = false, defaultValue = "asc") String order,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "0") int size) {
        log.info("Listing files, folder: {}, bucket: {}, sortBy: {}, order: {}, page: {}, size: {}", folder, bucket, sortBy, order, page, size);

        // 如果没有分页参数，返回原来的格式
        if (page <= 0 || size <= 0) {
            List<FileInfo> files = fileService.listFiles(folder, bucket, sortBy, order);
            return Result.success(files);
        }

        // 分页模式
        PageResult<FileInfo> pageResult = fileService.listFiles(folder, bucket, sortBy, order, page, size);
        return Result.success(pageResult);
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
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket,
            @RequestParam(value = "createTestFile", required = false, defaultValue = "true") boolean createTestFile) {
        log.info("Creating folder: {}, bucket: {}, createTestFile: {}", path, bucket, createTestFile);
        fileService.createFolder(path, bucket, createTestFile);
        return Result.success();
    }

    @GetMapping("/download")
    public ResponseEntity<InputStreamResource> download(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket) throws UnsupportedEncodingException {
        log.info("Downloading file: {}, bucket: {}", objectName, bucket);
        FileInfo fileInfo = fileService.getFileInfo(objectName, bucket);
        InputStream stream = fileService.getFileStream(objectName, bucket);

        String filename = URLEncoder.encode(fileInfo.getFileName(), "UTF-8");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(fileInfo.getContentType()))
                .contentLength(fileInfo.getSize())
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/download-folder")
    public ResponseEntity<InputStreamResource> downloadFolder(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket) throws Exception {
        log.info("Downloading folder: {}, bucket: {}", objectName, bucket);

        // 获取目录名
        String folderName = objectName;
        if (objectName.endsWith("/")) {
            folderName = objectName.substring(0, objectName.length() - 1);
        }
        int lastSlash = folderName.lastIndexOf('/');
        if (lastSlash >= 0) {
            folderName = folderName.substring(lastSlash + 1);
        }
        if (folderName.isEmpty()) {
            folderName = "folder";
        }

        // 使用管道流来实现流式传输
        PipedOutputStream pipedOut = new PipedOutputStream();
        PipedInputStream pipedIn = new PipedInputStream(pipedOut);

        // 在后台线程中生成ZIP
        executorService.submit(() -> {
            try {
                fileService.downloadFolderAsZip(objectName, bucket, pipedOut);
                pipedOut.close();
            } catch (Exception e) {
                log.error("Failed to create ZIP for folder: {}", objectName, e);
                try {
                    pipedOut.close();
                } catch (Exception ex) {
                    // ignore
                }
            }
        });

        String filename = URLEncoder.encode(folderName + ".zip", "UTF-8");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(new InputStreamResource(pipedIn));
    }

    @GetMapping("/batch-download")
    public ResponseEntity<InputStreamResource> batchDownload(
            @RequestParam("objectNames") String objectNamesStr,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket) throws Exception {
        // 解析逗号分隔的文件名列表
        List<String> objectNames = Arrays.asList(objectNamesStr.split(","));
        log.info("Batch downloading {} items, bucket: {}", objectNames.size(), bucket);

        // 计算总大小
        long totalSize = 0;
        for (String objectName : objectNames) {
            // 判断是否是目录（以/结尾）
            boolean isFolder = objectName.endsWith("/");

            if (isFolder) {
                // 如果是目录，递归获取所有文件大小
                try {
                    List<FileInfo> folderFiles = fileService.listFilesRecursive(objectName, bucket);
                    for (FileInfo subFile : folderFiles) {
                        totalSize += subFile.getSize() != null ? subFile.getSize() : 0;
                    }
                } catch (Exception e) {
                    log.warn("Failed to list folder {}: {}", objectName, e.getMessage());
                }
            } else {
                // 文件直接获取大小
                try {
                    FileInfo fileInfo = fileService.getFileInfo(objectName, bucket);
                    totalSize += fileInfo.getSize() != null ? fileInfo.getSize() : 0;
                } catch (Exception e) {
                    log.warn("Failed to get file info {}: {}", objectName, e.getMessage());
                }
            }
        }

        // 检查是否超过配置的大小限制
        Long maxSize = configService.getBatchDownloadMaxSize();
        if (totalSize > maxSize) {
            throw new RuntimeException("批量下载压缩后大小超过限制(" + formatSize(maxSize) + ")，无法下载");
        }

        // 使用管道流来实现流式传输
        PipedOutputStream pipedOut = new PipedOutputStream();
        PipedInputStream pipedIn = new PipedInputStream(pipedOut);

        // 在后台线程中生成ZIP
        executorService.submit(() -> {
            try {
                fileService.downloadBatchAsZip(objectNames, bucket, pipedOut);
                pipedOut.close();
            } catch (Exception e) {
                log.error("Failed to create batch ZIP", e);
                try {
                    pipedOut.close();
                } catch (Exception ex) {
                    // ignore
                }
            }
        });

        String filename = URLEncoder.encode("批量下载_" + objectNames.size() + "个文件.zip", "UTF-8");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(new InputStreamResource(pipedIn));
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    @GetMapping("/preview")
    public ResponseEntity<InputStreamResource> preview(
            @RequestParam("objectName") String objectName,
            @RequestParam(value = "bucket", required = false, defaultValue = "") String bucket) throws UnsupportedEncodingException {
        log.info("Previewing file: {}, bucket: {}", objectName, bucket);
        FileInfo fileInfo = fileService.getFileInfo(objectName, bucket);
        InputStream stream = fileService.getFileStream(objectName, bucket);

        String filename = URLEncoder.encode(fileInfo.getFileName(), "UTF-8");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
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
