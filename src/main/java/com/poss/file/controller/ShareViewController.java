package com.poss.file.controller;

import com.poss.file.model.ShareLink;
import com.poss.file.service.ShareService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class ShareViewController {

    private static final Logger log = LoggerFactory.getLogger(ShareViewController.class);

    private final ShareService shareService;

    public ShareViewController(ShareService shareService) {
        this.shareService = shareService;
    }

    @GetMapping("/share/{shareCode}")
    public RedirectView viewShare(@PathVariable String shareCode) {
        try {
            ShareLink shareLink = shareService.getShareInfo(shareCode, null);
            String downloadUrl;

            // 判断是文件分享还是目录/批量分享
            // 批量分享的objectName以"batch:"开头
            if (Boolean.TRUE.equals(shareLink.getIsFolder()) || (shareLink.getObjectName() != null && shareLink.getObjectName().startsWith("batch:"))) {
                downloadUrl = "/api/share/download-folder?shareCode=" + shareCode;
            } else {
                downloadUrl = "/api/share/download?shareCode=" + shareCode;
            }

            log.info("Redirecting share {} to {}", shareCode, downloadUrl);
            return new RedirectView(downloadUrl);
        } catch (Exception e) {
            log.error("Failed to process share {}: {}", shareCode, e.getMessage());
            // Return to home page on error
            return new RedirectView("/");
        }
    }
}
