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
import java.util.List;

@Service
public class FileService {

    private final FileDomainSupport support;
    private final BucketService bucketService;
    private final FileQueryService fileQueryService;
    private final FileCommandService fileCommandService;
    private final ArchiveService archiveService;

    @Value("${minio.default-bucket:file-manager}")
    private String defaultBucket;

    @Value("${minio.public-url:}")
    private String publicUrl;

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

    public List<FileInfo> listFiles(String folder, String bucket, String sortBy, String order) {
        return fileQueryService.listFiles(folder, bucket, defaultBucket, publicUrl, sortBy, order);
    }

    public PageResult<FileInfo> listFiles(String folder, String bucket, String sortBy, String order, int page, int size) {
        return fileQueryService.listFiles(folder, bucket, defaultBucket, publicUrl, sortBy, order, page, size);
    }

    public void deleteFile(String objectName, String bucket) {
        fileCommandService.deleteFile(objectName, bucket, defaultBucket);
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

    public List<FileInfo> listFilesRecursive(String folder, String bucket) {
        return fileQueryService.listFilesRecursive(folder, bucket, defaultBucket);
    }

    public void downloadFolderAsZip(String folder, String bucket, OutputStream outputStream) {
        archiveService.downloadFolderAsZip(folder, bucket, defaultBucket, outputStream);
    }

    public void downloadBatchAsZip(List<String> objectNames, String bucket, OutputStream outputStream) {
        archiveService.downloadBatchAsZip(objectNames, bucket, defaultBucket, outputStream);
    }

    private String getFileName(String objectName) {
        return support.getFileName(objectName);
    }
}
