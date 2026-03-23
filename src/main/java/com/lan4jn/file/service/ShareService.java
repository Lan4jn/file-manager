package com.lan4jn.file.service;

import com.lan4jn.file.dto.CreateShareRequest;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.model.ShareAccessInfo;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.repository.ShareFileRecordRepository;
import com.lan4jn.file.repository.ShareLinkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.util.List;

@Service
public class ShareService {

    private final ShareLinkService shareLinkService;
    private final ShareAccessService shareAccessService;

    public ShareService(FileService fileService,
                        ShareLinkRepository shareLinkRepository,
                        ShareFileRecordRepository shareFileRecordRepository,
                        ConfigService configService,
                        @Value("${share.default-expiration: 24}") Integer defaultExpiration) {
        ShareSupport shareSupport = new ShareSupport();
        this.shareLinkService = new ShareLinkService(
                fileService, shareLinkRepository, shareFileRecordRepository, shareSupport, defaultExpiration);
        this.shareAccessService = new ShareAccessService(
                fileService, configService, shareLinkService, shareSupport);
    }

    public ShareLink createShareLink(CreateShareRequest request, String createBy) {
        return shareLinkService.createShareLink(request, createBy);
    }

    public ShareLink createBatchShareLink(List<String> objectNames, String bucket, String createBy,
                                          Integer expirationHours, String password) {
        return shareLinkService.createBatchShareLink(objectNames, bucket, createBy, expirationHours, password);
    }

    public ShareAccessInfo getShareAccessInfo(String shareCode) {
        return shareLinkService.getShareAccessInfo(shareCode);
    }

    public ShareLink getShareInfo(String shareCode, String password) {
        return shareLinkService.getShareInfo(shareCode, password);
    }

    public FileInfo getFileByShareCode(String shareCode, String password) {
        return shareAccessService.getFileByShareCode(shareCode, password);
    }

    public List<FileInfo> listSharedItems(String shareCode, String password, String path) {
        return shareAccessService.listSharedItems(shareCode, password, path);
    }

    public FileInfo getSharedItemInfo(String shareCode, String password, String objectName) {
        return shareAccessService.getSharedItemInfo(shareCode, password, objectName);
    }

    public void downloadShareAsZip(String shareCode, String password, OutputStream outputStream) {
        shareAccessService.downloadShareAsZip(shareCode, password, outputStream);
    }

    public String getShareArchiveName(String shareCode, String password) {
        return shareAccessService.getShareArchiveName(shareCode, password);
    }

    public boolean validateShareCode(String shareCode) {
        return shareLinkService.validateShareCode(shareCode);
    }

    public void deleteShareLink(String shareCode, String createBy) {
        shareLinkService.deleteShareLink(shareCode, createBy);
    }

    public List<ShareLink> getShareLinksByUser(String createBy) {
        return shareLinkService.getShareLinksByUser(createBy);
    }

    public void cleanupExpiredShares() {
        shareLinkService.cleanupExpiredShares();
    }
}
