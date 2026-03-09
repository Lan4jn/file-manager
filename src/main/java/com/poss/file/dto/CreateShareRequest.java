package com.poss.file.dto;

import lombok.Data;

@Data
public class CreateShareRequest {
    private String objectName;
    private Integer expirationHours;
    private String password;
}
