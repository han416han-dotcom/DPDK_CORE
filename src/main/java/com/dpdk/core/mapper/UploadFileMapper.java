package com.dpdk.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dpdk.core.model.entity.UploadFile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UploadFileMapper extends BaseMapper<UploadFile> {
}
