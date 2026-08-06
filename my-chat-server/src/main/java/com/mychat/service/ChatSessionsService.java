package com.mychat.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mychat.common.result.Result;
import com.mychat.entity.dto.ChatSessionsDTO;
import com.mychat.entity.po.ChatSessions;
import com.mychat.vo.ChatSessionVO;

import java.util.List;

public interface ChatSessionsService extends IService<ChatSessions> {
    void addConversation(String conversationId);

    void addConversation(String conversationId, String kbId);

    void addConversation(String conversationId, String kbId, String workDir);

    /** 根据会话ID查询工作目录，可能为 null */
    String getWorkDir(String conversationId);

    /** 根据会话ID查询绑定的知识库 ID，可能为 null */
    String getKbId(String conversationId);

    Result updateConversation(ChatSessionsDTO dto);

    void deleteChatSessionById(String id);

    /**
     * 按知识库 ID 获取会话列表
     * @param kbId null → 返回普通会话（kb_id IS NULL）；具体值 → 返回该知识库的会话
     */
    List<ChatSessionVO> getConversationsByKbId(String kbId);

    /** 获取全部会话（不分类型） */
    List<ChatSessionVO> getAllConversations();
}
