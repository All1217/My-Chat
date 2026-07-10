package com.mychat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mychat.entity.po.SpringAiChatMemory;
import com.mychat.entity.vo.ChatSessionVO;

import java.util.List;

public interface SpringAiChatMemoryService extends IService<SpringAiChatMemory> {
    List<ChatSessionVO> getAllConversation();
}
