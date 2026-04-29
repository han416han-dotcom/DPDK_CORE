package com.dpdk.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dpdk.core.model.entity.FrameDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FrameDetailMapper extends BaseMapper<FrameDetail> {
    @Select("SELECT * FROM frame_details WHERE thread_id = #{threadId} ORDER BY frame_index ASC")
    List<FrameDetail> selectByThreadId(Long threadId);
}
