package com.dpdk.core.parser.symbol;

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
 * 符号解析阶段 — 解析 ELF 可执行文件获取符号表,
 * 然后将地址与符号名交叉引用。
 */
@Component
public class SymbolResolver implements Parser {

    private static final Logger log = LoggerFactory.getLogger(SymbolResolver.class);

    private final PythonExecutor pythonExecutor;
    private final ObjectMapper objectMapper;

    public SymbolResolver(PythonExecutor pythonExecutor, ObjectMapper objectMapper) {
        this.pythonExecutor = pythonExecutor;
        this.objectMapper = objectMapper;
    }

    @Override
    public ParseStage getStage() {
        return ParseStage.SYMBOL_RESOLVE;
    }

    @Override
    public String getName() {
        return "SymbolResolver";
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean parse(ParseContext context) {
        Path execPath = context.getExecutablePath();
        if (execPath == null || !execPath.toFile().exists()) {
            context.addWarning(getName(), "可执行文件不可用, 跳过符号解析: " + execPath);
            return true;
        }

        // Step 1: 解析 ELF 符号表 (先尝试完整解析)
        List<Map<String, Object>> symbols = parseElfSymbols(execPath, false);
        context.setSymbols(symbols);

        // Step 1b: 降级 — 完整符号表为空时, 尝试仅读取 .dynsym
        if (symbols == null || symbols.isEmpty()) {
            symbols = parseElfSymbols(execPath, true);
            context.setSymbols(symbols);
        }

        if (symbols == null || symbols.isEmpty()) {
            context.addLog("INFO", getName(), "符号表为空 (文件可能已被 strip，依赖 GDB 自身符号解析)");
            return true;
        }

        context.addLog("INFO", getName(), String.format("ELF 符号表: %d 个符号", symbols.size()));

        // Step 2: 地址交叉引用
        List<Map<String, Object>> threads = context.getParsedThreads();
        if (threads == null || threads.isEmpty()) {
            return true;
        }

        int resolvedCount = 0;
        int totalFrames = 0;

        for (Map<String, Object> thread : threads) {
            List<Map<String, Object>> frames = (List<Map<String, Object>>) thread.get("frames");
            if (frames == null) continue;

            totalFrames += frames.size();
            for (Map<String, Object> frame : frames) {
                String funcName = (String) frame.get("function_name");
                if (funcName != null && !"??".equals(funcName)) {
                    frame.put("resolved", true);
                    // 已经由 GDB 解析, 可信度加满
                    Object confObj = frame.get("confidence");
                    int confidence = confObj instanceof Number ? ((Number) confObj).intValue() : 0;
                    if (confidence == 0) {
                        frame.put("confidence", 100);
                    }
                    continue;
                }

                // 尝试用符号表解析
                String addrStr = (String) frame.get("address");
                if (addrStr == null) continue;

                Map<String, Object> matched = findSymbol(addrStr, symbols);
                if (matched != null) {
                    frame.put("function_name", matched.get("name"));
                    frame.put("resolved", true);
                    frame.put("confidence", 85);
                    resolvedCount++;
                }
            }
        }

        context.addLog("INFO", getName(),
                String.format("地址解析: %d/%d 帧已解析 (ELF 符号表)", resolvedCount, totalFrames));

        return true;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseElfSymbols(Path elfPath, boolean dynsymOnly) {
        try {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("elf_path", elfPath.toAbsolutePath().toString());
            input.put("dynsym_only", dynsymOnly);

            String jsonInput = objectMapper.writeValueAsString(input);
            String jsonOutput = pythonExecutor.execute("parse_elf.py", jsonInput);

            Map<String, Object> result = objectMapper.readValue(jsonOutput,
                    new TypeReference<Map<String, Object>>() {});

            if ("error".equals(result.get("status"))) {
                log.warn("ELF 解析失败: {}", result.get("error"));
                return Collections.emptyList();
            }

            return (List<Map<String, Object>>) result.getOrDefault("symbols", List.of());

        } catch (Exception e) {
            log.warn("ELF 解析异常, 跳过符号解析", e);
            return Collections.emptyList();
        }
    }

    private Map<String, Object> findSymbol(String addrStr, List<Map<String, Object>> symbols) {
        long targetAddr = parseHexAddress(addrStr);
        if (targetAddr < 0) return null;

        Map<String, Object> best = null;
        long bestOffset = Long.MAX_VALUE;
        long maxAllowed = 0x100000; // 1MB 搜索窗口

        for (Map<String, Object> sym : symbols) {
            long symAddr = parseHexAddress((String) sym.get("address"));
            if (symAddr < 0) continue;

            if (symAddr <= targetAddr) {
                long offset = targetAddr - symAddr;
                int size = sym.get("size") instanceof Number
                        ? ((Number) sym.get("size")).intValue() : 0;
                long limit = Math.max(size, maxAllowed);
                if (offset <= limit && offset < bestOffset) {
                    best = sym;
                    bestOffset = offset;
                }
            }
        }
        if (best != null) return best;

        // Pass 2: PIE fallback — 尝试基地址偏移匹配
        // PIE 可执行文件的符号地址是预重定位偏移(如0x2000),
        // GDB 帧地址是运行时绝对地址(如0x555555556000).
        // 尝试: targetAddr - symAddr 得到基地址, 若页对齐则匹配
        for (Map<String, Object> sym : symbols) {
            long symAddr = parseHexAddress((String) sym.get("address"));
            if (symAddr < 0) continue;

            long candidateBase = targetAddr - symAddr;
            if (candidateBase > 0 && candidateBase % 0x1000 == 0
                    && candidateBase > symAddr) {
                return sym;
            }
        }

        return null;
    }

    private long parseHexAddress(String addrStr) {
        if (addrStr == null) return -1;
        try {
            if (addrStr.startsWith("0x") || addrStr.startsWith("0X")) {
                return Long.parseLong(addrStr.substring(2), 16);
            }
            return Long.parseLong(addrStr, 16);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
