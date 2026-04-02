package com.lan4jn.file.service;

import com.lan4jn.file.model.FileInfo;
import com.lan4jn.file.model.ShareLink;

import java.util.Map;

public class ShareArchivePlan {

    private final ShareLink shareLink;
    private final Map<String, FileInfo> entries;

    public ShareArchivePlan(ShareLink shareLink, Map<String, FileInfo> entries) {
        this.shareLink = shareLink;
        this.entries = entries;
    }

    public ShareLink getShareLink() {
        return shareLink;
    }

    public Map<String, FileInfo> getEntries() {
        return entries;
    }
}
