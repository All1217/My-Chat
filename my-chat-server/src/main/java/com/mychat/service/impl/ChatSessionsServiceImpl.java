package com.mychat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mychat.common.result.Result;
import com.mychat.entity.dto.ChatSessionsDTO;
import com.mychat.entity.po.ChatSessions;
import com.mychat.entity.po.SpringAiChatMemory;
import com.mychat.entity.vo.ChatSessionVO;
import com.mychat.mapper.ChatSessionsMapper;
import com.mychat.mapper.SpringAiChatMemoryMapper;
import com.mychat.service.ChatSessionsService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static com.mychat.common.result.ResultCodeEnum.NORMAL_PARAM_ERROR;

@Service
@AllArgsConstructor
public class ChatSessionsServiceImpl extends ServiceImpl<ChatSessionsMapper, ChatSessions> implements ChatSessionsService {
    private final ChatSessionsMapper chatSessionsMapper;
    private final SpringAiChatMemoryMapper springAiChatMemoryMapper;

    @Override
    public void addConversation(String conversationId) {
        addConversation(conversationId, null);
    }

    @Override
    public void addConversation(String conversationId, String kbId) {
        ChatSessions dto = new ChatSessions();
        dto.setConversationId(conversationId);
        dto.setTitle(conversationId);
        dto.setKbId(kbId);
        chatSessionsMapper.insert(dto);
    }

    @Override
    public Result updateConversation(ChatSessionsDTO dto) {
        if (dto == null || dto.getConversationId() == null || dto.getConversationId().isEmpty()) {
            return Result.fail(NORMAL_PARAM_ERROR.getCode(), NORMAL_PARAM_ERROR.getMessage());
        }
        ChatSessions entity = new ChatSessions();
        entity.setConversationId(dto.getConversationId());
        if (dto.getTitle() != null) {
            entity.setTitle(dto.getTitle());
        }
        if (dto.getUserId() != null) {
            entity.setUserId(dto.getUserId());
        }
        int res = chatSessionsMapper.updateById(entity);
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

    @Override
    public List<ChatSessionVO> getAllConversations() {
        LambdaQueryWrapper<ChatSessions> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ChatSessions::getCreatedAt);
        return toVOList(chatSessionsMapper.selectList(wrapper));
    }

    @Override
    public List<ChatSessionVO> getConversationsByKbId(String kbId) {
        LambdaQueryWrapper<ChatSessions> wrapper = new LambdaQueryWrapper<>();
        if (kbId == null) {
            wrapper.isNull(ChatSessions::getKbId);
        } else {
            wrapper.eq(ChatSessions::getKbId, kbId);
        }
        wrapper.orderByDesc(ChatSessions::getCreatedAt);
        return toVOList(chatSessionsMapper.selectList(wrapper));
    }

    private List<ChatSessionVO> toVOList(List<ChatSessions> list) {
        return list.stream().map(s -> {
            ChatSessionVO vo = new ChatSessionVO();
            vo.setConversationId(s.getConversationId());
            vo.setTitle(s.getTitle());
            vo.setKbId(s.getKbId());
            return vo;
        }).collect(Collectors.toList());
    }
}
