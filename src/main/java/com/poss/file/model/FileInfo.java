package com.poss.file.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FileInfo {
    private String objectName;
    private String fileName;
    private Long size;
    private String contentType;
    private LocalDateTime uploadTime;
    private String url;
    private Boolean isFolder;
    private String etag;
}
