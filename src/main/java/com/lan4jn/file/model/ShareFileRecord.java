package com.lan4jn.file.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_share_file_record")
public class ShareFileRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String shareCode;  // 鍏宠仈鐨勫垎浜爜

    @Column(nullable = false)
    private String fileName;  // 鏂囦欢鍚嶏紙鐩稿璺緞锛?

    @Column(nullable = true)
    private String objectName;  // MinIO瀵硅薄鍚?

    @Column(nullable = false)
    private String md5;  // 鏂囦欢MD5鐮?

    private Long fileSize;  // 鏂囦欢澶у皬

    private LocalDateTime recordTime;  // 璁板綍鏃堕棿

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getShareCode() {
        return shareCode;
    }

    public void setShareCode(String shareCode) {
        this.shareCode = shareCode;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public LocalDateTime getRecordTime() {
        return recordTime;
    }

    public void setRecordTime(LocalDateTime recordTime) {
        this.recordTime = recordTime;
    }
}

