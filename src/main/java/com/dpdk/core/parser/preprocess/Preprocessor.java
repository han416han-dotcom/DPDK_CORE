package com.dpdk.core.parser.preprocess;

import com.dpdk.core.parser.ParseContext;
import com.dpdk.core.parser.ParseStage;
import com.dpdk.core.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 预处理阶段 — 编码检测、格式校验、文本规范化。
 */
@Component
public class Preprocessor implements Parser {

    private static final Logger log = LoggerFactory.getLogger(Preprocessor.class);

    @Override
    public ParseStage getStage() {
        return ParseStage.PREPROCESS;
    }

    @Override
    public String getName() {
        return "Preprocessor";
    }

    @Override
    public boolean parse(ParseContext context) {
        Path logPath = context.getGdbLogPath();
        if (logPath == null || !Files.exists(logPath)) {
            context.addError(getName(), "GDB 日志文件不存在: " + logPath);
            return true; // 不中断, 让后续 Parser 自行处理
        }

        // 1. 检测编码并读取文件
        Charset detectedCharset = detectEncoding(logPath);
        context.addLog("INFO", getName(), "文件编码: " + detectedCharset.name());

        try {
            List<String> lines = Files.readAllLines(logPath, detectedCharset);
            if (lines.isEmpty()) {
                context.addWarning(getName(), "GDB 日志文件为空");
            }

            // 2. 行规范化 (去除 BOM, 去除尾部空白但不丢失空行语义)
            lines = lines.stream()
                    .map(line -> {
                        // 去除 UTF-8 BOM (U+FEFF)
                        if (!line.isEmpty() && line.charAt(0) == '﻿') {
                            return line.substring(1);
                        }
                        return line;
                    })
                    .toList();

            context.setRawLines(lines);
            context.addLog("INFO", getName(), String.format("读取 %d 行", lines.size()));

            // 3. 基本格式校验
            validateFormat(lines, context);

        } catch (IOException e) {
            context.addError(getName(), "读取文件失败: " + e.getMessage());
        }

        return true;
    }

    private Charset detectEncoding(Path path) {
        // 简单启发式: 尝试 UTF-8, 如果失败则用系统默认编码
        try {
            byte[] bytes = Files.readAllBytes(path);
            // 检查 BOM
            if (bytes.length >= 3 && bytes[0] == (byte)0xEF
                    && bytes[1] == (byte)0xBB && bytes[2] == (byte)0xBF) {
                return StandardCharsets.UTF_8;
            }
            // 尝试 UTF-8 解码
            String test = new String(bytes, StandardCharsets.UTF_8);
            if (test.contains("�")) {
                return Charset.defaultCharset(); // 含非法字符, 回退
            }
            return StandardCharsets.UTF_8;
        } catch (IOException e) {
            return StandardCharsets.UTF_8;
        }
    }

    private void validateFormat(List<String> lines, ParseContext context) {
        boolean hasBt = lines.stream().anyMatch(l -> l.contains("#0 ") || l.contains("backtrace"));
        boolean hasSection = lines.stream().anyMatch(l -> l.contains("═══ SECTION:"));

        if (!hasBt && !hasSection) {
            context.addWarning(getName(), "日志中未检测到标准 backtrace 格式, 解析可能不完整");
        }
        if (hasSection && !hasBt) {
            context.addWarning(getName(), "日志包含 SECTION 标记但未检测到 backtrace 内容");
        }
    }
}
