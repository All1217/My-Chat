package com.mychat.service.impl;

import com.mychat.entity.vo.SpringAiChatMemoryVO;
import com.mychat.mapper.SpringAiChatMemoryMapper;
import com.mychat.service.SpringAiChatMemoryService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class SpringAiChatMemoryServiceImpl implements SpringAiChatMemoryService {
    private final SpringAiChatMemoryMapper mapper;

    @Override
    public List<SpringAiChatMemoryVO> getAllConversation() {
        return mapper.getAllConversation();
    }
}
