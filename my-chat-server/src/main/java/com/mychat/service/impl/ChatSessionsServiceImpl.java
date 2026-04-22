package com.mychat.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mychat.entity.po.ChatSessions;
import com.mychat.mapper.ChatSessionsMapper;
import com.mychat.service.ChatSessionsService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ChatSessionsServiceImpl extends ServiceImpl<ChatSessionsMapper, ChatSessions> implements ChatSessionsService {
    private final ChatSessionsMapper chatSessionsMapper;

    @Override
    public void addConversation(String conversationId) {
        ChatSessions dto = new ChatSessions();
        dto.setConversationId(conversationId);
        chatSessionsMapper.insert(dto);
    }
}
