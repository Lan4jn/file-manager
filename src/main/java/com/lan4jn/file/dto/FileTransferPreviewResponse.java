package com.lan4jn.file.dto;

import java.util.ArrayList;
import java.util.List;

public class FileTransferPreviewResponse {

    private boolean valid;
    private boolean hasConflicts;
    private String message;
    private String targetFolder;
    private List<Item> items = new ArrayList<>();

    public boolean isValid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean isHasConflicts() {
        return hasConflicts;
    }

    public void setHasConflicts(boolean hasConflicts) {
        this.hasConflicts = hasConflicts;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getTargetFolder() {
        return targetFolder;
    }

    public void setTargetFolder(String targetFolder) {
        this.targetFolder = targetFolder;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items;
    }

    public static class Item {
        private String source;
        private String target;
        private boolean conflict;
        private boolean folder;
        private String message;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getTarget() {
            return target;
        }

        public void setTarget(String target) {
            this.target = target;
        }

        public boolean isConflict() {
            return conflict;
        }

        public void setConflict(boolean conflict) {
            this.conflict = conflict;
        }

        public boolean isFolder() {
            return folder;
        }

        public void setFolder(boolean folder) {
            this.folder = folder;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
