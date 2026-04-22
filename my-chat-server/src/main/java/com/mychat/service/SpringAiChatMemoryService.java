package com.mychat.service;

import com.mychat.entity.vo.ChatSessionVO;

import java.util.List;

public interface SpringAiChatMemoryService {
    List<ChatSessionVO> getAllConversation();
}
