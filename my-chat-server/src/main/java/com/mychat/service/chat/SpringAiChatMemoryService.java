package com.mychat.service.chat;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mychat.entity.po.SpringAiChatMemory;
import com.mychat.vo.ChatSessionVO;

import java.util.List;

public interface SpringAiChatMemoryService extends IService<SpringAiChatMemory> {
    List<ChatSessionVO> getAllConversation();
}
