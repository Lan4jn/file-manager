package com.poss.file.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "sys_share_file_record")
public class ShareFileRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String shareCode;  // 关联的分享码

    @Column(nullable = false)
    private String fileName;  // 文件名（相对路径）

    @Column(nullable = false)
    private String md5;  // 文件MD5码

    private Long fileSize;  // 文件大小

    private LocalDateTime recordTime;  // 记录时间

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
