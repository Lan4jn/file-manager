package com.lan4jn.file.service;

import com.lan4jn.file.common.PageResult;
import com.lan4jn.file.dto.CreateShareRequest;
import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.model.ShareAccessInfo;
import com.lan4jn.file.model.ShareLink;
import com.lan4jn.file.repository.ShareFileRecordRepository;
import com.lan4jn.file.repository.ShareLinkRepository;
import com.lan4jn.file.repository.UserRepository;
import com.lan4jn.file.repository.UserShareMessageRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.util.List;

@Service
public class ShareService {

    private final ShareLinkService shareLinkService;
    private final ShareAccessService shareAccessService;

    public ShareService(FileService fileService,
                        ShareLinkRepository shareLinkRepository,
                        ShareFileRecordRepository shareFileRecordRepository,
                        UserRepository userRepository,
                        UserShareMessageRepository userShareMessageRepository,
                        ConfigService configService,
                        @Value("${share.default-expiration: 24}") Integer defaultExpiration) {
        this(fileService, shareLinkRepository, shareFileRecordRepository,
                userRepository, userShareMessageRepository, configService, defaultExpiration, null);
    }

    @Autowired
    public ShareService(FileService fileService,
                        ShareLinkRepository shareLinkRepository,
                        ShareFileRecordRepository shareFileRecordRepository,
                        UserRepository userRepository,
                        UserShareMessageRepository userShareMessageRepository,
                        ConfigService configService,
                        @Value("${share.default-expiration: 24}") Integer defaultExpiration,
                        MessagePushService messagePushService) {
        ShareSupport shareSupport = new ShareSupport();
        this.shareLinkService = new ShareLinkService(
                fileService, shareLinkRepository, shareFileRecordRepository,
                userRepository, userShareMessageRepository, shareSupport, defaultExpiration, messagePushService);
        this.shareAccessService = new ShareAccessService(
                fileService, configService, shareLinkService, shareSupport);
    }

    @Transactional
    public ShareLink createShareLink(CreateShareRequest request, String createBy) {
        return shareLinkService.createShareLink(request, createBy);
    }

    @Transactional
    public ShareLink createShareLink(CreateShareRequest request,
                                     String createBy,
                                     UserShareMessageDispatchContext messageDispatchContext) {
        return shareLinkService.createShareLink(request, createBy, messageDispatchContext);
    }

    public ShareLink createBatchShareLink(List<String> objectNames, String bucket, String createBy,
                                          Integer expirationHours, String password,
                                          String targetType, List<Long> recipientUserIds) {
        return shareLinkService.createBatchShareLink(
                objectNames, bucket, createBy, expirationHours, password, targetType, recipientUserIds);
    }

    public ShareLink createBatchShareLink(List<String> objectNames, String bucket, String createBy,
                                          Integer expirationHours, String password,
                                          String targetType, List<Long> recipientUserIds,
                                          UserShareMessageDispatchContext messageDispatchContext) {
        return shareLinkService.createBatchShareLink(
                objectNames, bucket, createBy, expirationHours, password, targetType, recipientUserIds,
                messageDispatchContext);
    }

    public ShareAccessInfo getShareAccessInfo(String shareCode) {
        return shareLinkService.getShareAccessInfo(shareCode);
    }

    public ShareLink getShareInfo(String shareCode, String password) {
        return shareLinkService.getShareInfo(shareCode, password);
    }

    public ShareLink peekShareInfo(String shareCode, String password) {
        return shareLinkService.peekShareInfo(shareCode, password);
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

    public ShareArchivePlan prepareShareArchive(String shareCode, String password) {
        return shareAccessService.prepareShareArchive(shareCode, password);
    }

    public void downloadPreparedShareAsZip(ShareArchivePlan plan, OutputStream outputStream) {
        shareAccessService.downloadShareAsZip(plan, outputStream);
    }

    public String getShareArchiveName(String shareCode, String password) {
        return shareAccessService.getShareArchiveName(shareCode, password);
    }

    public void incrementViewCount(String shareCode) {
        shareLinkService.incrementViewCount(shareCode);
    }

    public boolean validateShareCode(String shareCode) {
        return shareLinkService.validateShareCode(shareCode);
    }

    public void deleteShareLink(String shareCode, String createBy) {
        shareLinkService.deleteShareLink(shareCode, createBy);
    }

    public void cancelShareLink(String shareCode, String createBy) {
        shareLinkService.cancelShareLink(shareCode, createBy);
    }

    public List<ShareLink> getShareLinksByUser(String createBy) {
        return shareLinkService.getShareLinksByUser(createBy);
    }

    public PageResult<ShareLink> getShareLinksByUser(String createBy, int page, int size, boolean validOnly) {
        return shareLinkService.getShareLinksByUser(createBy, page, size, validOnly);
    }

    public void cleanupExpiredShares() {
        shareLinkService.cleanupExpiredShares();
    }

    public static class UserShareMessageDispatchContext {
        private String messageType;
        private String body;
        private String originSenderUsername;
        private String forwardedByUsername;
        private String relatedShareCode;
        private String conversationKey;

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }

        public String getBody() {
            return body;
        }

        public void setBody(String body) {
            this.body = body;
        }

        public String getOriginSenderUsername() {
            return originSenderUsername;
        }

        public void setOriginSenderUsername(String originSenderUsername) {
            this.originSenderUsername = originSenderUsername;
        }

        public String getForwardedByUsername() {
            return forwardedByUsername;
        }

        public void setForwardedByUsername(String forwardedByUsername) {
            this.forwardedByUsername = forwardedByUsername;
        }

        public String getRelatedShareCode() {
            return relatedShareCode;
        }

        public void setRelatedShareCode(String relatedShareCode) {
            this.relatedShareCode = relatedShareCode;
        }

        public String getConversationKey() {
            return conversationKey;
        }

        public void setConversationKey(String conversationKey) {
            this.conversationKey = conversationKey;
        }
    }
}
