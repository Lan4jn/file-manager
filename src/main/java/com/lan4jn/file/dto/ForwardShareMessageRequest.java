package com.lan4jn.file.dto;

import java.util.List;

public class ForwardShareMessageRequest {

    private List<Long> recipientUserIds;

    public List<Long> getRecipientUserIds() {
        return recipientUserIds;
    }

    public void setRecipientUserIds(List<Long> recipientUserIds) {
        this.recipientUserIds = recipientUserIds;
    }
}
