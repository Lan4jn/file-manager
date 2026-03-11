package com.poss.file.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.poss.file.dto.CreateShareRequest;
import com.poss.file.model.FileInfo;
import com.poss.file.model.ShareFileRecord;
import com.poss.file.model.ShareLink;
import com.poss.file.repository.ShareFileRecordRepository;
import com.poss.file.repository.ShareLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ShareService {

    private static final Logger log = LoggerFactory.getLogger(ShareService.class);
    private static final long MAX_SHARE_SIZE_BYTES = 2147483648L; // 2GB

    private final FileService fileService;
    private final ShareLinkRepository shareLinkRepository;
    private final ShareFileRecordRepository shareFileRecordRepository;
    private final ConfigService configService;

    @Value("${share.default-expiration: 24}")
    private Integer defaultExpiration;

    @Value("${share.directory:./share}")
    private String shareDirectory;

    public ShareService(FileService fileService, ShareLinkRepository shareLinkRepository,
                        ShareFileRecordRepository shareFileRecordRepository, ConfigService configService) {
        this.fileService = fileService;
        this.shareLinkRepository = shareLinkRepository;
        this.shareFileRecordRepository = shareFileRecordRepository;
        this.configService = configService;
    }

    /**
     * 创建分享链接
     */
    public ShareLink createShareLink(CreateShareRequest request, String createBy) {
        String shareCode = generateShareCode();
        Integer expirationHours = request.getExpirationHours();
        if (expirationHours == null || expirationHours <= 0) {
            expirationHours = defaultExpiration;
        }

        String bucket = request.getBucket();
        String objectName = request.getObjectName();

        // 判断是否是目录分享
        boolean isFolder = objectName.endsWith("/");

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode(shareCode);
        shareLink.setObjectName(objectName);
        shareLink.setBucket(bucket);

        // 处理目录名
        String folderName;
        if (isFolder) {
            // 目录：去掉末尾的斜杠，获取目录名
            folderName = objectName;
            if (folderName.endsWith("/")) {
                folderName = folderName.substring(0, folderName.length() - 1);
            }
            int lastSlash = folderName.lastIndexOf('/');
            if (lastSlash >= 0) {
                folderName = folderName.substring(lastSlash + 1);
            }
        } else {
            folderName = fileService.getFileInfo(objectName, bucket).getFileName();
        }
        shareLink.setFileName(folderName);
        shareLink.setExpireTime(LocalDateTime.now().plusHours(expirationHours));
        shareLink.setCreateTime(LocalDateTime.now());
        shareLink.setCreateBy(createBy);
        shareLink.setViewCount(0L);
        shareLink.setHasPassword(StrUtil.isNotEmpty(request.getPassword()));
        shareLink.setPassword(request.getPassword());
        shareLink.setIsFolder(isFolder);

        // 如果是目录分享，需要先压缩并保存
        if (isFolder) {
            try {
                // 获取目录下的所有文件
                List<FileInfo> files = fileService.listFilesRecursive(objectName, bucket);

                // 计算总大小
                long totalSize = 0;
                for (FileInfo file : files) {
                    totalSize += file.getSize() != null ? file.getSize() : 0;
                }

                // 检查是否超过2GB
                if (totalSize > MAX_SHARE_SIZE_BYTES) {
                    throw new RuntimeException("目录压缩后大小超过2GB，无法分享");
                }

                // 确保share目录存在
                File shareDir = new File(shareDirectory);
                if (!shareDir.exists()) {
                    shareDir.mkdirs();
                }

                // 生成压缩包文件名
                String zipFileName = shareCode + ".zip";
                String zipFilePath = shareDirectory + "/" + zipFileName;
                shareLink.setZipFileName(zipFileName);

                // 保存文件记录（包含MD5）
                List<ShareFileRecord> records = new ArrayList<>();
                LocalDateTime now = LocalDateTime.now();

                // 创建压缩包
                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFilePath))) {
                    byte[] buffer = new byte[8192];

                    for (FileInfo file : files) {
                        // 获取文件流
                        InputStream is = fileService.getFileStream(file.getObjectName(), bucket);

                        // 计算MD5
                        String md5 = calculateMD5(is);
                        is.close();

                        // 重新获取流用于压缩
                        is = fileService.getFileStream(file.getObjectName(), bucket);

                        // 创建ZIP条目
                        String entryName = file.getFileName();
                        ZipEntry zipEntry = new ZipEntry(entryName);
                        zos.putNextEntry(zipEntry);

                        // 复制文件内容
                        int len;
                        while ((len = is.read(buffer)) > 0) {
                            zos.write(buffer, 0, len);
                        }

                        zos.closeEntry();
                        is.close();

                        // 保存文件记录
                        ShareFileRecord record = new ShareFileRecord();
                        record.setShareCode(shareCode);
                        record.setFileName(entryName);
                        record.setMd5(md5);
                        record.setFileSize(file.getSize());
                        record.setRecordTime(now);
                        records.add(record);
                    }
                }

                // 保存文件记录到数据库
                shareFileRecordRepository.saveAll(records);

                log.info("Folder compressed and saved: {}", zipFilePath);
            } catch (Exception e) {
                log.error("Failed to compress folder: {}", objectName, e);
                throw new RuntimeException("目录压缩失败: " + e.getMessage());
            }
        }

        shareLink = shareLinkRepository.save(shareLink);

        log.info("Share link created: {}", shareCode);
        return shareLink;
    }

    /**
     * 计算输入流的MD5值
     */
    private String calculateMD5(InputStream inputStream) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192];
        int len;

        while ((len = inputStream.read(buffer)) > 0) {
            md.update(buffer, 0, len);
        }

        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 获取share目录的路径
     */
    public String getShareDirectory() {
        return shareDirectory;
    }

    /**
     * 检查分享的压缩包是否存在
     */
    public boolean isZipFileExists(String shareCode) {
        String zipFilePath = shareDirectory + "/" + shareCode + ".zip";
        return new File(zipFilePath).exists();
    }

    /**
     * 获取分享的压缩包文件
     */
    public File getZipFile(String shareCode) {
        String zipFilePath = shareDirectory + "/" + shareCode + ".zip";
        File zipFile = new File(zipFilePath);
        if (zipFile.exists()) {
            return zipFile;
        }
        return null;
    }

    /**
     * 重新压缩分享目录（当压缩包不存在时）
     * 返回是否有文件变化的警告
     */
    public String recompressShareFolder(String shareCode) {
        Optional<ShareLink> optLink = shareLinkRepository.findByShareCode(shareCode);
        if (!optLink.isPresent()) {
            throw new RuntimeException("分享链接不存在");
        }

        ShareLink shareLink = optLink.get();
        if (!shareLink.getIsFolder()) {
            throw new RuntimeException("不是目录分享");
        }

        String objectName = shareLink.getObjectName();
        String bucket = shareLink.getBucket();

        try {
            // 获取当前目录下的所有文件
            List<FileInfo> currentFiles = fileService.listFilesRecursive(objectName, bucket);
            Map<String, FileInfo> currentFileMap = currentFiles.stream()
                    .collect(Collectors.toMap(FileInfo::getFileName, f -> f));

            // 获取数据库中记录的文件
            List<ShareFileRecord> records = shareFileRecordRepository.findByShareCode(shareCode);
            Map<String, ShareFileRecord> recordMap = records.stream()
                    .collect(Collectors.toMap(ShareFileRecord::getFileName, r -> r));

            // 检查文件变化
            List<String> warnings = new ArrayList<>();

            // 检查被删除或修改的文件
            for (Map.Entry<String, ShareFileRecord> entry : recordMap.entrySet()) {
                String fileName = entry.getKey();
                ShareFileRecord record = entry.getValue();

                if (!currentFileMap.containsKey(fileName)) {
                    warnings.add("文件已删除: " + fileName);
                } else {
                    // 重新计算MD5比对
                    FileInfo currentFile = currentFileMap.get(fileName);
                    if (!Objects.equals(record.getFileSize(), currentFile.getSize())) {
                        warnings.add("文件已修改: " + fileName);
                    }
                }
            }

            // 检查新增的文件
            for (String fileName : currentFileMap.keySet()) {
                if (!recordMap.containsKey(fileName)) {
                    warnings.add("新增文件: " + fileName);
                }
            }

            // 如果有警告，生成说明文件
            boolean hasWarnings = !warnings.isEmpty();
            String noteFileName = null;

            // 确保share目录存在
            File shareDir = new File(shareDirectory);
            if (!shareDir.exists()) {
                shareDir.mkdirs();
            }

            // 生成新的压缩包
            String zipFileName = shareCode + ".zip";
            String zipFilePath = shareDirectory + "/" + zipFileName;

            // 更新文件记录
            List<ShareFileRecord> newRecords = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFilePath))) {
                byte[] buffer = new byte[8192];

                // 如果有警告，先添加说明文件
                if (hasWarnings) {
                    StringBuilder noteContent = new StringBuilder();
                    noteContent.append("⚠️ 警告：此压缩包为重新生成，原分享的部分文件可能已变化\n");
                    noteContent.append("====================================\n");
                    noteContent.append("变化详情：\n");
                    for (String warning : warnings) {
                        noteContent.append("- ").append(warning).append("\n");
                    }
                    noteContent.append("====================================\n");
                    noteContent.append("请重新获取分享链接以获取最新文件\n");

                    noteFileName = "_share_warning.txt";
                    ZipEntry noteEntry = new ZipEntry(noteFileName);
                    zos.putNextEntry(noteEntry);
                    zos.write(noteContent.toString().getBytes("UTF-8"));
                    zos.closeEntry();
                }

                // 添加所有当前文件
                for (FileInfo file : currentFiles) {
                    InputStream is = fileService.getFileStream(file.getObjectName(), bucket);

                    String entryName = file.getFileName();
                    ZipEntry zipEntry = new ZipEntry(entryName);
                    zos.putNextEntry(zipEntry);

                    int len;
                    while ((len = is.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }

                    zos.closeEntry();
                    is.close();

                    // 计算并保存新的MD5记录
                    is = fileService.getFileStream(file.getObjectName(), bucket);
                    String md5 = calculateMD5(is);
                    is.close();

                    ShareFileRecord record = new ShareFileRecord();
                    record.setShareCode(shareCode);
                    record.setFileName(entryName);
                    record.setMd5(md5);
                    record.setFileSize(file.getSize());
                    record.setRecordTime(now);
                    newRecords.add(record);
                }
            }

            // 更新数据库记录
            shareFileRecordRepository.deleteByShareCode(shareCode);
            shareFileRecordRepository.saveAll(newRecords);

            // 更新分享链接的压缩包文件名
            shareLink.setZipFileName(zipFileName);
            shareLinkRepository.save(shareLink);

            log.info("Folder recompressed: {}", shareCode);

            return hasWarnings ? String.join("\n", warnings) : null;
        } catch (Exception e) {
            log.error("Failed to recompress folder: {}", shareCode, e);
            throw new RuntimeException("重新压缩失败: " + e.getMessage());
        }
    }

    /**
     * 清理过期的分享压缩包
     */
    public void cleanupExpiredShares() {
        try {
            List<ShareLink> allShares = shareLinkRepository.findAll();
            LocalDateTime now = LocalDateTime.now();

            for (ShareLink share : allShares) {
                if (now.isAfter(share.getExpireTime()) && share.getIsFolder()) {
                    // 删除压缩包文件
                    if (share.getZipFileName() != null) {
                        String zipFilePath = shareDirectory + "/" + share.getZipFileName();
                        File zipFile = new File(zipFilePath);
                        if (zipFile.exists()) {
                            zipFile.delete();
                        }
                    }
                    // 删除文件记录
                    shareFileRecordRepository.deleteByShareCode(share.getShareCode());
                }
            }

            log.info("Expired shares cleaned up");
        } catch (Exception e) {
            log.error("Failed to cleanup expired shares", e);
        }
    }

    /**
     * 获取分享信息
     */
    public ShareLink getShareInfo(String shareCode, String password) {
        ShareLink shareLink = shareLinkRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new RuntimeException("分享链接不存在"));

        // 检查是否过期
        if (LocalDateTime.now().isAfter(shareLink.getExpireTime())) {
            throw new RuntimeException("分享链接已过期");
        }

        // 检查密码
        if (shareLink.getHasPassword()) {
            String storedPwd = shareLink.getPassword();
            if (!password.equals(storedPwd)) {
                throw new RuntimeException("密码错误");
            }
        }

        // 更新浏览次数
        shareLink.setViewCount(shareLink.getViewCount() + 1);
        shareLinkRepository.save(shareLink);

        return shareLink;
    }

    /**
     * 通过分享链接获取文件信息
     */
    public FileInfo getFileByShareCode(String shareCode) {
        ShareLink shareLink = shareLinkRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new RuntimeException("分享链接不存在"));

        if (LocalDateTime.now().isAfter(shareLink.getExpireTime())) {
            throw new RuntimeException("分享链接已过期");
        }

        return fileService.getFileInfo(shareLink.getObjectName(), shareLink.getBucket());
    }

    /**
     * 验证分享链接是否有效
     */
    public boolean validateShareCode(String shareCode) {
        Optional<ShareLink> shareLink = shareLinkRepository.findByShareCode(shareCode);
        return shareLink.isPresent() && !LocalDateTime.now().isAfter(shareLink.get().getExpireTime());
    }

    /**
     * 删除分享链接
     */
    public void deleteShareLink(String shareCode) {
        shareLinkRepository.deleteByShareCode(shareCode);
        log.info("Share link deleted: {}", shareCode);
    }

    /**
     * 获取当前用户的所有分享链接
     */
    public List<ShareLink> getShareLinksByUser(String createBy) {
        return shareLinkRepository.findByCreateBy(createBy);
    }

    private String generateShareCode() {
        // 生成8位随机分享码
        return IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
    }
}
