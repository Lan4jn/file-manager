package com.poss.file.service;

import cn.hutool.core.util.StrUtil;
import com.poss.file.common.PageResult;
import com.poss.file.dto.UploadResponse;
import com.poss.file.model.BucketInfo;
import com.poss.file.model.FileInfo;
import io.minio.*;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final MinioClient minioClient;
    private final ConfigService configService;

    @Value("${minio.default-bucket:file-manager}")
    private String defaultBucket;

    @Value("${minio.public-url:}")
    private String publicUrl;

    public FileService(MinioClient minioClient, ConfigService configService) {
        this.minioClient = minioClient;
        this.configService = configService;
    }

    /**
     * 获取所有Bucket列表
     */
    public List<BucketInfo> listBuckets(String sortBy, String order) {
        try {
            List<Bucket> buckets = minioClient.listBuckets();
            List<BucketInfo> result = new ArrayList<>();

            for (Bucket bucket : buckets) {
                BucketInfo info = new BucketInfo();
                info.setName(bucket.name());
                info.setCreationDate(LocalDateTime.ofInstant(
                        bucket.creationDate().toInstant(), ZoneId.systemDefault()));

                // 统计bucket中的对象数量和大小
                try {
                    Iterable<Result<Item>> objects = minioClient.listObjects(
                            ListObjectsArgs.builder()
                                    .bucket(bucket.name())
                                    .recursive(true)
                                    .build()
                    );

                    long objectCount = 0;
                    long totalSize = 0;
                    for (Result<Item> obj : objects) {
                        Item item = obj.get();
                        if (!item.isDir()) {
                            objectCount++;
                            totalSize += item.size();
                        }
                    }
                    info.setObjectCount(objectCount);
                    info.setTotalSize(totalSize);
                } catch (Exception e) {
                    info.setObjectCount(0L);
                    info.setTotalSize(0L);
                }

                result.add(info);
            }

            // 排序
            result.sort((b1, b2) -> {
                int resultVal;
                switch (sortBy) {
                    case "size":
                        resultVal = Long.compare(b1.getTotalSize() != null ? b1.getTotalSize() : 0, b2.getTotalSize() != null ? b2.getTotalSize() : 0);
                        break;
                    case "time":
                        resultVal = (b1.getCreationDate() != null ? b1.getCreationDate() : LocalDateTime.MIN).compareTo(b2.getCreationDate() != null ? b2.getCreationDate() : LocalDateTime.MIN);
                        break;
                    default: // name
                        resultVal = (b1.getName() != null ? b1.getName() : "").compareTo(b2.getName() != null ? b2.getName() : "");
                }
                return "desc".equalsIgnoreCase(order) ? -resultVal : resultVal;
            });

            return result;
        } catch (Exception e) {
            log.error("Failed to list buckets", e);
            throw new RuntimeException("获取Bucket列表失败: " + e.getMessage());
        }
    }

    /**
     * 创建Bucket
     */
    public BucketInfo createBucket(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );

            if (exists) {
                throw new RuntimeException("Bucket已存在: " + bucketName);
            }

            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(bucketName).build()
            );

            BucketInfo info = new BucketInfo();
            info.setName(bucketName);
            info.setCreationDate(LocalDateTime.now());
            info.setObjectCount(0L);
            info.setTotalSize(0L);

            log.info("Bucket created: {}", bucketName);
            return info;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create bucket: {}", bucketName, e);
            throw new RuntimeException("创建Bucket失败: " + e.getMessage());
        }
    }

    /**
     * 删除Bucket
     */
    public void deleteBucket(String bucketName) {
        try {
            // 先删除所有对象
            Iterable<Result<Item>> objects = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .recursive(true)
                            .build()
            );

            for (Result<Item> result : objects) {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(result.get().objectName())
                                .build()
                );
            }

            // 再删除bucket
            minioClient.removeBucket(
                    RemoveBucketArgs.builder().bucket(bucketName).build()
            );

            log.info("Bucket deleted: {}", bucketName);
        } catch (Exception e) {
            log.error("Failed to delete bucket: {}", bucketName, e);
            throw new RuntimeException("删除Bucket失败: " + e.getMessage());
        }
    }

    /**
     * 获取默认Bucket名称
     */
    public String getDefaultBucket() {
        return defaultBucket;
    }

    /**
     * 上传文件
     */
    public UploadResponse uploadFile(MultipartFile file, String folder, String bucket) {
        if (bucket == null || bucket.isEmpty()) {
            bucket = defaultBucket;
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String extension = getFileExtension(originalFilename).toLowerCase();

            // 检查文件类型是否允许
            Set<String> allowedExtensions = configService.getAllowedExtensions();
            if (!allowedExtensions.isEmpty() && !allowedExtensions.contains(extension)) {
                throw new RuntimeException("不允许上传此类文件: " + extension);
            }

            // 检查文件大小
            Long maxSize = configService.getMaxFileSize();
            if (maxSize > 0 && file.getSize() > maxSize) {
                throw new RuntimeException("文件大小超出限制: 最大 " + formatSize(maxSize));
            }

            // 生成唯一文件名
            String objectName = generateObjectName(folder, originalFilename, bucket);

            // 上传到 MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            // 获取文件 Etag
            StatObjectArgs statArgs = StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build();
            StatObjectResponse stat = minioClient.statObject(statArgs);

            UploadResponse response = new UploadResponse();
            response.setObjectName(objectName);
            response.setFileName(originalFilename);
            response.setSize(file.getSize());
            response.setContentType(file.getContentType());
            response.setUrl(publicUrl + "/" + bucket + "/" + objectName);
            response.setEtag(stat.etag());

            log.info("File uploaded successfully: {}", objectName);
            return response;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件列表
     */
    public List<FileInfo> listFiles(String folder, String bucket, String sortBy, String order) {
        if (bucket == null || bucket.isEmpty()) {
            bucket = defaultBucket;
        }

        try {
            // 确保 folder 以 / 结尾
            if (StrUtil.isNotEmpty(folder) && !folder.endsWith("/")) {
                folder = folder + "/";
            }

            // 列出指定前缀的所有对象
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(folder)
                            .recursive(false)
                            .build()
            );

            List<FileInfo> fileList = new ArrayList<>();
            for (Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName();

                // 过滤掉当前查询的目录本身
                if (objectName.equals(folder)) {
                    log.info("Skipping current folder itself: {}", objectName);
                    continue;
                }

                // 判断是否是目录：要么item.isDir()为true，要么objectName以/结尾
                boolean isFolder = item.isDir() || objectName.endsWith("/");

                // 如果是0字节文件，且文件名在当前目录下存在同名目录，则跳过这个0字节文件
                if (!isFolder && item.size() == 0) {
                    // 检查是否有同名目录
                    boolean hasFolderWithSameName = false;
                    for (FileInfo fi : fileList) {
                        if (fi.getIsFolder() != null && fi.getIsFolder()) {
                            // 获取目录名（不带后缀/）
                            String folderName = fi.getFileName();
                            String fileName = getFileName(objectName);
                            if (folderName.equals(fileName)) {
                                hasFolderWithSameName = true;
                                break;
                            }
                        }
                    }
                    if (hasFolderWithSameName) {
                        log.info("Skipping 0-byte file that has same name as folder: {}", objectName);
                        continue;
                    }
                }

                FileInfo fileInfo = new FileInfo();
                fileInfo.setObjectName(objectName);
                fileInfo.setFileName(getFileName(objectName));
                fileInfo.setSize(item.size());
                fileInfo.setIsFolder(isFolder);
                try {
                    if (item.lastModified() != null) {
                        fileInfo.setUploadTime(LocalDateTime.ofInstant(
                                item.lastModified().toInstant(), ZoneId.systemDefault()));
                    } else {
                        fileInfo.setUploadTime(LocalDateTime.now());
                    }
                } catch (Exception e) {
                    fileInfo.setUploadTime(LocalDateTime.now());
                }
                fileInfo.setUrl(publicUrl + "/" + bucket + "/" + objectName);
                fileList.add(fileInfo);
            }

            // 排序：默认先显示目录再显示文件
            fileList.sort((f1, f2) -> {
                // 首先比较是否是目录，目录排在前面
                boolean f1IsFolder = f1.getIsFolder() != null && f1.getIsFolder();
                boolean f2IsFolder = f2.getIsFolder() != null && f2.getIsFolder();

                if (f1IsFolder != f2IsFolder) {
                    return f1IsFolder ? -1 : 1;
                }

                // 如果都是目录或都是文件，再按指定条件排序
                int result;
                switch (sortBy) {
                    case "size":
                        result = Long.compare(f1.getSize() != null ? f1.getSize() : 0, f2.getSize() != null ? f2.getSize() : 0);
                        break;
                    case "time":
                        result = (f1.getUploadTime() != null ? f1.getUploadTime() : LocalDateTime.MIN).compareTo(f2.getUploadTime() != null ? f2.getUploadTime() : LocalDateTime.MIN);
                        break;
                    default: // name
                        result = (f1.getFileName() != null ? f1.getFileName() : "").compareTo(f2.getFileName() != null ? f2.getFileName() : "");
                }
                return "desc".equalsIgnoreCase(order) ? -result : result;
            });

            return fileList;
        } catch (Exception e) {
            log.error("Failed to list files", e);
            throw new RuntimeException("获取文件列表失败: " + e.getMessage());
        }
    }

    /**
     * 分页获取文件列表 - MinIO级别分页
     * 使用startAfter实现真正的MinIO层分页，避免加载所有文件到内存
     */
    public PageResult<FileInfo> listFiles(String folder, String bucket, String sortBy, String order, int page, int size) {
        // 参数校验和默认值
        if (page < 1) page = 1;
        if (size != 10 && size != 20 && size != 50 && size != 100) {
            size = 10; // 默认每页10条
        }

        if (bucket == null || bucket.isEmpty()) {
            bucket = defaultBucket;
        }

        // 确保 folder 以 / 结尾
        if (StrUtil.isNotEmpty(folder) && !folder.endsWith("/")) {
            folder = folder + "/";
        }

        try {
            // 计算startAfter：如果是第一页则为空，否则使用上一页最后一个文件名
            String startAfter = null;
            if (page > 1) {
                // 需要先获取前N页的最后一个文件名作为startAfter
                // 由于MinIO的startAfter是基于字典序的，对于分页我们使用一个技巧：
                // 先获取前一页的起始marker，然后获取前一页的所有文件，找到最后一个
                startAfter = calculateStartAfterForPage(folder, bucket, sortBy, order, page - 1, size);
                if (startAfter == null) {
                    // 如果无法计算startAfter，返回空（可能数据已变化）
                    return new PageResult<>(new ArrayList<>(), 0, page, size);
                }
            }

            // 使用MinIO的listObjects进行分页查询
            ListObjectsArgs.Builder argsBuilder = ListObjectsArgs.builder()
                    .bucket(bucket)
                    .prefix(folder)
                    .recursive(false)
                    .maxKeys(size + 1); // 多取一条来判断是否有下一页

            if (startAfter != null) {
                argsBuilder.startAfter(startAfter);
            }

            Iterable<Result<Item>> results = minioClient.listObjects(argsBuilder.build());

            List<FileInfo> fileList = new ArrayList<>();
            String lastObjectName = null;
            boolean hasMore = false;
            int count = 0;

            for (Result<Item> result : results) {
                Item item = result.get();
                String objectName = item.objectName();

                // 过滤掉当前查询的目录本身
                if (objectName.equals(folder)) {
                    continue;
                }

                // 判断是否是目录
                boolean isFolder = item.isDir() || objectName.endsWith("/");

                // 0字节文件且有同名目录的处理
                if (!isFolder && item.size() == 0) {
                    boolean hasFolderWithSameName = false;
                    for (FileInfo fi : fileList) {
                        if (fi.getIsFolder() != null && fi.getIsFolder()) {
                            String folderName = fi.getFileName();
                            String fileName = getFileName(objectName);
                            if (folderName.equals(fileName)) {
                                hasFolderWithSameName = true;
                                break;
                            }
                        }
                    }
                    if (hasFolderWithSameName) {
                        continue;
                    }
                }

                count++;
                // 如果已经取了size+1条，说明有下一页
                if (count > size) {
                    hasMore = true;
                    continue;
                }

                lastObjectName = objectName;

                FileInfo fileInfo = new FileInfo();
                fileInfo.setObjectName(objectName);
                fileInfo.setFileName(getFileName(objectName));
                fileInfo.setSize(item.size());
                fileInfo.setIsFolder(isFolder);
                try {
                    if (item.lastModified() != null) {
                        fileInfo.setUploadTime(LocalDateTime.ofInstant(
                                item.lastModified().toInstant(), ZoneId.systemDefault()));
                    } else {
                        fileInfo.setUploadTime(LocalDateTime.now());
                    }
                } catch (Exception e) {
                    fileInfo.setUploadTime(LocalDateTime.now());
                }
                fileInfo.setUrl(publicUrl + "/" + bucket + "/" + objectName);
                fileList.add(fileInfo);
            }

            // 对当前页进行排序
            fileList.sort((f1, f2) -> {
                boolean f1IsFolder = f1.getIsFolder() != null && f1.getIsFolder();
                boolean f2IsFolder = f2.getIsFolder() != null && f2.getIsFolder();

                if (f1IsFolder != f2IsFolder) {
                    return f1IsFolder ? -1 : 1;
                }

                int result;
                switch (sortBy) {
                    case "size":
                        result = Long.compare(f1.getSize() != null ? f1.getSize() : 0, f2.getSize() != null ? f2.getSize() : 0);
                        break;
                    case "time":
                        result = (f1.getUploadTime() != null ? f1.getUploadTime() : LocalDateTime.MIN).compareTo(f2.getUploadTime() != null ? f2.getUploadTime() : LocalDateTime.MIN);
                        break;
                    default:
                        result = (f1.getFileName() != null ? f1.getFileName() : "").compareTo(f2.getFileName() != null ? f2.getFileName() : "");
                }
                return "desc".equalsIgnoreCase(order) ? -result : result;
            });

            // 计算总数（估算：如果hasMore为true，总数至少是page*size）
            long total = hasMore ? (long) page * size : fileList.size();
            if (hasMore) {
                // 更准确的估算：假设还有更多数据
                total = (long) page * size + 1;
            }

            return new PageResult<>(fileList, total, page, size, hasMore ? lastObjectName : null, hasMore);

        } catch (Exception e) {
            log.error("Failed to list files with pagination", e);
            throw new RuntimeException("获取文件列表失败: " + e.getMessage());
        }
    }

    /**
     * 计算指定页码的startAfter参数
     * 通过获取前一页的最后一个文件名来实现
     */
    private String calculateStartAfterForPage(String folder, String bucket, String sortBy, String order, int targetPage, int size) {
        // 对于非name排序，我们无法直接用startAfter计算
        // 这种情况下降级为应用层分页（但限制总数）
        if (!"name".equals(sortBy) && !"".equals(sortBy) && sortBy != null) {
            // 对于size/time排序，MinIO不支持，我们只能获取所有然后分页
            // 但这种情况很少见，且大多数场景下文件数量不会太多
            return calculateStartAfterByFetchingAll(folder, bucket, sortBy, order, targetPage, size);
        }

        try {
            // 使用startAfter迭代获取前一页
            String startAfter = null;

            for (int i = 0; i < targetPage; i++) {
                ListObjectsArgs.Builder argsBuilder = ListObjectsArgs.builder()
                        .bucket(bucket)
                        .prefix(folder)
                        .recursive(false)
                        .maxKeys(size);

                if (startAfter != null) {
                    argsBuilder.startAfter(startAfter);
                }

                Iterable<Result<Item>> results = minioClient.listObjects(argsBuilder.build());

                String lastName = null;
                int count = 0;
                boolean hasMore = false;

                for (Result<Item> result : results) {
                    Item item = result.get();
                    String objectName = item.objectName();

                    // 过滤掉当前查询的目录本身
                    if (objectName.equals(folder)) {
                        continue;
                    }

                    count++;
                    if (count > size) {
                        hasMore = true;
                        break;
                    }
                    lastName = objectName;
                }

                if (lastName == null) {
                    return null;
                }

                startAfter = lastName;

                if (!hasMore) {
                    // 没有更多数据了
                    return null;
                }
            }

            return startAfter;
        } catch (Exception e) {
            log.error("Failed to calculate startAfter for page {}", targetPage, e);
            return null;
        }
    }

    /**
     * 通过获取所有文件来计算startAfter（适用于非name排序）
     * 限制最大获取数量为10000以防止内存溢出
     */
    private String calculateStartAfterByFetchingAll(String folder, String bucket, String sortBy, String order, int targetPage, int size) {
        try {
            List<FileInfo> allFiles = new ArrayList<>();
            int maxTotal = 10000; // 限制最大获取数量

            // 列出所有对象
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(folder)
                            .recursive(false)
                            .build()
            );

            for (Result<Item> result : results) {
                if (allFiles.size() >= maxTotal) {
                    break; // 达到上限，停止获取
                }

                Item item = result.get();
                String objectName = item.objectName();

                if (objectName.equals(folder)) {
                    continue;
                }

                boolean isFolder = item.isDir() || objectName.endsWith("/");

                FileInfo fileInfo = new FileInfo();
                fileInfo.setObjectName(objectName);
                fileInfo.setFileName(getFileName(objectName));
                fileInfo.setSize(item.size());
                fileInfo.setIsFolder(isFolder);
                try {
                    if (item.lastModified() != null) {
                        fileInfo.setUploadTime(LocalDateTime.ofInstant(
                                item.lastModified().toInstant(), ZoneId.systemDefault()));
                    } else {
                        fileInfo.setUploadTime(LocalDateTime.now());
                    }
                } catch (Exception e) {
                    fileInfo.setUploadTime(LocalDateTime.now());
                }
                allFiles.add(fileInfo);
            }

            // 排序
            allFiles.sort((f1, f2) -> {
                boolean f1IsFolder = f1.getIsFolder() != null && f1.getIsFolder();
                boolean f2IsFolder = f2.getIsFolder() != null && f2.getIsFolder();

                if (f1IsFolder != f2IsFolder) {
                    return f1IsFolder ? -1 : 1;
                }

                int result;
                switch (sortBy) {
                    case "size":
                        result = Long.compare(f1.getSize() != null ? f1.getSize() : 0, f2.getSize() != null ? f2.getSize() : 0);
                        break;
                    case "time":
                        result = (f1.getUploadTime() != null ? f1.getUploadTime() : LocalDateTime.MIN).compareTo(f2.getUploadTime() != null ? f2.getUploadTime() : LocalDateTime.MIN);
                        break;
                    default:
                        result = (f1.getFileName() != null ? f1.getFileName() : "").compareTo(f2.getFileName() != null ? f2.getFileName() : "");
                }
                return "desc".equalsIgnoreCase(order) ? -result : result;
            });

            // 计算偏移量
            int offset = (targetPage - 1) * size;
            if (offset >= allFiles.size()) {
                return null;
            }

            // 返回offset位置的文件名作为startAfter
            return allFiles.get(offset).getObjectName();

        } catch (Exception e) {
            log.error("Failed to calculate startAfter by fetching all", e);
            return null;
        }
    }

    /**
     * 删除文件
     */
    public void deleteFile(String objectName, String bucket) {
        if (bucket == null || bucket.isEmpty()) {
            bucket = defaultBucket;
        }

        try {
            // 检查是否是文件夹（以/结尾）
            boolean isFolder = objectName.endsWith("/");

            // 如果是文件夹，删除文件夹内的所有文件
            if (isFolder) {
                // 列出文件夹下所有对象并删除
                Iterable<Result<Item>> results = minioClient.listObjects(
                        ListObjectsArgs.builder()
                                .bucket(bucket)
                                .prefix(objectName)
                                .recursive(true)
                                .build()
                );
                for (Result<Item> result : results) {
                    Item item = result.get();
                    if (!item.isDir()) {
                        minioClient.removeObject(
                                RemoveObjectArgs.builder()
                                        .bucket(bucket)
                                        .object(item.objectName())
                                        .build()
                        );
                    }
                }
                // 最后删除文件夹本身
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectName)
                                .build()
                );
            } else {
                // 普通文件直接删除
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectName)
                                .build()
                );
            }
            log.info("File deleted: {}", objectName);
        } catch (Exception e) {
            log.error("Failed to delete file: {}", objectName, e);
            throw new RuntimeException("文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 创建文件夹
     * @param folderPath 文件夹路径
     * @param bucket 存储桶
     * @param createTestFile 是否创建测试文件
     */
    public void createFolder(String folderPath, String bucket, boolean createTestFile) {
        if (bucket == null || bucket.isEmpty()) {
            bucket = defaultBucket;
        }

        try {
            if (!folderPath.endsWith("/")) {
                folderPath = folderPath + "/";
            }
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(folderPath)
                            .stream(new ByteArrayInputStream(new byte[0]), 0, -1)
                            .build()
            );
            log.info("Folder created: {}", folderPath);

            // 如果需要创建测试文件
            if (createTestFile) {
                String testFilePath = folderPath + "test.txt";
                String testContent = "This is a test file.";
                ByteArrayInputStream testStream = new ByteArrayInputStream(testContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(testFilePath)
                                .stream(testStream, testContent.length(), -1)
                                .contentType("text/plain")
                                .build()
                );
                log.info("Test file created: {}", testFilePath);
            }
        } catch (Exception e) {
            log.error("Failed to create folder: {}", folderPath, e);
            throw new RuntimeException("创建文件夹失败: " + e.getMessage());
        }
    }

    /**
     * 创建文件夹（兼容旧版本，默认不创建测试文件）
     */
    public void createFolder(String folderPath, String bucket) {
        createFolder(folderPath, bucket, false);
    }

    /**
     * 获取文件流
     */
    public InputStream getFileStream(String objectName, String bucket) {
        if (bucket == null || bucket.isEmpty()) {
            bucket = defaultBucket;
        }

        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
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
    public FileInfo getFileInfo(String objectName, String bucket) {
        if (bucket == null || bucket.isEmpty()) {
            bucket = defaultBucket;
        }

        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
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
            fileInfo.setUrl(publicUrl + "/" + bucket + "/" + objectName);
            fileInfo.setIsFolder(objectName.endsWith("/"));

            return fileInfo;
        } catch (Exception e) {
            log.error("Failed to get file info: {}", objectName, e);
            throw new RuntimeException("获取文件信息失败: " + e.getMessage());
        }
    }

    /**
     * 获取文件信息（使用默认bucket）
     */
    public FileInfo getFileInfo(String objectName) {
        return getFileInfo(objectName, defaultBucket);
    }

    /**
     * 复制文件
     */
    public void copyFile(String sourceObjectName, String destObjectName, String sourceBucket, String destBucket) {
        if (sourceBucket == null || sourceBucket.isEmpty()) {
            sourceBucket = defaultBucket;
        }
        if (destBucket == null || destBucket.isEmpty()) {
            destBucket = defaultBucket;
        }

        try {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(destBucket)
                            .object(destObjectName)
                            .source(
                                    CopySource.builder()
                                            .bucket(sourceBucket)
                                            .object(sourceObjectName)
                                            .build()
                            )
                            .build()
            );
            log.info("File copied from {}/{} to {}/{}", sourceBucket, sourceObjectName, destBucket, destObjectName);
        } catch (Exception e) {
            log.error("Failed to copy file", e);
            throw new RuntimeException("文件复制失败: " + e.getMessage());
        }
    }

    /**
     * 移动文件
     */
    public void moveFile(String sourceObjectName, String destObjectName, String sourceBucket, String destBucket) {
        copyFile(sourceObjectName, destObjectName, sourceBucket, destBucket);
        deleteFile(sourceObjectName, sourceBucket);
        log.info("File moved from {}/{} to {}/{}", sourceBucket, sourceObjectName, destBucket, destObjectName);
    }

    /**
     * 获取文件预览URL
     */
    public String getPreviewUrl(String objectName, String bucket) {
        if (bucket == null || bucket.isEmpty()) {
            bucket = defaultBucket;
        }
        return publicUrl + "/" + bucket + "/" + objectName;
    }

    /**
     * 递归列出目录下的所有文件
     */
    public List<FileInfo> listFilesRecursive(String folder, String bucket) {
        if (bucket == null || bucket.isEmpty()) {
            bucket = defaultBucket;
        }

        try {
            // 确保 folder 以 / 结尾
            if (StrUtil.isNotEmpty(folder) && !folder.endsWith("/")) {
                folder = folder + "/";
            }

            // 递归列出所有对象
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(folder)
                            .recursive(true)
                            .build()
            );

            List<FileInfo> fileList = new ArrayList<>();
            for (Result<Item> result : results) {
                Item item = result.get();
                // 跳过目录本身
                if (item.isDir()) {
                    continue;
                }
                FileInfo fileInfo = new FileInfo();
                fileInfo.setObjectName(item.objectName());
                // 获取相对路径作为文件名（去掉folder前缀）
                String relativePath = item.objectName();
                if (StrUtil.isNotEmpty(folder) && relativePath.startsWith(folder)) {
                    relativePath = relativePath.substring(folder.length());
                }
                fileInfo.setFileName(relativePath);
                fileInfo.setSize(item.size());
                fileInfo.setIsFolder(false);
                try {
                    if (item.lastModified() != null) {
                        fileInfo.setUploadTime(LocalDateTime.ofInstant(
                                item.lastModified().toInstant(), ZoneId.systemDefault()));
                    } else {
                        fileInfo.setUploadTime(LocalDateTime.now());
                    }
                } catch (Exception e) {
                    fileInfo.setUploadTime(LocalDateTime.now());
                }
                fileList.add(fileInfo);
            }

            return fileList;
        } catch (Exception e) {
            log.error("Failed to list files recursively", e);
            throw new RuntimeException("获取文件列表失败: " + e.getMessage());
        }
    }

    /**
     * 将目录压缩成ZIP并写入输出流
     */
    public void downloadFolderAsZip(String folder, String bucket, OutputStream outputStream) {
        if (bucket == null || bucket.isEmpty()) {
            bucket = defaultBucket;
        }

        // 确保 folder 以 / 结尾
        if (StrUtil.isNotEmpty(folder) && !folder.endsWith("/")) {
            folder = folder + "/";
        }

        // 获取目录名
        String folderName = folder;
        if (StrUtil.isNotEmpty(folder)) {
            String[] parts = folder.split("/");
            folderName = parts[parts.length - 2];
            if (folderName.isEmpty()) {
                folderName = "folder";
            }
        } else {
            folderName = "root";
        }

        try {
            // 获取所有文件
            List<FileInfo> files = listFilesRecursive(folder, bucket);

            // 计算总大小（解压后）
            long totalSize = 0;
            for (FileInfo file : files) {
                totalSize += file.getSize() != null ? file.getSize() : 0;
            }

            // 检查是否超过2GB
            long maxSize = 2147483648L; // 2GB
            if (totalSize > maxSize) {
                throw new RuntimeException("目录压缩后大小超过2GB，无法下载");
            }

            // 创建ZIP输出流
            try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
                byte[] buffer = new byte[8192];

                for (FileInfo file : files) {
                    // 获取文件流
                    InputStream is = getFileStream(file.getObjectName(), bucket);

                    // 创建ZIP条目（使用完整目录结构）
                    String entryName = file.getObjectName();
                    ZipEntry zipEntry = new ZipEntry(entryName);
                    zos.putNextEntry(zipEntry);

                    // 复制文件内容到ZIP
                    long bytesWritten = 0;
                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        bytesWritten += len;
                        // 再次检查大小（因为压缩后可能比原文件大）
                        if (bytesWritten > maxSize) {
                            is.close();
                            throw new RuntimeException("目录压缩后大小超过2GB，无法下载");
                        }
                        zos.write(buffer, 0, len);
                    }

                    zos.closeEntry();
                    is.close();
                }

                zos.finish();
            }

            log.info("Folder downloaded as ZIP: {}", folder);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to download folder as ZIP: {}", folder, e);
            throw new RuntimeException("目录下载失败: " + e.getMessage());
        }
    }

    /**
     * 批量下载多个文件/目录为ZIP
     */
    public void downloadBatchAsZip(List<String> objectNames, String bucket, OutputStream outputStream) {
        if (bucket == null || bucket.isEmpty()) {
            bucket = defaultBucket;
        }

        try {
            // 创建ZIP输出流
            try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
                byte[] buffer = new byte[8192];
                long maxSize = 2147483648L; // 2GB

                for (String objectName : objectNames) {
                    // 判断是否是目录（以/结尾）
                    boolean isFolder = objectName.endsWith("/");

                    if (isFolder) {
                        // 如果是目录，递归获取所有文件
                        try {
                            List<FileInfo> folderFiles = listFilesRecursive(objectName, bucket);
                            for (FileInfo subFile : folderFiles) {
                                addFileToZip(zos, subFile, bucket, buffer, maxSize);
                            }
                        } catch (Exception e) {
                            log.warn("Failed to list folder {}: {}", objectName, e.getMessage());
                        }
                    } else {
                        // 文件直接添加到ZIP
                        try {
                            FileInfo fileInfo = getFileInfo(objectName, bucket);
                            addFileToZip(zos, fileInfo, bucket, buffer, maxSize);
                        } catch (Exception e) {
                            log.warn("Failed to get file {}: {}", objectName, e.getMessage());
                        }
                    }
                }

                zos.finish();
            }

            log.info("Batch files downloaded as ZIP: {} items", objectNames.size());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to download batch as ZIP", e);
            throw new RuntimeException("批量下载失败: " + e.getMessage());
        }
    }

    private void addFileToZip(ZipOutputStream zos, FileInfo fileInfo, String bucket, byte[] buffer, long maxSize) throws Exception {
        InputStream is = getFileStream(fileInfo.getObjectName(), bucket);

        // 使用 objectName 保留完整目录结构
        String entryName = fileInfo.getObjectName();
        ZipEntry zipEntry = new ZipEntry(entryName);
        zos.putNextEntry(zipEntry);

        long bytesWritten = 0;
        int len;
        while ((len = is.read(buffer)) > 0) {
            bytesWritten += len;
            if (bytesWritten > maxSize) {
                is.close();
                throw new RuntimeException("批量下载压缩后大小超过2GB，无法下载");
            }
            zos.write(buffer, 0, len);
        }

        zos.closeEntry();
        is.close();
    }

    // ===== 私有方法 =====

    private String generateObjectName(String folder, String originalFilename, String bucket) {
        String extension = getFileExtension(originalFilename);

        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotEmpty(folder)) {
            if (!folder.endsWith("/")) {
                sb.append(folder).append("/");
            } else {
                sb.append(folder);
            }
        }

        // 保留原始文件名，支持中文，只替换真正的非法字符
        String nameWithoutExt;
        if (StrUtil.isNotEmpty(extension)) {
            nameWithoutExt = originalFilename.substring(0,
                    Math.max(0, originalFilename.length() - extension.length() - 1));
        } else {
            nameWithoutExt = originalFilename;
        }
        // 只替换MinIO/S3中真正非法的字符，保留中文和其他语言字符
        nameWithoutExt = nameWithoutExt.replaceAll("[\\\\/:*?\"<>|]", "_");

        if (StrUtil.isNotEmpty(extension)) {
            sb.append(nameWithoutExt).append(".").append(extension);
        } else {
            sb.append(nameWithoutExt);
        }

        String objectName = sb.toString();

        // 检查文件是否已存在，如存在则添加数字后缀
        if (fileExists(objectName, bucket)) {
            int counter = 2;
            String baseName = nameWithoutExt;
            while (true) {
                String newObjectName;
                if (StrUtil.isNotEmpty(extension)) {
                    newObjectName = sb.toString().replace(baseName + "." + extension, baseName + "_" + counter + "." + extension);
                } else {
                    newObjectName = sb.toString().replace(baseName, baseName + "_" + counter);
                }
                if (!fileExists(newObjectName, bucket)) {
                    return newObjectName;
                }
                counter++;
                if (counter > 1000) {
                    throw new RuntimeException("文件命名冲突，无法创建唯一文件名");
                }
            }
        }

        return objectName;
    }

    // 检查文件是否已存在
    private boolean fileExists(String objectName, String bucket) {
        try {
            minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(objectName).build());
            return true;
        } catch (Exception e) {
            return false;
        }
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

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
