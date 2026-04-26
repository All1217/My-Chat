package com.mychat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mychat.common.result.Result;
import com.mychat.entity.dto.ChatSessionsDTO;
import com.mychat.entity.po.ChatSessions;
import com.mychat.entity.po.SpringAiChatMemory;
import com.mychat.mapper.ChatSessionsMapper;
import com.mychat.mapper.SpringAiChatMemoryMapper;
import com.mychat.service.ChatSessionsService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.mychat.common.result.ResultCodeEnum.NORMAL_PARAM_ERROR;

@Service
@AllArgsConstructor
public class ChatSessionsServiceImpl extends ServiceImpl<ChatSessionsMapper, ChatSessions> implements ChatSessionsService {
    private final ChatSessionsMapper chatSessionsMapper;
    private final SpringAiChatMemoryMapper springAiChatMemoryMapper;

    @Override
    public void addConversation(String conversationId) {
        ChatSessions dto = new ChatSessions();
        dto.setConversationId(conversationId);
        chatSessionsMapper.insert(dto);
    }

    @Override
    public Result updateConversation(ChatSessionsDTO dto) {
        if (dto == null || dto.getConversationId() == null || dto.getConversationId().isEmpty()) {
            return Result.fail(NORMAL_PARAM_ERROR.getCode(), NORMAL_PARAM_ERROR.getMessage());
        }
        LambdaUpdateWrapper<ChatSessions> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ChatSessions::getConversationId, dto.getConversationId())
                .set(dto.getTitle() != null, ChatSessions::getTitle, dto.getTitle())
                .set(dto.getUserId() != null, ChatSessions::getUserId, dto.getUserId());
        int res = chatSessionsMapper.update(wrapper);
        return res > 0 ? Result.ok("更新成功") : Result.fail("SQL执行失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteChatSessionById(String id) {
        chatSessionsMapper.deleteById(id);
        LambdaQueryWrapper<SpringAiChatMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SpringAiChatMemory::getConversationId, id);
        springAiChatMemoryMapper.delete(wrapper);
    }
}
