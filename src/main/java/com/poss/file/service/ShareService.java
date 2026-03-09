package com.poss.file.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.poss.file.dto.CreateShareRequest;
import com.poss.file.model.FileInfo;
import com.poss.file.model.ShareLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShareService {

    private final FileService fileService;

    @Value("${share.default-expiration: 24}")
    private Integer defaultExpiration;

    // 内存存储，实际项目应使用数据库
    private final Map<String, ShareLink> shareLinkMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> shareViewCountMap = new ConcurrentHashMap<>();

    /**
     * 创建分享链接
     */
    public ShareLink createShareLink(CreateShareRequest request, String createBy) {
        String shareCode = generateShareCode();
        Integer expirationHours = request.getExpirationHours();
        if (expirationHours == null || expirationHours <= 0) {
            expirationHours = defaultExpiration;
        }

        ShareLink shareLink = new ShareLink();
        shareLink.setShareCode(shareCode);
        shareLink.setObjectName(request.getObjectName());
        shareLink.setFileName(fileService.getFileInfo(request.getObjectName()).getFileName());
        shareLink.setExpireTime(LocalDateTime.now().plusHours(expirationHours));
        shareLink.setCreateTime(LocalDateTime.now());
        shareLink.setCreateBy(createBy);
        shareLink.setViewCount(0L);
        shareLink.setHasPassword(StrUtil.isNotEmpty(request.getPassword()));

        // 如果有密码，应该加密存储（简化处理）
        if (StrUtil.isNotEmpty(request.getPassword())) {
            shareLinkMap.put(shareCode + "_pwd", shareLink);
            // 实际项目需要加密存储密码
        }

        shareLinkMap.put(shareCode, shareLink);
        shareViewCountMap.put(shareCode, 0);

        log.info("Share link created: {}", shareCode);
        return shareLink;
    }

    /**
     * 获取分享信息
     */
    public ShareLink getShareInfo(String shareCode, String password) {
        ShareLink shareLink = shareLinkMap.get(shareCode);
        if (shareLink == null) {
            throw new RuntimeException("分享链接不存在");
        }

        // 检查是否过期
        if (LocalDateTime.now().isAfter(shareLink.getExpireTime())) {
            throw new RuntimeException("分享链接已过期");
        }

        // 检查密码
        if (shareLink.getHasPassword()) {
            String storedPwd = shareLinkMap.get(shareCode + "_pwd") != null
                    ? shareLinkMap.get(shareCode + "_pwd").toString()
                    : null;
            if (!password.equals(storedPwd)) {
                throw new RuntimeException("密码错误");
            }
        }

        // 更新浏览次数
        Integer viewCount = shareViewCountMap.get(shareCode);
        shareViewCountMap.put(shareCode, viewCount + 1);
        shareLink.setViewCount(viewCount + 1L);

        return shareLink;
    }

    /**
     * 通过分享链接获取文件信息
     */
    public FileInfo getFileByShareCode(String shareCode) {
        ShareLink shareLink = shareLinkMap.get(shareCode);
        if (shareLink == null) {
            throw new RuntimeException("分享链接不存在");
        }

        if (LocalDateTime.now().isAfter(shareLink.getExpireTime())) {
            throw new RuntimeException("分享链接已过期");
        }

        return fileService.getFileInfo(shareLink.getObjectName());
    }

    /**
     * 验证分享链接是否有效
     */
    public boolean validateShareCode(String shareCode) {
        ShareLink shareLink = shareLinkMap.get(shareCode);
        if (shareLink == null) {
            return false;
        }
        return !LocalDateTime.now().isAfter(shareLink.getExpireTime());
    }

    /**
     * 删除分享链接
     */
    public void deleteShareLink(String shareCode) {
        shareLinkMap.remove(shareCode);
        shareViewCountMap.remove(shareCode);
        log.info("Share link deleted: {}", shareCode);
    }

    /**
     * 获取当前用户的所有分享链接
     */
    public java.util.List<ShareLink> getShareLinksByUser(String createBy) {
        return shareLinkMap.values().stream()
                .filter(link -> createBy.equals(link.getCreateBy()))
                .collect(java.util.stream.Collectors.toList());
    }

    private String generateShareCode() {
        // 生成8位随机分享码
        return IdUtil.fastSimpleUUID().substring(0, 8).toUpperCase();
    }
}
