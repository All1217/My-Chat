package com.mychat.controller;

import com.mychat.common.result.Result;
import com.mychat.entity.dto.ChatSessionsDTO;
import com.mychat.vo.ChatMessageVO;
import com.mychat.vo.ChatSessionVO;
import com.mychat.service.chat.ChatAssistantTurnService;
import com.mychat.service.chat.ChatSessionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai/history")
@RequiredArgsConstructor
public class ChatHistoryController {

    private final ChatMemory chatMemory;
    private final ChatSessionsService chatSessionsService;
    private final ChatAssistantTurnService chatAssistantTurnService;

    /**
     * 获取指定会话的聊天历史（含可选工具时间线 parts / thinking）。
     */
    @GetMapping("/getMessages/{conversationId}")
    public Result<List<ChatMessageVO>> getConversationHistory(@PathVariable String conversationId) {
        return Result.ok(chatAssistantTurnService.mergeHistory(
                conversationId, chatMemory.get(conversationId)));
    }

    /**
     * 获取会话列表
     *
     * @param kbId 可选：null → 返回全部；具体值 → 返回该知识库的会话
     */
    @GetMapping("/getConversations")
    public Result<List<ChatSessionVO>> getConversations(
            @RequestParam(value = "kbId", required = false) String kbId) {
        if (kbId != null) {
            return Result.ok(chatSessionsService.getConversationsByKbId(kbId));
        }
        return Result.ok(chatSessionsService.getAllConversations());
    }

    /**
     * 删除指定会话的聊天历史
     */
    @DeleteMapping("/deleteById")
    public Result<Void> deleteChatSessionById(@RequestParam String id) {
        chatSessionsService.deleteChatSessionById(id);
        return Result.ok();
    }

    /**
     * 新增聊天会话
     *
     * @param conversationId 会话ID
     * @param kbId           可选：关联的知识库ID
     */
    @PostMapping("/addConversation")
    public Result<Void> addConversation(
            @RequestParam String conversationId,
            @RequestParam(value = "kbId", required = false) String kbId,
            @RequestParam(value = "workDir", required = false) String workDir) {
        chatSessionsService.addConversation(conversationId, kbId, workDir);
        return Result.ok();
    }

    /**
     * 更新聊天会话
     * @param dto 会话DTO，内含更新条件
     */
    @PostMapping("/update")
    public Result updateConversation(@RequestBody ChatSessionsDTO dto) {
        return chatSessionsService.updateConversation(dto);
    }
}
