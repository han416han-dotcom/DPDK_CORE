package com.dpdk.core.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dpdk.core.model.entity.UploadFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorageService {

    UploadFile storeFile(MultipartFile file, String fileType);

    UploadFile registerLocalFile(Path path, String fileType);

    UploadFile getFileById(Long id);

    Page<UploadFile> listFiles(int page, int size);

    String computeHash(byte[] data);
}
