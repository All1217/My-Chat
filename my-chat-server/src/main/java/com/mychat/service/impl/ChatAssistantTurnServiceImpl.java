package com.mychat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mychat.chat.stream.TurnPartsReducer;
import com.mychat.common.ChatStreamEvent;
import com.mychat.entity.po.ChatAssistantTurn;
import com.mychat.vo.ChatMessageVO;
import com.mychat.vo.MessagePartVO;
import com.mychat.mapper.ChatAssistantTurnMapper;
import com.mychat.service.ChatAssistantTurnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAssistantTurnServiceImpl
        extends ServiceImpl<ChatAssistantTurnMapper, ChatAssistantTurn>
        implements ChatAssistantTurnService {

    private final ChatMemory chatMemory;

    @Override
    public void saveTurnFromEvents(String conversationId, String turnId,
                                   List<ChatStreamEvent> events, boolean cancelledOrError) {
        if (conversationId == null || turnId == null) {
            return;
        }
        TurnPartsReducer.TurnSnapshot snap = TurnPartsReducer.reduce(events, cancelledOrError);
        if (!snap.hasTrajectory()) {
            return;
        }

        int ordinal = resolveAssistantOrdinal(conversationId);
        ChatAssistantTurn row = new ChatAssistantTurn();
        row.setConversationId(conversationId);
        row.setTurnId(turnId);
        row.setAssistantOrdinal(ordinal);
        row.setAssistantText(snap.assistantText());
        row.setThinking(snap.thinking());
        row.setParts(snap.parts() != null ? snap.parts() : List.of());

        try {
            upsertByTurnId(row);
        } catch (Exception e) {
            log.error("保存助手回合失败 conversationId={} turnId={}", conversationId, turnId, e);
        }
    }

    private void upsertByTurnId(ChatAssistantTurn row) {
        LambdaQueryWrapper<ChatAssistantTurn> byTurn = new LambdaQueryWrapper<>();
        byTurn.eq(ChatAssistantTurn::getConversationId, row.getConversationId())
                .eq(ChatAssistantTurn::getTurnId, row.getTurnId());
        ChatAssistantTurn existing = getOne(byTurn, false);
        if (existing != null) {
            existing.setAssistantText(row.getAssistantText());
            existing.setThinking(row.getThinking());
            existing.setParts(row.getParts());
            // ordinal 已占用则保持原值，避免唯一键冲突
            updateById(existing);
            return;
        }
        try {
            save(row);
        } catch (DuplicateKeyException ex) {
            log.warn("助手回合唯一键冲突，尝试按 turn_id 更新 conversationId={} turnId={} ordinal={}",
                    row.getConversationId(), row.getTurnId(), row.getAssistantOrdinal());
            ChatAssistantTurn again = getOne(byTurn, false);
            if (again != null) {
                again.setAssistantText(row.getAssistantText());
                again.setThinking(row.getThinking());
                again.setParts(row.getParts());
                updateById(again);
            } else {
                // ordinal 冲突：改用 max+1 再插
                row.setAssistantOrdinal(maxOrdinal(row.getConversationId()) + 1);
                save(row);
            }
        }
    }

    /**
     * Memory ASSISTANT 条数 - 1；若 Memory 滞后则用 DB max+1，避免覆盖上一轮。
     */
    private int resolveAssistantOrdinal(String conversationId) {
        int memoryCount = countAssistantsInMemory(conversationId);
        if (memoryCount <= 0) {
            try {
                Thread.sleep(120);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            memoryCount = countAssistantsInMemory(conversationId);
        }

        int nextDb = maxOrdinal(conversationId) + 1;
        if (memoryCount > 0) {
            int candidate = memoryCount - 1;
            if (candidate < nextDb) {
                log.warn("Memory 可能滞后：conversationId={} memoryAssistants={} nextDbOrdinal={}，改用 nextDb",
                        conversationId, memoryCount, nextDb);
                return nextDb;
            }
            return candidate;
        }
        log.warn("无法从 Memory 解析 ASSISTANT 序号，使用 DB nextOrdinal={} conversationId={}",
                nextDb, conversationId);
        return nextDb;
    }

    private int countAssistantsInMemory(String conversationId) {
        try {
            List<Message> msgs = chatMemory.get(conversationId);
            if (msgs == null) {
                return 0;
            }
            int n = 0;
            for (Message m : msgs) {
                if (m != null && m.getMessageType() == MessageType.ASSISTANT) {
                    n++;
                }
            }
            return n;
        } catch (Exception e) {
            log.warn("读取 ChatMemory 失败 conversationId={}: {}", conversationId, e.getMessage());
            return 0;
        }
    }

    @Override
    public int maxOrdinal(String conversationId) {
        LambdaQueryWrapper<ChatAssistantTurn> q = new LambdaQueryWrapper<>();
        q.eq(ChatAssistantTurn::getConversationId, conversationId)
                .orderByDesc(ChatAssistantTurn::getAssistantOrdinal)
                .last("LIMIT 1");
        ChatAssistantTurn one = getOne(q, false);
        return one != null && one.getAssistantOrdinal() != null ? one.getAssistantOrdinal() : -1;
    }

    @Override
    public void deleteByConversation(String conversationId) {
        if (conversationId == null) {
            return;
        }
        LambdaQueryWrapper<ChatAssistantTurn> q = new LambdaQueryWrapper<>();
        q.eq(ChatAssistantTurn::getConversationId, conversationId);
        remove(q);
    }

    @Override
    public List<ChatMessageVO> mergeHistory(String conversationId, List<Message> memoryMessages) {
        List<ChatMessageVO> result = new ArrayList<>();
        if (memoryMessages == null || memoryMessages.isEmpty()) {
            return result;
        }

        List<ChatAssistantTurn> turns = listByConversation(conversationId);
        Map<Integer, ChatAssistantTurn> byOrdinal = new HashMap<>();
        Map<String, ChatAssistantTurn> byText = new HashMap<>();
        for (ChatAssistantTurn t : turns) {
            if (t.getAssistantOrdinal() != null) {
                byOrdinal.put(t.getAssistantOrdinal(), t);
            }
            if (t.getAssistantText() != null && !t.getAssistantText().isEmpty()) {
                byText.putIfAbsent(t.getAssistantText(), t);
            }
        }

        Set<Long> usedTurnIds = new HashSet<>();
        int assistantIndex = 0;

        for (Message msg : memoryMessages) {
            ChatMessageVO vo = toBaseVo(msg);
            if (msg.getMessageType() == MessageType.ASSISTANT) {
                ChatAssistantTurn matched = byOrdinal.get(assistantIndex);
                if (matched != null && usedTurnIds.add(matched.getId())) {
                    attachTurn(vo, matched);
                } else {
                    ChatAssistantTurn byExact = byText.get(Objects.toString(msg.getText(), ""));
                    if (byExact != null && usedTurnIds.add(byExact.getId())) {
                        attachTurn(vo, byExact);
                    }
                }
                assistantIndex++;
            }
            result.add(vo);
        }
        return result;
    }

    private List<ChatAssistantTurn> listByConversation(String conversationId) {
        LambdaQueryWrapper<ChatAssistantTurn> q = new LambdaQueryWrapper<>();
        q.eq(ChatAssistantTurn::getConversationId, conversationId)
                .orderByAsc(ChatAssistantTurn::getAssistantOrdinal);
        return list(q);
    }

    private static ChatMessageVO toBaseVo(Message msg) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setMessageType(msg.getMessageType() != null ? msg.getMessageType().name() : null);
        vo.setText(msg.getText() != null ? msg.getText() : "");
        return vo;
    }

    private static void attachTurn(ChatMessageVO vo, ChatAssistantTurn turn) {
        vo.setThinking(turn.getThinking());
        List<MessagePartVO> parts = turn.getParts();
        if (parts != null && !parts.isEmpty()) {
            vo.setParts(parts);
        }
    }
}
