package com.mychat.controller;

import com.mychat.common.result.Result;
import com.mychat.entity.vo.ChatSessionVO;
import com.mychat.service.ChatSessionsService;
import com.mychat.service.SpringAiChatMemoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/history")
public class ChatHistoryController {
    private final ChatMemory chatMemory;
    private final SpringAiChatMemoryService service;
    private final ChatSessionsService chatSessionsService;

    /**
     * 获取指定会话的聊天历史
     *
     * @param conversationId 某个会话的ID
     * @return List<Message>聊天记录
     */
    @GetMapping("/getMessages/{conversationId}")
    public Result<List<Message>> getConversationHistory(@PathVariable String conversationId) {
        // 通过 ChatMemory 获取聊天历史
        return Result.ok(chatMemory.get(conversationId));
    }

    /**
     * 获取所有会话列表
     *
     * @return SpringAiChatMemoryVO 列表
     */
    @GetMapping("/getConversations")
    public Result<List<ChatSessionVO>> getAllConversation() {
        return Result.ok(service.getAllConversation());
    }

    /**
     * 删除指定会话的聊天历史
     */
    @DeleteMapping("/{conversationId}")
    public void deleteConversationHistory(@PathVariable String conversationId) {
        chatMemory.clear(conversationId);
    }

    /**
     * 新增聊天会话
     * @param conversationId 会话ID
     */
    @PostMapping("/addConversation")
    public void addConversation(@RequestParam String conversationId) {
        chatSessionsService.addConversation(conversationId);
    }
}
