package com.dpdk.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dpdk.core.mapper.*;
import com.dpdk.core.model.dto.ParseResultVO;
import com.dpdk.core.model.dto.TaskCreateRequest;
import com.dpdk.core.model.entity.*;
import com.dpdk.core.parser.ParseContext;
import com.dpdk.core.parser.Pipeline;
import com.dpdk.core.service.FileStorageService;
import com.dpdk.core.service.ParseTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ParseTaskServiceImpl implements ParseTaskService {

    private static final Logger log = LoggerFactory.getLogger(ParseTaskServiceImpl.class);

    private final ParseTaskMapper taskMapper;
    private final ThreadStackMapper threadStackMapper;
    private final FrameDetailMapper frameDetailMapper;
    private final ParseLogMapper parseLogMapper;
    private final FileStorageService fileStorageService;
    private final Pipeline pipeline;
    private final ObjectMapper objectMapper;

    private static final String DIAG_STAGE = "CRASH_DIAGNOSE";

    public ParseTaskServiceImpl(ParseTaskMapper taskMapper,
                                ThreadStackMapper threadStackMapper,
                                FrameDetailMapper frameDetailMapper,
                                ParseLogMapper parseLogMapper,
                                FileStorageService fileStorageService,
                                Pipeline pipeline,
                                ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.threadStackMapper = threadStackMapper;
        this.frameDetailMapper = frameDetailMapper;
        this.parseLogMapper = parseLogMapper;
        this.fileStorageService = fileStorageService;
        this.pipeline = pipeline;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public ParseTask createAndStart(TaskCreateRequest request) {
        // 1. 验证文件
        UploadFile gdbFile = fileStorageService.getFileById(request.getGdbLogFileId());
        if (gdbFile == null) {
            throw new IllegalArgumentException("GDB 日志文件不存在: " + request.getGdbLogFileId());
        }
        UploadFile execFile = fileStorageService.getFileById(request.getExecFileId());
        if (execFile == null) {
            throw new IllegalArgumentException("可执行文件不存在: " + request.getExecFileId());
        }

        // 2. 校验物理文件存在
        if (!Files.exists(Path.of(gdbFile.getStoragePath()))) {
            throw new IllegalArgumentException("GDB 日志文件已不存在于磁盘: " + gdbFile.getStoragePath());
        }
        if (!Files.exists(Path.of(execFile.getStoragePath()))) {
            throw new IllegalArgumentException("可执行文件已不存在于磁盘: " + execFile.getStoragePath());
        }

        // 3. 创建任务
        ParseTask task = new ParseTask();
        task.setGdbLogFileId(request.getGdbLogFileId());
        task.setExecFileId(request.getExecFileId());
        task.setTaskName(request.getTaskName() != null ? request.getTaskName() : gdbFile.getFileName());
        task.setStatus("PENDING");
        task.setCreatedAt(LocalDateTime.now());
        taskMapper.insert(task);

        log.info("创建解析任务: id={}, name={}", task.getId(), task.getTaskName());

        // 4. 执行解析 (同步)
        executeParse(task, gdbFile, execFile);

        return task;
    }

    private void executeParse(ParseTask task, UploadFile gdbFile, UploadFile execFile) {
        // 更新状态为运行中
        task.setStatus("RUNNING");
        task.setParseVersion("1.0");
        taskMapper.updateById(task);

        ParseContext context = new ParseContext(task.getId());
        context.setGdbLogPath(Path.of(gdbFile.getStoragePath()));
        context.setExecutablePath(Path.of(execFile.getStoragePath()));

        try {
            // 执行解析管道
            boolean success = pipeline.execute(context);

            // 保存解析结果
            saveParseResult(task, context);

            // 保存诊断信息
            Map<String, Object> diagnosis = (Map<String, Object>) context.getExtras().get("diagnosis");
            if (diagnosis != null) {
                ParseLog diagLog = new ParseLog();
                diagLog.setTaskId(task.getId());
                diagLog.setLogLevel("INFO");
                diagLog.setStage(DIAG_STAGE);
                diagLog.setMessage(objectMapper.writeValueAsString(diagnosis));
                diagLog.setCreatedAt(LocalDateTime.now());
                parseLogMapper.insert(diagLog);
            }

            // 更新任务状态
            task.setStatus(success ? "COMPLETED" : "FAILED");
            task.setCompletedAt(LocalDateTime.now());

            // 从上下文获取崩溃信息
            Map<String, Object> crashInfo = context.getCrashInfo();
            if (crashInfo != null) {
                task.setCrashSignal((String) crashInfo.getOrDefault("signal_name", ""));
                task.setFaultAddress((String) crashInfo.getOrDefault("fault_address", ""));
            }

            // 线程数
            List<Map<String, Object>> threads = context.getParsedThreads();
            task.setTotalThreads(threads != null ? threads.size() : 0);

        } catch (Exception e) {
            log.error("解析任务执行异常: taskId={}", task.getId(), e);
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setCompletedAt(LocalDateTime.now());
        }

        taskMapper.updateById(task);
    }

    @SuppressWarnings("unchecked")
    private void saveParseResult(ParseTask task, ParseContext context) {
        List<Map<String, Object>> threads = context.getParsedThreads();
        if (threads == null) return;

        for (Map<String, Object> threadData : threads) {
            // 保存线程
            ThreadStack thread = new ThreadStack();
            thread.setTaskId(task.getId());
            thread.setThreadId(String.valueOf(threadData.getOrDefault("thread_id", "")));
            thread.setThreadName((String) threadData.get("thread_name"));
            thread.setOsThreadId((String) threadData.get("os_thread_id"));
            thread.setIsLcore(Boolean.TRUE.equals(threadData.get("is_lcore")));
            Object lcoreObj = threadData.get("lcore_id");
            thread.setLcoreId(lcoreObj instanceof Number ? ((Number) lcoreObj).intValue() : null);
            thread.setCrashThread(Boolean.TRUE.equals(threadData.get("crash_thread")));
            thread.setStackDepth(threadData.get("stack_depth") instanceof Number
                    ? ((Number) threadData.get("stack_depth")).intValue() : 0);
            thread.setRawHeader(null);

            threadStackMapper.insert(thread);

            // 保存帧
            List<Map<String, Object>> frames = (List<Map<String, Object>>) threadData.get("frames");
            if (frames != null) {
                for (Map<String, Object> frameData : frames) {
                    FrameDetail frame = new FrameDetail();
                    frame.setThreadId(thread.getId());
                    frame.setFrameIndex(frameData.get("frame_index") instanceof Number
                            ? ((Number) frameData.get("frame_index")).intValue() : 0);
                    frame.setRawLine((String) frameData.get("raw_line"));
                    frame.setAddress((String) frameData.get("address"));
                    frame.setFunctionName((String) frameData.get("function_name"));
                    frame.setSourceFile((String) frameData.get("source_file"));
                    frame.setSourceLine(frameData.get("source_line") instanceof Number
                            ? ((Number) frameData.get("source_line")).intValue() : null);
                    frame.setOffsetInFunc((String) frameData.get("offset_in_func"));
                    frame.setArgs((String) frameData.get("args"));
                    frame.setResolved(Boolean.TRUE.equals(frameData.get("resolved")));
                    frame.setIsDpdkFunc(Boolean.TRUE.equals(frameData.get("is_dpdk_func")));
                    frame.setConfidence(frameData.get("confidence") instanceof Number
                            ? ((Number) frameData.get("confidence")).intValue() : 0);

                    frameDetailMapper.insert(frame);
                }
            }
        }

        // 保存解析日志
        for (ParseContext.ParseLogEntry logEntry : context.getLogs()) {
            ParseLog parseLog = new ParseLog();
            parseLog.setTaskId(task.getId());
            parseLog.setLogLevel(logEntry.getLevel());
            parseLog.setStage(logEntry.getStage());
            parseLog.setMessage(logEntry.getMessage());
            parseLog.setCreatedAt(LocalDateTime.now());
            parseLogMapper.insert(parseLog);
        }
    }

    @Override
    public ParseTask getTask(Long taskId) {
        return taskMapper.selectById(taskId);
    }

    @Override
    public ParseResultVO getResult(Long taskId) {
        ParseTask task = taskMapper.selectById(taskId);
        if (task == null) return null;

        ParseResultVO vo = new ParseResultVO();
        vo.setTaskId(task.getId());
        vo.setTaskName(task.getTaskName());
        vo.setStatus(task.getStatus());
        vo.setCrashSignal(task.getCrashSignal());
        vo.setFaultAddress(task.getFaultAddress());
        vo.setTotalThreads(task.getTotalThreads());

        // 加载线程
        List<ThreadStack> threads = threadStackMapper.selectByTaskId(taskId);
        List<ParseResultVO.ThreadInfo> threadInfos = new ArrayList<>();

        for (ThreadStack t : threads) {
            ParseResultVO.ThreadInfo ti = new ParseResultVO.ThreadInfo();
            ti.setId(t.getId());
            ti.setThreadId(t.getThreadId());
            ti.setThreadName(t.getThreadName());
            ti.setIsLcore(t.getIsLcore());
            ti.setLcoreId(t.getLcoreId());
            ti.setCrashThread(t.getCrashThread());
            ti.setStackDepth(t.getStackDepth());

            // 加载帧
            List<FrameDetail> frames = frameDetailMapper.selectByThreadId(t.getId());
            List<ParseResultVO.FrameInfo> frameInfos = frames.stream().map(f -> {
                ParseResultVO.FrameInfo fi = new ParseResultVO.FrameInfo();
                fi.setIndex(f.getFrameIndex());
                fi.setAddress(f.getAddress());
                fi.setFunctionName(f.getFunctionName());
                fi.setSourceFile(f.getSourceFile());
                fi.setSourceLine(f.getSourceLine());
                fi.setOffsetInFunc(f.getOffsetInFunc());
                fi.setArgs(f.getArgs());
                fi.setResolved(f.getResolved());
                fi.setIsDpdkFunc(f.getIsDpdkFunc());
                fi.setConfidence(f.getConfidence());
                return fi;
            }).collect(Collectors.toList());

            ti.setFrames(frameInfos);
            threadInfos.add(ti);
        }

        vo.setThreads(threadInfos);

        // 加载解析日志
        List<ParseLog> logs = parseLogMapper.selectList(
                new LambdaQueryWrapper<ParseLog>()
                        .eq(ParseLog::getTaskId, taskId)
                        .orderByAsc(ParseLog::getCreatedAt)
        );
        vo.setLogs(logs);

        // 加载诊断信息
        ParseLog diagLog = parseLogMapper.selectOne(
                new LambdaQueryWrapper<ParseLog>()
                        .eq(ParseLog::getTaskId, taskId)
                        .eq(ParseLog::getStage, DIAG_STAGE)
                        .last("LIMIT 1")
        );
        if (diagLog != null && diagLog.getMessage() != null && !diagLog.getMessage().isEmpty()) {
            try {
                Map<String, Object> dm = objectMapper.readValue(diagLog.getMessage(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                ParseResultVO.Diagnosis diag = new ParseResultVO.Diagnosis();
                diag.setCrashType((String) dm.get("crashType"));
                diag.setCrashTypeId((String) dm.get("crashTypeId"));
                diag.setConfidence(dm.get("confidence") instanceof Number
                        ? ((Number) dm.get("confidence")).intValue() : 0);
                diag.setCrashFunction((String) dm.get("crashFunction"));
                diag.setSourceLocation((String) dm.get("sourceLocation"));
                diag.setSignal((String) dm.get("signal"));
                diag.setFaultAddress((String) dm.get("faultAddress"));
                diag.setIsAbortChain(Boolean.TRUE.equals(dm.get("isAbortChain")));
                diag.setRootCause((String) dm.get("rootCause"));
                diag.setSuggestion((String) dm.get("suggestion"));
                diag.setKeyFunctions((List<String>) dm.get("keyFunctions"));
                diag.setContentionDetected(Boolean.TRUE.equals(dm.get("contentionDetected")));
                diag.setRelatedThreads((List<Map<String, String>>) dm.get("relatedThreads"));
                diag.setSubPattern((String) dm.get("subPattern"));
                diag.setSubPatternLabel((String) dm.get("subPatternLabel"));
                diag.setAbortSourceFunc((String) dm.get("abortSourceFunc"));
                diag.setAbortSourceDepth(dm.get("abortSourceDepth") instanceof Number
                        ? ((Number) dm.get("abortSourceDepth")).intValue() : -1);
                vo.setDiagnosis(diag);
            } catch (Exception e) {
                log.warn("诊断信息反序列化失败: taskId={}", taskId, e);
            }
        }

        return vo;
    }

    @Override
    public Page<ParseTask> listTasks(int page, int size) {
        return taskMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<ParseTask>().orderByDesc(ParseTask::getCreatedAt)
        );
    }

    @Override
    @Transactional
    public void deleteTask(Long taskId) {
        // 级联删除: ON DELETE CASCADE 会自动删除关联的 thread_stacks, frame_details, parse_logs
        taskMapper.deleteById(taskId);
        log.info("删除解析任务: id={}", taskId);
    }
}
