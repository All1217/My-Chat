package com.mychat.mapper;

import com.mychat.entity.vo.SpringAiChatMemoryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SpringAiChatMemoryMapper {
    @Select("select conversation_id, title from spring_ai_chat_memory group by conversation_id;")
    List<SpringAiChatMemoryVO> getAllConversation();
}
