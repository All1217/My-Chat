package com.mychat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mychat.common.ChatStreamEvent;
import com.mychat.entity.po.ChatAssistantTurn;
import com.mychat.entity.vo.ChatMessageVO;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 助手回合 UI 轨迹（parts / thinking）持久化与历史合并。
 */
public interface ChatAssistantTurnService extends IService<ChatAssistantTurn> {

    /**
     * 从本轮流式事件归约并异步落库（闲聊无工具且无 thinking 则跳过）。
     *
     * @param cancelledOrError 取消/错误时，将 running 工具标为 cancelled
     */
    void saveTurnFromEvents(String conversationId, String turnId,
                            List<ChatStreamEvent> events, boolean cancelledOrError);

    /** 按会话删除全部回合轨迹（删会话时级联） */
    void deleteByConversation(String conversationId);

    /** Memory 消息 + 回合表合并为前端可用的 VO 列表 */
    List<ChatMessageVO> mergeHistory(String conversationId, List<Message> memoryMessages);

    /** 当前库中该会话最大 assistant_ordinal；无记录返回 -1 */
    int maxOrdinal(String conversationId);
}
