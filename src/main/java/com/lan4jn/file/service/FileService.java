package com.lan4jn.file.service;

import com.lan4jn.file.common.PageResult;
import com.lan4jn.file.dto.UploadResponse;
import com.lan4jn.file.model.BucketInfo;
import com.lan4jn.file.model.FileInfo;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.List;
import java.util.Set;

@Service
public class FileService {

    private static final Set<String> PREVIEWABLE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg",
            "pdf",
            "txt", "md", "log", "json", "xml", "yml", "yaml", "properties", "csv", "java", "js",
            "ts", "css", "html", "htm", "sql", "sh",
            "mp3", "wav", "ogg", "m4a", "aac", "flac",
            "mp4", "webm", "mov", "m4v"
    );

    private final FileDomainSupport support;
    private final BucketService bucketService;
    private final FileQueryService fileQueryService;
    private final FileCommandService fileCommandService;
    private final ArchiveService archiveService;

    @Value("${minio.default-bucket:file-manager}")
    private String defaultBucket;

    @Value("${minio.public-url:}")
    private String publicUrl;

    @Value("${minio.presigned-expiry-seconds:900}")
    private Integer presignedExpirySeconds;

    public FileService(MinioClient minioClient, ConfigService configService) {
        this.support = new FileDomainSupport(minioClient, configService);
        this.bucketService = new BucketService(minioClient);
        this.fileQueryService = new FileQueryService(minioClient, support);
        this.fileCommandService = new FileCommandService(minioClient, support);
        this.archiveService = new ArchiveService(support, fileQueryService);
    }

    public List<BucketInfo> listBuckets(String sortBy, String order) {
        return bucketService.listBuckets(sortBy, order);
    }

    public List<BucketInfo> listBuckets(List<String> bucketNames, String sortBy, String order) {
        return bucketService.listBuckets(bucketNames, sortBy, order);
    }

    public BucketInfo createBucket(String bucketName) {
        return bucketService.createBucket(bucketName);
    }

    public void deleteBucket(String bucketName) {
        bucketService.deleteBucket(bucketName);
    }

    public String getDefaultBucket() {
        return defaultBucket;
    }

    public UploadResponse uploadFile(MultipartFile file, String folder, String bucket) {
        return fileCommandService.uploadFile(file, folder, bucket, defaultBucket, publicUrl);
    }

    public FileDomainSupport.PreparedUpload prepareDirectUpload(String fileName, Long size, String folder, String bucket) {
        return fileCommandService.prepareDirectUpload(fileName, size, folder, bucket, defaultBucket);
    }

    public UploadResponse completeDirectUpload(String bucket,
                                               String objectName,
                                               String fileName,
                                               String contentType,
                                               Long expectedSize) {
        return fileCommandService.completeDirectUpload(bucket, objectName, fileName, contentType, expectedSize, publicUrl);
    }

    public List<FileInfo> listFiles(String folder, String bucket, String sortBy, String order) {
        return fileQueryService.listFiles(folder, bucket, defaultBucket, publicUrl, sortBy, order);
    }

    public PageResult<FileInfo> listFiles(String folder, String bucket, String sortBy, String order, int page, int size) {
        return fileQueryService.listFiles(folder, bucket, defaultBucket, publicUrl, sortBy, order, page, size);
    }

    public PageResult<FileInfo> listFiles(String folder, String bucket, String sortBy, String order,
                                          boolean folderFirst, int page, int size) {
        return fileQueryService.listFiles(folder, bucket, defaultBucket, publicUrl, sortBy, order, folderFirst, page, size);
    }

    public void deleteFile(String objectName, String bucket) {
        fileCommandService.deleteFile(objectName, bucket, defaultBucket);
    }

    public void deleteFiles(List<String> objectNames, String bucket) {
        fileCommandService.deleteFiles(objectNames, bucket, defaultBucket);
    }

    public void createFolder(String folderPath, String bucket, boolean createTestFile) {
        fileCommandService.createFolder(folderPath, bucket, defaultBucket, createTestFile);
    }

    public void createFolder(String folderPath, String bucket) {
        createFolder(folderPath, bucket, false);
    }

    public InputStream getFileStream(String objectName, String bucket) {
        return fileQueryService.getFileStream(objectName, bucket, defaultBucket);
    }

    public InputStream getFileStream(String objectName, String bucket, Long offset, Long length) {
        return fileQueryService.getFileStream(objectName, bucket, defaultBucket, offset, length);
    }

    public FileInfo getFileInfo(String objectName, String bucket) {
        return fileQueryService.getFileInfo(objectName, bucket, defaultBucket, publicUrl);
    }

    public FileInfo getFileInfo(String objectName) {
        return getFileInfo(objectName, defaultBucket);
    }

    public void copyFile(String sourceObjectName, String destObjectName, String sourceBucket, String destBucket) {
        fileCommandService.copyFile(sourceObjectName, destObjectName, sourceBucket, destBucket, defaultBucket);
    }

    public void moveFile(String sourceObjectName, String destObjectName, String sourceBucket, String destBucket) {
        fileCommandService.moveFile(sourceObjectName, destObjectName, sourceBucket, destBucket, defaultBucket);
    }

    public String getPreviewUrl(String objectName, String bucket) {
        return fileQueryService.getPreviewUrl(objectName, bucket, defaultBucket, publicUrl);
    }

    public String getPresignedDownloadUrl(String objectName, String bucket, String fileName) {
        String resolvedBucket = support.resolveBucket(bucket, defaultBucket);
        return support.getPresignedDownloadUrl(resolvedBucket, objectName, fileName, presignedExpirySeconds);
    }

    public String getPresignedUploadUrl(String objectName, String bucket) {
        String resolvedBucket = support.resolveBucket(bucket, defaultBucket);
        return support.getPresignedUploadUrl(resolvedBucket, objectName, presignedExpirySeconds);
    }

    public void assertPreviewSupported(FileInfo fileInfo) {
        if (fileInfo == null || Boolean.TRUE.equals(fileInfo.getIsFolder())) {
            throw new IllegalArgumentException("当前对象不支持在线预览");
        }
        String extension = extractExtension(fileInfo.getFileName());
        String contentType = fileInfo.getContentType() == null ? "" : fileInfo.getContentType().toLowerCase(Locale.ROOT);
        if (contentType.startsWith("image/")
                || contentType.startsWith("text/")
                || contentType.startsWith("audio/")
                || contentType.startsWith("video/")
                || "application/pdf".equals(contentType)
                || PREVIEWABLE_EXTENSIONS.contains(extension)) {
            return;
        }
        throw new IllegalArgumentException("当前文件类型不支持在线预览");
    }

    public List<FileInfo> listFilesRecursive(String folder, String bucket) {
        return fileQueryService.listFilesRecursive(folder, bucket, defaultBucket);
    }

    public void downloadFolderAsZip(String folder, String bucket, OutputStream outputStream) {
        archiveService.downloadFolderAsZip(folder, bucket, defaultBucket, outputStream);
    }

    public void downloadBatchAsZip(List<String> objectNames, String bucket, OutputStream outputStream) {
        archiveService.downloadBatchAsZip(objectNames, bucket, defaultBucket, outputStream);
    }

    public List<FileInfo> resolveFolderArchiveEntries(String folder, String bucket) {
        return archiveService.resolveFolderEntries(support.normalizeFolder(folder), support.resolveBucket(bucket, defaultBucket));
    }

    public List<FileInfo> resolveBatchArchiveEntries(List<String> objectNames, String bucket) {
        return archiveService.resolveBatchEntries(objectNames, support.resolveBucket(bucket, defaultBucket));
    }

    public void downloadResolvedFilesAsZip(List<FileInfo> files, String bucket, OutputStream outputStream) throws Exception {
        archiveService.streamFilesAsZip(files, support.resolveBucket(bucket, defaultBucket), defaultBucket, outputStream);
    }

    private String getFileName(String objectName) {
        return support.getFileName(objectName);
    }

    private String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
