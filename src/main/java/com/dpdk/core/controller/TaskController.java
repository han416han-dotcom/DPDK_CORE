package com.dpdk.core.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dpdk.core.model.dto.ParseResultVO;
import com.dpdk.core.model.dto.TaskCreateRequest;
import com.dpdk.core.model.entity.ParseTask;
import com.dpdk.core.service.ParseTaskService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 解析任务接口
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final ParseTaskService taskService;

    public TaskController(ParseTaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 创建解析任务
     */
    @PostMapping("/create")
    public ResponseEntity<ParseTask> createTask(@Valid @RequestBody TaskCreateRequest request) {
        ParseTask task = taskService.createAndStart(request);
        return ResponseEntity.ok(task);
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<ParseTask> getTask(@PathVariable Long id) {
        ParseTask task = taskService.getTask(id);
        if (task == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(task);
    }

    /**
     * 获取解析结果 (完整数据)
     */
    @GetMapping("/{id}/result")
    public ResponseEntity<ParseResultVO> getResult(@PathVariable Long id) {
        ParseResultVO result = taskService.getResult(id);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 任务列表
     */
    @GetMapping("/list")
    public ResponseEntity<Page<ParseTask>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(taskService.listTasks(page, size));
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        taskService.deleteTask(id);
        return ResponseEntity.ok().build();
    }
}
