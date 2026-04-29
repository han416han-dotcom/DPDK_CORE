package com.dpdk.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dpdk.core.mapper.UploadFileMapper;
import com.dpdk.core.model.entity.UploadFile;
import com.dpdk.core.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageServiceImpl.class);

    private final UploadFileMapper uploadFileMapper;

    @Value("${app.upload.dir:./data/uploads}")
    private String uploadDir;

    public FileStorageServiceImpl(UploadFileMapper uploadFileMapper) {
        this.uploadFileMapper = uploadFileMapper;
    }

    @Override
    public UploadFile storeFile(MultipartFile file, String fileType) {
        Path dir = Path.of(uploadDir);
        try {
            Files.createDirectories(dir);
            byte[] content = file.getBytes();
            String hash = computeHash(content);

            UploadFile existing = findExisting(hash, fileType);
            if (existing != null && Files.exists(Path.of(existing.getStoragePath()))) {
                log.info("File already exists by hash, reuse existing row: id={}, hash={}", existing.getId(), hash);
                return existing;
            }

            String originalName = file.getOriginalFilename() != null
                    ? file.getOriginalFilename()
                    : "uploaded.bin";
            Path targetPath = dir.resolve(System.currentTimeMillis() + "_" + originalName);
            Files.write(targetPath, content);

            if (existing != null) {
                log.warn("Hash matched but physical file was missing, update existing row: id={}", existing.getId());
                existing.setFileName(originalName);
                existing.setFileSize((long) content.length);
                existing.setStoragePath(targetPath.toAbsolutePath().toString());
                existing.setUploadTime(LocalDateTime.now());
                existing.setStatus("UPLOADED");
                uploadFileMapper.updateById(existing);
                return existing;
            }

            UploadFile entity = new UploadFile();
            entity.setFileName(originalName);
            entity.setFileType(fileType);
            entity.setFileSize((long) content.length);
            entity.setFileHash(hash);
            entity.setStoragePath(targetPath.toAbsolutePath().toString());
            entity.setUploadTime(LocalDateTime.now());
            entity.setStatus("UPLOADED");
            uploadFileMapper.insert(entity);

            log.info("Stored uploaded file: id={}, name={}, type={}, size={}",
                    entity.getId(), entity.getFileName(), fileType, content.length);
            return entity;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store uploaded file: " + e.getMessage(), e);
        }
    }

    @Override
    public UploadFile registerLocalFile(Path path, String fileType) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.exists(normalized) || !Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("Local file does not exist: " + normalized);
        }

        try {
            String hash = computeHash(normalized);
            long size = Files.size(normalized);

            UploadFile existing = findExisting(hash, fileType);
            if (existing != null) {
                Path existingPath = Path.of(existing.getStoragePath());
                if (Files.exists(existingPath)) {
                    log.info("Local file already registered by hash: id={}, path={}", existing.getId(), normalized);
                    return existing;
                }
                log.warn("Existing DB row matched by hash but original path is gone, redirect to new path: id={}, path={}",
                        existing.getId(), normalized);
                existing.setFileName(normalized.getFileName().toString());
                existing.setFileSize(size);
                existing.setStoragePath(normalized.toString());
                existing.setUploadTime(LocalDateTime.now());
                existing.setStatus("UPLOADED");
                uploadFileMapper.updateById(existing);
                return existing;
            }

            UploadFile entity = new UploadFile();
            entity.setFileName(normalized.getFileName().toString());
            entity.setFileType(fileType);
            entity.setFileSize(size);
            entity.setFileHash(hash);
            entity.setStoragePath(normalized.toString());
            entity.setUploadTime(LocalDateTime.now());
            entity.setStatus("UPLOADED");
            uploadFileMapper.insert(entity);

            log.info("Registered local file: id={}, path={}, type={}",
                    entity.getId(), normalized, fileType);
            return entity;
        } catch (IOException e) {
            throw new RuntimeException("Failed to register local file: " + e.getMessage(), e);
        }
    }

    @Override
    public UploadFile getFileById(Long id) {
        return uploadFileMapper.selectById(id);
    }

    @Override
    public Page<UploadFile> listFiles(int page, int size) {
        return uploadFileMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<UploadFile>().orderByDesc(UploadFile::getUploadTime)
        );
    }

    @Override
    public String computeHash(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(data);
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 is not available", e);
        }
    }

    private String computeHash(Path path) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 is not available", e);
        }
    }

    private UploadFile findExisting(String hash, String fileType) {
        return uploadFileMapper.selectOne(
                new LambdaQueryWrapper<UploadFile>()
                        .eq(UploadFile::getFileHash, hash)
                        .eq(UploadFile::getFileType, fileType)
                        .last("LIMIT 1")
        );
    }
}
