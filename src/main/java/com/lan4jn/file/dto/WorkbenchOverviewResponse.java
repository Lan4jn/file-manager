package com.lan4jn.file.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class WorkbenchOverviewResponse {

    private boolean defaultWorkbench;
    private List<DirectoryItem> recentDirectories = new ArrayList<>();
    private List<DirectoryItem> favoriteDirectories = new ArrayList<>();
    private List<ShareReminderItem> shareReminders = new ArrayList<>();

    public boolean isDefaultWorkbench() {
        return defaultWorkbench;
    }

    public void setDefaultWorkbench(boolean defaultWorkbench) {
        this.defaultWorkbench = defaultWorkbench;
    }

    public List<DirectoryItem> getRecentDirectories() {
        return recentDirectories;
    }

    public void setRecentDirectories(List<DirectoryItem> recentDirectories) {
        this.recentDirectories = recentDirectories;
    }

    public List<DirectoryItem> getFavoriteDirectories() {
        return favoriteDirectories;
    }

    public void setFavoriteDirectories(List<DirectoryItem> favoriteDirectories) {
        this.favoriteDirectories = favoriteDirectories;
    }

    public List<ShareReminderItem> getShareReminders() {
        return shareReminders;
    }

    public void setShareReminders(List<ShareReminderItem> shareReminders) {
        this.shareReminders = shareReminders;
    }

    public static class DirectoryItem {
        private String bucketName;
        private String directoryPath;
        private String directoryName;
        private LocalDateTime time;

        public String getBucketName() {
            return bucketName;
        }

        public void setBucketName(String bucketName) {
            this.bucketName = bucketName;
        }

        public String getDirectoryPath() {
            return directoryPath;
        }

        public void setDirectoryPath(String directoryPath) {
            this.directoryPath = directoryPath;
        }

        public String getDirectoryName() {
            return directoryName;
        }

        public void setDirectoryName(String directoryName) {
            this.directoryName = directoryName;
        }

        public LocalDateTime getTime() {
            return time;
        }

        public void setTime(LocalDateTime time) {
            this.time = time;
        }
    }

    public static class ShareReminderItem {
        private String title;
        private String content;
        private LocalDateTime createTime;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }
    }
}
