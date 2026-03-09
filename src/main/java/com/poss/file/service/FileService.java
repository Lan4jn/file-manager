package com.poss.file.service;

import cn.hutool.core.util.StrUtil;
import com.poss.file.dto.UploadResponse;
import com.poss.file.model.FileInfo;
import io.minio.*;
import io.minio.errors.MinioException;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    @Value("${minio.public-url}")
    private String publicUrl;

    /**
     * 上传文件
     */
    public UploadResponse uploadFile(MultipartFile file, String folder) {
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename);

            // 生成唯一文件名
            String objectName = generateObjectName(folder, originalFilename);

            // 上传到 MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // 获取文件 Etag
            StatObjectArgs statArgs = StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build();
            StatObjectResponse stat = minioClient.statObject(statArgs);

            UploadResponse response = new UploadResponse();
            response.setObjectName(objectName);
            response.setFileName(originalFilename);
            response.setSize(file.getSize());
            response.setContentType(file.getContentType());
            response.setUrl(publicUrl + "/" + objectName);
            response.setEtag(stat.etag());

            log.info("File uploaded successfully: {}", objectName);
            return response;
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件列表
     */
    public List<FileInfo> listFiles(String folder) {
        try {
            // 确保 folder 以 / 结尾
            if (StrUtil.isNotEmpty(folder) && !folder.endsWith("/")) {
                folder = folder + "/";
            }

            // 列出指定前缀的所有对象
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(folder)
                            .recursive(false)
                            .build()
            );

            List<FileInfo> fileList = new ArrayList<>();
            for (Result<Item> result : results) {
                Item item = result.get();
                FileInfo fileInfo = new FileInfo();
                fileInfo.setObjectName(item.objectName());
                fileInfo.setFileName(getFileName(item.objectName()));
                fileInfo.setSize(item.size());
                fileInfo.setIsFolder(item.isDir());
                fileInfo.setUploadTime(LocalDateTime.ofInstant(
                        item.lastModified().toInstant(), ZoneId.systemDefault()));
                fileInfo.setUrl(publicUrl + "/" + item.objectName());
                fileList.add(fileInfo);
            }

            return fileList;
        } catch (Exception e) {
            log.error("Failed to list files", e);
            throw new RuntimeException("获取文件列表失败: " + e.getMessage());
        }
    }

    /**
     * 删除文件
     */
    public void deleteFile(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            log.info("File deleted: {}", objectName);
        } catch (Exception e) {
            log.error("Failed to delete file: {}", objectName, e);
            throw new RuntimeException("文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 创建文件夹
     */
    public void createFolder(String folderPath) {
        try {
            if (!folderPath.endsWith("/")) {
                folderPath = folderPath + "/";
            }
            // 创建一个空对象来模拟文件夹
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(folderPath)
                            .stream(new byte[0], 0, -1)
                            .build()
            );
            log.info("Folder created: {}", folderPath);
        } catch (Exception e) {
            log.error("Failed to create folder: {}", folderPath, e);
            throw new RuntimeException("创建文件夹失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件流
     */
    public InputStream getFileStream(String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to get file stream: {}", objectName, e);
            throw new RuntimeException("获取文件失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件信息
     */
    public FileInfo getFileInfo(String objectName) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );

            FileInfo fileInfo = new FileInfo();
            fileInfo.setObjectName(objectName);
            fileInfo.setFileName(getFileName(objectName));
            fileInfo.setSize(stat.size());
            fileInfo.setContentType(stat.contentType());
            fileInfo.setEtag(stat.etag());
            fileInfo.setUploadTime(LocalDateTime.ofInstant(
                    stat.lastModified().toInstant(), ZoneId.systemDefault()));
            fileInfo.setUrl(publicUrl + "/" + objectName);
            fileInfo.setIsFolder(objectName.endsWith("/"));

            return fileInfo;
        } catch (Exception e) {
            log.error("Failed to get file info: {}", objectName, e);
            throw new RuntimeException("获取文件信息失败: " + e.getMessage());
        }
    }

    /**
     * 复制文件
     */
    public void copyFile(String sourceObjectName, String destObjectName) {
        try {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(bucketName)
                            .object(destObjectName)
                            .source(
                                    CopySource.builder()
                                            .bucket(bucketName)
                                            .object(sourceObjectName)
                                            .build()
                            )
                            .build()
            );
            log.info("File copied from {} to {}", sourceObjectName, destObjectName);
        } catch (Exception e) {
            log.error("Failed to copy file", e);
            throw new RuntimeException("文件复制失败: " + e.getMessage());
        }
    }

    /**
     * 移动文件
     */
    public void moveFile(String sourceObjectName, String destObjectName) {
        copyFile(sourceObjectName, destObjectName);
        deleteFile(sourceObjectName);
        log.info("File moved from {} to {}", sourceObjectName, destObjectName);
    }

    /**
     * 获取文件预览URL
     */
    public String getPreviewUrl(String objectName) {
        return publicUrl + "/" + objectName;
    }

    // ===== 私有方法 =====

    private String generateObjectName(String folder, String originalFilename) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String extension = getFileExtension(originalFilename);

        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotEmpty(folder)) {
            if (!folder.endsWith("/")) {
                sb.append(folder).append("/");
            } else {
                sb.append(folder);
            }
        }

        String nameWithoutExt = originalFilename.substring(0,
                originalFilename.length() - extension.length() - 1);
        nameWithoutExt = nameWithoutExt.replaceAll("[^a-zA-Z0-9.-]", "_");

        sb.append(nameWithoutExt)
                .append("_")
                .append(timestamp)
                .append("_")
                .append(uuid);

        if (StrUtil.isNotEmpty(extension)) {
            sb.append(".").append(extension);
        }

        return sb.toString();
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private String getFileName(String objectName) {
        if (objectName == null) {
            return "";
        }
        int lastSlash = objectName.lastIndexOf("/");
        if (lastSlash >= 0 && lastSlash < objectName.length() - 1) {
            return objectName.substring(lastSlash + 1);
        }
        return objectName;
    }
}
