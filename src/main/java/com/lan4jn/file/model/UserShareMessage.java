package com.lan4jn.file.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "sys_user_share_message")
public class UserShareMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long recipientUserId;
    private String senderUsername;
    private String shareCode;
    private String messageType;
    @Lob
    private String body;
    private String originSenderUsername;
    private String forwardedByUsername;
    private String relatedShareCode;
    private String conversationKey;
    private String title;
    private Boolean read;
    private LocalDateTime readTime;
    private LocalDateTime createTime;
    private Boolean deleted;

    @Transient
    private String bucket;

    @Transient
    private String objectName;

    @Transient
    private String fileName;

    @Transient
    private Boolean isFolder;

    @Transient
    private LocalDateTime expireTime;

    @Transient
    private Boolean cancelled;

    @Transient
    private List<FileInfo> shareItems;

    @Transient
    private String shareItemsError;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(Long recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public String getShareCode() {
        return shareCode;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public void setSenderUsername(String senderUsername) {
        this.senderUsername = senderUsername;
    }

    public void setShareCode(String shareCode) {
        this.shareCode = shareCode;
    }

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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean getRead() {
        return read;
    }

    public void setRead(Boolean read) {
        this.read = read;
    }

    public LocalDateTime getReadTime() {
        return readTime;
    }

    public void setReadTime(LocalDateTime readTime) {
        this.readTime = readTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Boolean getIsFolder() {
        return isFolder;
    }

    public void setIsFolder(Boolean folder) {
        isFolder = folder;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public Boolean getCancelled() {
        return cancelled;
    }

    public void setCancelled(Boolean cancelled) {
        this.cancelled = cancelled;
    }

    public List<FileInfo> getShareItems() {
        return shareItems;
    }

    public void setShareItems(List<FileInfo> shareItems) {
        this.shareItems = shareItems;
    }

    public String getShareItemsError() {
        return shareItemsError;
    }

    public void setShareItemsError(String shareItemsError) {
        this.shareItemsError = shareItemsError;
    }
}
