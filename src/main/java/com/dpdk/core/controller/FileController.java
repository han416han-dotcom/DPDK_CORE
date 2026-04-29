package com.dpdk.core.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dpdk.core.model.entity.UploadFile;
import com.dpdk.core.service.FileStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传接口
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * 上传文件
     * @param file     上传文件
     * @param fileType GDB_LOG 或 EXECUTABLE
     */
    @PostMapping("/upload")
    public ResponseEntity<UploadFile> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("fileType") String fileType) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (!"GDB_LOG".equals(fileType) && !"EXECUTABLE".equals(fileType) && !"CORE_DUMP".equals(fileType)) {
            return ResponseEntity.badRequest().build();
        }

        UploadFile saved = fileStorageService.storeFile(file, fileType);
        return ResponseEntity.ok(saved);
    }

    /**
     * 获取文件列表
     */
    @GetMapping("/list")
    public ResponseEntity<Page<UploadFile>> listFiles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(fileStorageService.listFiles(page, size));
    }

    /**
     * 获取单个文件
     */
    @GetMapping("/{id}")
    public ResponseEntity<UploadFile> getFile(@PathVariable Long id) {
        UploadFile file = fileStorageService.getFileById(id);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(file);
    }
}
