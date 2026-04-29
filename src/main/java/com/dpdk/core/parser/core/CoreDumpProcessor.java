package com.dpdk.core.parser.core;

import com.dpdk.core.parser.ParseContext;
import com.dpdk.core.parser.ParseStage;
import com.dpdk.core.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Core dump 自动解析阶段。
 * <p>
 * 检测上传文件是否为 ELF core dump, 若是则自动调用 GDB 生成标准化日志,
 * 更新上下文中的日志路径指向生成结果, 后续管道阶段透明消费。
 * <p>
 * 仅在 Linux + GDB 可用时生效, Windows 开发环境跳过。
 */
@Component
public class CoreDumpProcessor implements Parser {

    private static final Logger log = LoggerFactory.getLogger(CoreDumpProcessor.class);

    @Value("${app.gdb.cmd:gdb}")
    private String gdbCmd;

    @Value("${app.gdb.timeout-seconds:120}")
    private int timeoutSeconds;

    @Value("${app.gdb.enabled:true}")
    private boolean gdbEnabled;

    @Value("${app.gdb.script-path:}")
    private String gdbScriptPath;

    /** ELF magic: \x7f E L F */
    private static final byte[] ELF_MAGIC = {(byte) 0x7f, 0x45, 0x4c, 0x46};

    /** ELF type offset for ET_CORE (4) */
    private static final int ET_CORE = 4;
    private static final int EI_CLASS = 4;   // 32/64bit offset in ident
    private static final int ELFCLASS64 = 2;
    private static final int ET_CORE_OFFSET_32 = 16;
    private static final int ET_CORE_OFFSET_64 = 16;

    @Override
    public ParseStage getStage() {
        return ParseStage.CORE_PARSE;
    }

    @Override
    public String getName() {
        return "CoreDumpProcessor";
    }

    @Override
    public boolean parse(ParseContext context) {
        if (!gdbEnabled) {
            return true; // GDB 功能未启用 (如 Windows 开发环境)
        }

        Path logPath = context.getGdbLogPath();
        Path execPath = context.getExecutablePath();

        if (logPath == null || !Files.exists(logPath)) {
            return true;
        }

        // 1. 检测是否为 ELF core dump
        if (!isCoreDump(logPath)) {
            return true; // 不是 core dump, 跳过
        }

        context.addLog("INFO", getName(), "检测到 core dump 文件, 启动 GDB 自动解析");

        // 2. 检查 GDB 是否可用
        if (!isGdbAvailable()) {
            context.addWarning(getName(), "GDB 不可用, 无法解析 core dump (仅 Linux 环境支持)");
            return true;
        }

        // 3. 校验可执行文件
        if (execPath == null || !Files.exists(execPath)) {
            context.addError(getName(), "缺少可执行文件, 无法解析 core dump");
            return true;
        }

        // 4. 查找 GDB 脚本
        File scriptFile = findGdbScript();
        if (scriptFile == null) {
            context.addError(getName(), "GDB 脚本 (generate_dpdk_core_log.gdb) 未找到");
            return true;
        }

        // 5. 执行 GDB
        try {
            Path outputPath = runGdb(scriptFile, execPath, logPath, context);
            if (outputPath != null) {
                // 更新上下文, 后续阶段使用生成的日志
                context.setGdbLogPath(outputPath);
                context.addLog("INFO", getName(),
                        String.format("GDB 解析完成, 生成日志: %s (%d bytes)",
                                outputPath.getFileName(), Files.size(outputPath)));
            } else {
                // GDB 失败, 清空日志路径防止下游读取二进制 core dump
                context.setGdbLogPath(null);
                context.addError(getName(), "GDB 解析失败, 无法继续");
                return false;
            }
        } catch (Exception e) {
            context.addError(getName(), "GDB 执行异常: " + e.getMessage());
            context.setGdbLogPath(null);
            log.error("GDB core dump 解析异常", e);
            return false;
        }

        return true;
    }

