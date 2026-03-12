package com.poss.file.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_minio_api_key")
public class MinioApiKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;  // 密钥名称

    @Column(nullable = false, unique = true)
    private String accessKey;  // Access Key

    @Column(nullable = false)
    private String secretKey;  // Secret Key

    private LocalDateTime createTime;

    private LocalDateTime expireTime;

    private Boolean isActive;  // 是否启用

    @Transient
    private String bucketsJson;  // JSON格式的桶权限配置

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public String getBucketsJson() {
        return bucketsJson;
    }

    public void setBucketsJson(String bucketsJson) {
        this.bucketsJson = bucketsJson;
    }
}
