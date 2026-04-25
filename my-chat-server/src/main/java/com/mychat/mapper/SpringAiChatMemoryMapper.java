package com.mychat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mychat.entity.po.SpringAiChatMemory;
import com.mychat.entity.vo.ChatSessionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SpringAiChatMemoryMapper extends BaseMapper<SpringAiChatMemory> {
    @Select("select conversation_id, title from chat_sessions;")
    List<ChatSessionVO> getAllConversation();
}
