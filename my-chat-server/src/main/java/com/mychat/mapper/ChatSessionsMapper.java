package com.mychat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mychat.entity.po.ChatSessions;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatSessionsMapper extends BaseMapper<ChatSessions> {
}
