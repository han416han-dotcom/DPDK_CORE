package com.dpdk.core.parser.python;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Python 脚本执行器。
 * 通过 ProcessBuilder 调用 Python 脚本，通过 stdin/stdout 传递 JSON 数据。
 */
@Component
public class PythonExecutor {

    private static final Logger log = LoggerFactory.getLogger(PythonExecutor.class);

    @Value("${app.python.cmd:python3}")
    private String pythonCmd;

    @Value("${app.python.scripts-dir:scripts}")
    private String scriptsDir;

    /**
     * 解析脚本文件路径。
     * 支持从文件系统、classpath (jar 包内) 中查找并提取到临时文件。
     */
    private File resolveScript(String scriptName) {
        // 1. 从文件系统查找 (开发环境 / 独立部署)
        File scriptFile = new File(scriptsDir, scriptName);
        if (scriptFile.exists()) return scriptFile;

        scriptFile = new File("scripts/" + scriptName);
        if (scriptFile.exists()) return scriptFile;

        scriptFile = new File("../" + scriptsDir, scriptName);
        if (scriptFile.exists()) return scriptFile;

        // 2. 从 classpath (jar 包内) 提取到临时文件
        try {
            var resource = getClass().getClassLoader().getResource("scripts/" + scriptName);
            if (resource != null) {
                if ("file".equals(resource.getProtocol())) {
                    return new File(resource.getFile());
                }
                // jar 包: 复制到临时文件
                try (InputStream is = resource.openStream()) {
                    Path tempFile = Files.createTempFile("dpdk-py-", ".py");
                    Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    tempFile.toFile().deleteOnExit();
                    log.debug("从 jar 提取 Python 脚本: {}", scriptName);
                    return tempFile.toFile();
                }
            }
        } catch (IOException e) {
            log.warn("从 classpath 提取脚本失败: {}", scriptName, e);
        }

        log.error("Python 脚本未找到: {} (已搜索文件系统和 classpath)", scriptName);
        return null;
    }

    /**
     * 调用 Python 脚本并返回 JSON 结果
     *
     * @param scriptName 脚本文件名 (如 parse_gdb_log.py)
     * @param jsonInput  传递给脚本的 JSON 字符串
     * @return 脚本输出的 JSON 字符串
     */
    public String execute(String scriptName, String jsonInput) {
        File scriptFile = resolveScript(scriptName);
        if (scriptFile == null) {
            return "{\"status\":\"error\",\"error\":\"脚本未找到: " + scriptName + "\"}";
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(pythonCmd, scriptFile.getAbsolutePath());
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // 发送 JSON 到 stdin
            try (OutputStream os = process.getOutputStream()) {
                os.write(jsonInput.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            // 并发读取 stderr (防止管道缓冲区满导致子进程死锁)
            StringBuilder stderrBuf = new StringBuilder();
            Thread stderrReader = new Thread(() -> {
                try (InputStream es = process.getErrorStream()) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = es.read(buf)) != -1) {
                        stderrBuf.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                    }
                } catch (IOException e) {
                    // ignore stream close
                }
            });
            stderrReader.start();

            // 读取 stdout
            String stdout;
            try (InputStream is = process.getInputStream()) {
                stdout = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            // 等待 stderr 读取完毕
            try {
                stderrReader.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            String stderr = stderrBuf.toString();

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Python 脚本超时: {}", scriptName);
                return "{\"status\":\"error\",\"error\":\"脚本执行超时(30s): " + scriptName + "\"}";
            }

            int exitCode = process.exitValue();
            if (exitCode != 0 && !stdout.contains("\"status\":\"ok\"")) {
                log.warn("Python 脚本退出码异常: {} ({}), stderr={}", scriptName, exitCode, stderr);
                if (stdout.isBlank()) {
                    return "{\"status\":\"error\",\"error\":\"脚本异常(exit=" + exitCode + "): " +
                            stderr.replace("\"", "'") + "\"}";
                }
            }

            if (!stderr.isBlank()) {
                log.debug("Python stderr [{}]: {}", scriptName, stderr.trim());
            }

            return stdout.isBlank() ? "{\"status\":\"error\",\"error\":\"脚本无输出\"}" : stdout;

        } catch (IOException e) {
            log.error("执行 Python 脚本失败: {}", scriptName, e);
            return "{\"status\":\"error\",\"error\":\"调用脚本异常: " + e.getMessage() + "\"}";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "{\"status\":\"error\",\"error\":\"执行被中断\"}";
        }
    }

    /**
     * 简化调用: 脚本参数模式
     */
    public String executeWithArgs(String scriptName, String... args) {
        File scriptFile = resolveScript(scriptName);
        if (scriptFile == null) {
            return "{\"status\":\"error\",\"error\":\"脚本未找到: " + scriptName + "\"}";
        }

        String[] cmd = new String[args.length + 2];
        cmd[0] = pythonCmd;
        cmd[1] = scriptFile.getAbsolutePath();
        System.arraycopy(args, 0, cmd, 2, args.length);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (InputStream is = process.getInputStream()) {
                output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "{\"status\":\"error\",\"error\":\"脚本执行超时\"}";
            }

            return output;

        } catch (Exception e) {
            log.error("执行脚本失败: {}", scriptName, e);
            return "{\"status\":\"error\",\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
