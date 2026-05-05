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

@Component
public class CoreDumpProcessor implements Parser {

    private static final Logger log = LoggerFactory.getLogger(CoreDumpProcessor.class);

    @Value("${app.gdb.cmd:gdb}")
    private String gdbCmd;

    @Value("${app.gdb.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${app.gdb.enabled:true}")
    private boolean gdbEnabled;

    @Value("${app.gdb.script-path:}")
    private String gdbScriptPath;

    private static final byte[] ELF_MAGIC = {(byte) 0x7f, 0x45, 0x4c, 0x46};
    private static final int ET_CORE = 4;
    private static final int EI_CLASS = 4;
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
            return true;
        }

        Path corePath = context.getGdbLogPath();
        Path execPath = context.getExecutablePath();

        if (corePath == null || !Files.exists(corePath)) {
            return true;
        }

        if (!isCoreDump(corePath)) {
            return true;
        }

        long coreSizeMb = 0;
        try {
            coreSizeMb = Files.size(corePath) / (1024 * 1024);
        } catch (IOException ignored) {
        }
        if (coreSizeMb > 500) {
            context.addWarning(getName(),
                    String.format("Core file is %dMB, larger than 500MB; suggest uploading log for analysis", coreSizeMb));
            return true;
        }

        context.addLog("INFO", getName(), "Detected core dump, starting GDB auto analysis");

        if (!isGdbAvailable()) {
            context.addWarning(getName(), "GDB unavailable, cannot analyze core dump");
            return true;
        }

        if (execPath == null || !Files.exists(execPath)) {
            context.addError(getName(), "Missing executable file, cannot analyze core dump");
            return true;
        }

        File scriptFile = findGdbScript();
        if (scriptFile == null) {
            context.addError(getName(), "GDB script not found");
            return true;
        }

        try {
            Path outputPath = runGdb(scriptFile, execPath, corePath, context);
            if (outputPath != null) {
                context.setGdbLogPath(outputPath);
                context.addLog("INFO", getName(),
                        String.format("GDB analysis complete, generated log: %s (%d bytes)",
                                outputPath.getFileName(), Files.size(outputPath)));
            } else {
                context.setGdbLogPath(null);
                context.addError(getName(), "GDB analysis failed");
                return false;
            }
        } catch (Exception e) {
            context.addError(getName(), "GDB execution error: " + e.getMessage());
            context.setGdbLogPath(null);
            log.error("GDB core dump analysis error", e);
            return false;
        }

        return true;
    }

    private boolean isCoreDump(Path path) {
        try {
            byte[] header = new byte[32];
            try (InputStream is = Files.newInputStream(path)) {
                int read = is.read(header);
                if (read < 16) return false;
            }

            for (int i = 0; i < 4; i++) {
                if (header[i] != ELF_MAGIC[i]) return false;
            }

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
            log.debug("Failed to read file header, treat as non-core dump: {}", path, e);
            return false;
        }
    }

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

    private File findGdbScript() {
        if (!gdbScriptPath.isBlank()) {
            File f = new File(gdbScriptPath);
            if (f.exists()) return f;
        }

        var resource = getClass().getClassLoader().getResource("gdb/generate_dpdk_core_log.gdb");
        if (resource != null) {
            if ("file".equals(resource.getProtocol())) {
                File f = new File(resource.getFile());
                if (f.exists()) return f;
            } else {
                try (InputStream is = resource.openStream()) {
                    Path tempFile = Files.createTempFile("dpdk-gdb-script-", ".gdb");
                    Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    tempFile.toFile().deleteOnExit();
                    log.debug("Extracted GDB script from jar: {}", tempFile);
                    return tempFile.toFile();
                } catch (IOException e) {
                    log.warn("Failed to extract GDB script from jar", e);
                }
            }
        }

        File f = new File("gdb/generate_dpdk_core_log.gdb");
        if (f.exists()) return f;

        f = new File("../gdb/generate_dpdk_core_log.gdb");
        if (f.exists()) return f;

        return null;
    }

    private Path runGdb(File scriptFile, Path execPath, Path corePath, ParseContext context) throws Exception {
        Path outputPath = Files.createTempFile("dpdk-core-", ".log");
        outputPath.toFile().deleteOnExit();

        String[] cmd = {
                gdbCmd, "-batch",
                "-x", scriptFile.getAbsolutePath(),
                execPath.toAbsolutePath().toString(),
                corePath.toAbsolutePath().toString()
        };

        log.info("Executing GDB: {} {} ...", gdbCmd, execPath.getFileName());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectOutput(outputPath.toFile());
        pb.redirectErrorStream(true);

        long start = System.currentTimeMillis();
        Process process = pb.start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            log.warn("GDB timed out ({}s), terminated forcibly", timeoutSeconds);
            context.addWarning(getName(),
                    String.format("GDB timed out (%ds), using partial output", timeoutSeconds));
        }

        int exitCode = process.exitValue();
        long outputSize = Files.size(outputPath);

        if (outputSize > 0) {
            String content = Files.readString(outputPath);
            if (content.contains("DPDK_CORE_ANALYZER_EOF")) {
                context.addLog("INFO", getName(),
                        String.format("GDB finished (exit=%d), elapsed %dms, output %d bytes",
                                exitCode, elapsed, outputSize));
                return outputPath;
            } else {
                context.addWarning(getName(), "GDB output missing EOF marker, may be incomplete");
            }
        }

        Files.deleteIfExists(outputPath);
        context.addError(getName(),
                String.format("GDB output invalid (exit=%d, size=%d, elapsed=%dms)",
                        exitCode, outputSize, elapsed));
        return null;
    }
}
