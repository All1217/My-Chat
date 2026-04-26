package com.mychat.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mychat.entity.po.SpringAiChatMemory;
import com.mychat.entity.vo.ChatSessionVO;
import com.mychat.mapper.SpringAiChatMemoryMapper;
import com.mychat.service.SpringAiChatMemoryService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class SpringAiChatMemoryServiceImpl extends ServiceImpl<SpringAiChatMemoryMapper, SpringAiChatMemory> implements SpringAiChatMemoryService {
    private final SpringAiChatMemoryMapper mapper;

    @Override
    public List<ChatSessionVO> getAllConversation() {
        return mapper.getAllConversation();
    }
}
