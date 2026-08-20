package com.mychat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mychat.entity.po.ChatSessionSummary;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionSummaryMapper extends BaseMapper<ChatSessionSummary> {
}
