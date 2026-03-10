package com.poss.file.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.poss.file.dto.CreateShareRequest;
import com.poss.file.model.FileInfo;
import com.poss.file.model.ShareLink;
import com.poss.file.repository.ShareLinkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ShareService {

    private static final Logger log = LoggerFactory.getLogger(ShareService.class);

    private final FileService fileService;
    private final ShareLinkRepository shareLinkRepository;

    @Value("${share.default-expiration: 24}")
    private Integer defaultExpiration;

    public ShareService(FileService fileService, ShareLinkRepository shareLinkRepository) {
        this.fileService = fileService;
        this.shareLinkRepository = shareLinkRepository;
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
        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode(shareCode);
        shareLink.setObjectName(request.getObjectName());
        shareLink.setBucket(bucket);
        shareLink.setFileName(fileService.getFileInfo(request.getObjectName(), bucket).getFileName());
        shareLink.setExpireTime(LocalDateTime.now().plusHours(expirationHours));
        shareLink.setCreateTime(LocalDateTime.now());
        shareLink.setCreateBy(createBy);
        shareLink.setViewCount(0L);
        shareLink.setHasPassword(StrUtil.isNotEmpty(request.getPassword()));
        shareLink.setPassword(request.getPassword()); // 简单存储，实际项目应加密

        shareLink = shareLinkRepository.save(shareLink);

        log.info("Share link created: {}", shareCode);
        return shareLink;
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
