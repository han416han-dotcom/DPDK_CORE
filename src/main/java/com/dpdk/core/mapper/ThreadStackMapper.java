package com.dpdk.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dpdk.core.model.entity.ThreadStack;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ThreadStackMapper extends BaseMapper<ThreadStack> {
    @Select("SELECT * FROM thread_stacks WHERE task_id = #{taskId} ORDER BY crash_thread DESC, CAST(thread_id AS UNSIGNED) ASC")
    List<ThreadStack> selectByTaskId(Long taskId);
}
