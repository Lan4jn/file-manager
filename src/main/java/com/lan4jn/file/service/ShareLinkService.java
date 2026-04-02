package com.lan4jn.file.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.lan4jn.file.dto.CreateShareRequest;
import com.lan4jn.file.model.ShareAccessInfo;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.repository.ShareFileRecordRepository;
import com.lan4jn.file.repository.ShareLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class ShareLinkService {

    private static final Logger log = LoggerFactory.getLogger(ShareLinkService.class);

    private final FileService fileService;
    private final ShareLinkRepository shareLinkRepository;
    private final ShareFileRecordRepository shareFileRecordRepository;
    private final ShareSupport shareSupport;
    private final Integer defaultExpiration;

    ShareLinkService(FileService fileService,
                     ShareLinkRepository shareLinkRepository,
                     ShareFileRecordRepository shareFileRecordRepository,
                     ShareSupport shareSupport,
                     Integer defaultExpiration) {
        this.fileService = fileService;
        this.shareLinkRepository = shareLinkRepository;
        this.shareFileRecordRepository = shareFileRecordRepository;
        this.shareSupport = shareSupport;
        this.defaultExpiration = defaultExpiration;
    }

    ShareLink createShareLink(CreateShareRequest request, String createBy) {
        String shareCode = generateShareCode();
        Integer expirationHours = request.getExpirationHours();
        if (expirationHours == null || expirationHours <= 0) {
            expirationHours = defaultExpiration;
        }

        String bucket = request.getBucket();
        String objectName = request.getObjectName();
        boolean isFolder = objectName.endsWith("/");

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode(shareCode);
        shareLink.setObjectName(objectName);
        shareLink.setBucket(bucket);
        shareLink.setFileName(isFolder
                ? shareSupport.extractFolderName(objectName)
                : fileService.getFileInfo(objectName, bucket).getFileName());
        shareLink.setExpireTime(LocalDateTime.now().plusHours(expirationHours));
        shareLink.setCreateTime(LocalDateTime.now());
        shareLink.setCreateBy(createBy);
        shareLink.setViewCount(0L);
        shareLink.setHasPassword(StrUtil.isNotEmpty(request.getPassword()));
        shareLink.setPassword(request.getPassword());
        shareLink.setIsFolder(isFolder);
        shareLink.setZipFileName(null);

        ShareLink saved = shareLinkRepository.save(shareLink);
        log.info("Share link created: {}", shareCode);
        return saved;
    }

    ShareLink createBatchShareLink(List<String> objectNames, String bucket, String createBy,
                                   Integer expirationHours, String password) {
        if (objectNames == null || objectNames.isEmpty()) {
            throw new RuntimeException("分享对象不能为空");
        }

        String shareCode = generateShareCode();
        if (expirationHours == null || expirationHours <= 0) {
            expirationHours = defaultExpiration;
        }

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode(shareCode);
        shareLink.setObjectName(ShareSupport.BATCH_PREFIX + JSONUtil.toJsonStr(objectNames));
        shareLink.setBucket(bucket);
        shareLink.setFileName("批量分享_" + objectNames.size() + "项");
        shareLink.setExpireTime(LocalDateTime.now().plusHours(expirationHours));
        shareLink.setCreateTime(LocalDateTime.now());
        shareLink.setCreateBy(createBy);
        shareLink.setViewCount(0L);
        shareLink.setHasPassword(StrUtil.isNotEmpty(password));
        shareLink.setPassword(password);
        shareLink.setIsFolder(true);
        shareLink.setZipFileName(null);

        ShareLink saved = shareLinkRepository.save(shareLink);
        log.info("Batch share link created: {}", shareCode);
        return saved;
    }

    ShareAccessInfo getShareAccessInfo(String shareCode) {
        ShareLink shareLink = findValidShare(shareCode);
        ShareAccessInfo info = new ShareAccessInfo();
        info.setShareCode(shareLink.getShareCode());
        info.setFileName(shareLink.getFileName());
        info.setHasPassword(Boolean.TRUE.equals(shareLink.getHasPassword()));
        info.setIsFolder(Boolean.TRUE.equals(shareLink.getIsFolder()));
        info.setExpireTime(shareLink.getExpireTime());
        info.setDisplayPath(shareSupport.getShareDisplayPath(shareLink));
        return info;
    }

    ShareLink getShareInfo(String shareCode, String password) {
        return resolveShare(shareCode, password, true);
    }

    ShareLink peekShareInfo(String shareCode, String password) {
        return resolveShare(shareCode, password, false);
    }

    boolean validateShareCode(String shareCode) {
        Optional<ShareLink> shareLink = shareLinkRepository.findByShareCode(shareCode);
        return shareLink.isPresent() && !LocalDateTime.now().isAfter(shareLink.get().getExpireTime());
    }

    void deleteShareLink(String shareCode, String createBy) {
        ShareLink shareLink = shareLinkRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new RuntimeException("分享链接不存在"));
        if (!Objects.equals(shareLink.getCreateBy(), createBy)) {
            throw new RuntimeException("无权删除该分享");
        }
        shareFileRecordRepository.deleteByShareCode(shareCode);
        shareLinkRepository.delete(shareLink);
        log.info("Share link deleted: {}", shareCode);
    }

    List<ShareLink> getShareLinksByUser(String createBy) {
        return shareLinkRepository.findByCreateByOrderByCreateTimeDesc(createBy);
    }

    void cleanupExpiredShares() {
        log.info("Share cleanup skipped: modern share mode no longer stores ZIP archives");
    }

    ShareLink resolveShare(String shareCode, String password, boolean incrementView) {
        ShareLink shareLink = findValidShare(shareCode);
        if (Boolean.TRUE.equals(shareLink.getHasPassword())) {
            if (StrUtil.isBlank(password)) {
                throw new RuntimeException("请输入提取码");
            }
            if (!Objects.equals(password, shareLink.getPassword())) {
                throw new RuntimeException("提取码错误");
            }
        }

        if (incrementView) {
            shareLink.setViewCount((shareLink.getViewCount() == null ? 0L : shareLink.getViewCount()) + 1);
            shareLinkRepository.save(shareLink);
        }
        return shareLink;
    }

    void incrementViewCount(String shareCode) {
        ShareLink shareLink = findValidShare(shareCode);
        shareLink.setViewCount((shareLink.getViewCount() == null ? 0L : shareLink.getViewCount()) + 1);
        shareLinkRepository.save(shareLink);
    }

    private ShareLink findValidShare(String shareCode) {
        ShareLink shareLink = shareLinkRepository.findByShareCode(shareCode)
                .orElseThrow(() -> new RuntimeException("分享链接不存在"));
        if (LocalDateTime.now().isAfter(shareLink.getExpireTime())) {
            throw new RuntimeException("分享链接已过期");
        }
        return shareLink;
    }

    private String generateShareCode() {
        return IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
    }
}