    /**
     * 检测文件是否为 ELF core dump
     */
    private boolean isCoreDump(Path path) {
        try {
            byte[] header = new byte[32];
            try (InputStream is = Files.newInputStream(path)) {
                int read = is.read(header);
                if (read < 16) return false;
            }

            // 检查 ELF magic
            for (int i = 0; i < 4; i++) {
                if (header[i] != ELF_MAGIC[i]) return false;
            }

            // 读取 e_type (offset 16)
            int eType;
            if (header[EI_CLASS] == ELFCLASS64) {
                eType = (header[ET_CORE_OFFSET_64] & 0xff)
                        | ((header[ET_CORE_OFFSET_64 + 1] & 0xff) << 8);
            } else {
                eType = (header[ET_CORE_OFFSET_32] & 0xff)
                        | ((header[ET_CORE_OFFSET_32 + 1] & 0xff) << 8);
            }

            return eType == ET_CORE;

        } catch (IOException e) {
            log.debug("读取文件头失败, 视为非 core dump: {}", path, e);
            return false;
        }
    }

    /**
     * 检查 GDB 命令是否可用
     */
    private boolean isGdbAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(gdbCmd, "--version");
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 查找 GDB 脚本文件。
     * 支持从 classpath (jar 包内) 提取到临时文件。
     */
    private File findGdbScript() {
        // 1. 从配置路径查找
        if (!gdbScriptPath.isBlank()) {
            File f = new File(gdbScriptPath);
            if (f.exists()) return f;
        }

        // 2. 从 classpath 查找 (打包后 jar 内)
        var resource = getClass().getClassLoader().getResource("gdb/generate_dpdk_core_log.gdb");
        if (resource != null) {
            if ("file".equals(resource.getProtocol())) {
                // IDE 开发环境: 直接文件路径
                File f = new File(resource.getFile());
                if (f.exists()) return f;
            } else {
                // jar 包: 提取到临时文件
                try (InputStream is = resource.openStream()) {
                    Path tempFile = Files.createTempFile("dpdk-gdb-script-", ".gdb");
                    Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    tempFile.toFile().deleteOnExit();
                    log.debug("从 jar 提取 GDB 脚本: {}", tempFile);
                    return tempFile.toFile();
                } catch (IOException e) {
                    log.warn("从 jar 提取 GDB 脚本失败", e);
                }
            }
        }

        // 3. 从项目目录查找
        File f = new File("gdb/generate_dpdk_core_log.gdb");
        if (f.exists()) return f;

        // 4. 从上级目录查找
        f = new File("../gdb/generate_dpdk_core_log.gdb");
        if (f.exists()) return f;

        return null;
    }

    /**
     * 执行 GDB 命令生成日志
     *
     * @param scriptFile GDB 脚本
     * @param execPath   可执行文件路径
     * @param corePath   core dump 路径
     * @return 生成的日志文件路径, 失败返回 null
     */
    private Path runGdb(File scriptFile, Path execPath, Path corePath, ParseContext context) throws Exception {
        Path outputPath = Files.createTempFile("dpdk-core-", ".log");
        outputPath.toFile().deleteOnExit();

        String[] cmd = {
                gdbCmd, "-batch",
                "-x", scriptFile.getAbsolutePath(),
                execPath.toAbsolutePath().toString(),
                corePath.toAbsolutePath().toString()
        };

        log.info("执行 GDB: {} {} ...", gdbCmd, execPath.getFileName());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectOutput(outputPath.toFile());
        pb.redirectErrorStream(true);

        long start = System.currentTimeMillis();
        Process process = pb.start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS); // 等待强制终止完成
            log.warn("GDB 执行超时 ({}s), 强制终止", timeoutSeconds);
            context.addWarning(getName(),
                    String.format("GDB 执行超时 (%ds), 尝试使用部分输出", timeoutSeconds));
        }

        int exitCode = process.exitValue();
        long outputSize = Files.size(outputPath);

        // 即使非零退出码也可能有部分输出, 检查输出文件是否有效
        if (outputSize > 0) {
            // 验证输出是否为有效日志格式
            String content = Files.readString(outputPath);
            if (content.contains("DPDK_CORE_ANALYZER_EOF")) {
                context.addLog("INFO", getName(),
                        String.format("GDB 完成 (exit=%d), 耗时 %dms, 输出 %d bytes",
                                exitCode, elapsed, outputSize));
                return outputPath;
            } else {
                context.addWarning(getName(),
                        "GDB 输出缺少结束标记, 可能不完整");
            }
        }

        // 输出文件无效: 先记录 size 再删除（防止 Files.size 在 delete 后抛异常）
        Files.deleteIfExists(outputPath);
        context.addError(getName(),
                String.format("GDB 输出无效 (exit=%d, size=%d, elapsed=%dms)",
                        exitCode, outputSize, elapsed));
        return null;
    }

}
