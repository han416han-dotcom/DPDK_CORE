package com.dpdk.core.parser.gdb;

import com.dpdk.core.parser.ParseContext;
import com.dpdk.core.parser.ParseStage;
import com.dpdk.core.parser.Parser;
import com.dpdk.core.parser.python.PythonExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;

/**
 * GDB 日志解析阶段 — 调用 Python parse_gdb_log.py 进行解析。
 * Python 脚本处理复杂的文本解析逻辑, Java 负责数据交换。
 */
@Component
public class GdbLogParser implements Parser {

    private static final Logger log = LoggerFactory.getLogger(GdbLogParser.class);

    private final PythonExecutor pythonExecutor;
    private final ObjectMapper objectMapper;

    public GdbLogParser(PythonExecutor pythonExecutor, ObjectMapper objectMapper) {
        this.pythonExecutor = pythonExecutor;
        this.objectMapper = objectMapper;
    }

    @Override
    public ParseStage getStage() {
        return ParseStage.GDB_LOG_PARSE;
    }

    @Override
    public String getName() {
        return "GdbLogParser";
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean parse(ParseContext context) {
        Path logPath = context.getGdbLogPath();
        if (logPath == null || !logPath.toFile().exists()) {
            context.addError(getName(), "GDB 日志文件不可用: " + logPath);
            return false;
        }

        // 构造 JSON 输入
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("log_path", logPath.toAbsolutePath().toString());

        try {
            String jsonInput = objectMapper.writeValueAsString(input);
            String jsonOutput = pythonExecutor.execute("parse_gdb_log.py", jsonInput);

            Map<String, Object> result = objectMapper.readValue(jsonOutput,
                    new TypeReference<Map<String, Object>>() {});

            String status = (String) result.getOrDefault("status", "error");
            if ("error".equals(status)) {
                context.addError(getName(),
                        "Python 解析失败: " + result.getOrDefault("error", "未知错误"));
                return true; // 不中断, 后续阶段可能利用部分结果
            }

            // 提取解析结果
            List<Map<String, Object>> threads = (List<Map<String, Object>>) result.getOrDefault("threads", List.of());
            Map<String, Object> crashInfo = (Map<String, Object>) result.get("crash_info");
            Map<String, Object> registers = (Map<String, Object>) result.get("registers");
            Map<String, Object> stats = (Map<String, Object>) result.getOrDefault("stats", Map.of());
            List<Map<String, Object>> errors = (List<Map<String, Object>>) result.getOrDefault("errors", List.of());

            context.setParsedThreads(threads);
            context.setCrashInfo(crashInfo);
            if (registers != null) {
                Map<String, String> regMap = new LinkedHashMap<>();
                registers.forEach((k, v) -> regMap.put(k, String.valueOf(v)));
                context.setRegisters(regMap);
            }

            // 统计
            if (stats != null) {
                context.getStats().put("total_threads", stats.getOrDefault("total_threads", 0));
                context.getStats().put("total_frames", stats.getOrDefault("total_frames", 0));
            }

            // 传递 Python 脚本中的 warning/error
            if (errors != null) {
                for (Map<String, Object> e : errors) {
                    String level = (String) e.getOrDefault("level", "WARN");
                    String msg = (String) e.getOrDefault("msg", "");
                    if ("ERROR".equals(level)) {
                        context.addError(getName(), msg);
                    } else {
                        context.addWarning(getName(), msg);
                    }
                }
            }

            context.addLog("INFO", getName(),
                    String.format("解析完成: %d 线程, %d 栈帧",
                            threads.size(),
                            stats.getOrDefault("total_frames", 0)));

        } catch (Exception e) {
            log.error("GDB 日志解析异常", e);
            context.addError(getName(), "解析异常: " + e.getMessage());
        }

        return true;
    }
}
