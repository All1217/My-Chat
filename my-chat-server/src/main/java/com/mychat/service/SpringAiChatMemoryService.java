package com.mychat.service;

import com.mychat.entity.vo.SpringAiChatMemoryVO;

import java.util.List;

public interface SpringAiChatMemoryService {
    List<SpringAiChatMemoryVO> getAllConversation();
}
