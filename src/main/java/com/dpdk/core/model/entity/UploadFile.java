package com.dpdk.core.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;

@TableName("upload_files")
public class UploadFile {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String fileName;
    private String fileType;   // GDB_LOG / EXECUTABLE
    private Long fileSize;
    private String fileHash;
    private String storagePath;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime uploadTime;

    private String status;     // UPLOADED / PARSING / PARSED / FAILED

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public LocalDateTime getUploadTime() { return uploadTime; }
    public void setUploadTime(LocalDateTime uploadTime) { this.uploadTime = uploadTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
