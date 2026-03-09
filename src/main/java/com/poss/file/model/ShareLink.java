package com.poss.file.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ShareLink {
    private String shareCode;
    private String objectName;
    private String fileName;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private String createBy;
    private Long viewCount;
    private Boolean hasPassword;
}
