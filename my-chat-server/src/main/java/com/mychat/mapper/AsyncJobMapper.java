package com.mychat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mychat.entity.po.AsyncJob;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AsyncJobMapper extends BaseMapper<AsyncJob> {
}
