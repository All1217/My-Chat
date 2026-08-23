package com.mychat.service.chat.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mychat.entity.po.SpringAiChatMemory;
import com.mychat.vo.ChatSessionVO;
import com.mychat.mapper.SpringAiChatMemoryMapper;
import com.mychat.service.chat.SpringAiChatMemoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SpringAiChatMemoryServiceImpl extends ServiceImpl<SpringAiChatMemoryMapper, SpringAiChatMemory> implements SpringAiChatMemoryService {
    @Autowired
    private SpringAiChatMemoryMapper mapper;

    @Override
    public List<ChatSessionVO> getAllConversation() {
        return mapper.getAllConversation();
    }
}
