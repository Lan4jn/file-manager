package com.poss.file.dto;

import lombok.Data;

@Data
public class UploadResponse {
    private String objectName;
    private String fileName;
    private Long size;
    private String contentType;
    private String url;
    private String etag;
}
