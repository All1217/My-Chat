package com.mychat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mychat.entity.po.ChatSessions;

public interface ChatSessionsService extends IService<ChatSessions> {
    void addConversation(String conversationId);
}
