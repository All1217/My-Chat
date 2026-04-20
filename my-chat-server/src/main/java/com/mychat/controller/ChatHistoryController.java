package com.mychat.controller;

import com.mychat.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/history")
public class ChatHistoryController {
    private final ChatMemory chatMemory;
    private final ChatMemoryRepository chatMemoryRepository;

    /**
     * 获取指定会话的聊天历史
     *
     * @param conversationId 某个会话的ID
     * @return List<Message>聊天记录
     */
    @GetMapping("/{conversationId}")
    public Result<List<Message>> getConversationHistory(@PathVariable String conversationId) {
        // 通过 ChatMemory 获取聊天历史
        return Result.ok(chatMemory.get(conversationId));
    }

    /**
     * 获取所有会话ID列表
     *
     * @return String类型的ID列表
     */
    @GetMapping("/getIds")
    public Result<List<String>> getAllConversationIds() {
        // 通过 ChatMemoryRepository 获取所有会话ID
        // 注意：需要根据具体的 Repository 实现来调整
        return Result.ok(chatMemoryRepository.findConversationIds());
    }

    /**
     * 删除指定会话的聊天历史
     */
    @DeleteMapping("/{conversationId}")
    public void deleteConversationHistory(@PathVariable String conversationId) {
        chatMemory.clear(conversationId);
    }
}
