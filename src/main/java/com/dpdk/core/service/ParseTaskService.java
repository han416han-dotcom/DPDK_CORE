package com.dpdk.core.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dpdk.core.model.dto.ParseResultVO;
import com.dpdk.core.model.dto.TaskCreateRequest;
import com.dpdk.core.model.entity.ParseTask;

/**
 * 解析任务服务 — 任务生命周期管理 + 解析执行。
 */
public interface ParseTaskService {

    /**
     * 创建并启动解析任务
     */
    ParseTask createAndStart(TaskCreateRequest request);

    /**
     * 获取任务详情
     */
    ParseTask getTask(Long taskId);

    /**
     * 获取解析结果 (含完整的线程/帧数据)
     */
    ParseResultVO getResult(Long taskId);

    /**
     * 分页查询任务列表
     */
    Page<ParseTask> listTasks(int page, int size);

    /**
     * 删除任务及关联数据
     */
    void deleteTask(Long taskId);
}
