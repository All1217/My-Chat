package com.mychat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mychat.common.result.Result;
import com.mychat.entity.dto.ChatSessionsDTO;
import com.mychat.entity.po.ChatSessions;

public interface ChatSessionsService extends IService<ChatSessions> {
    void addConversation(String conversationId);

    Result updateConversation(ChatSessionsDTO dto);

    void deleteChatSessionById(String id);
}
