package com.mychat.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mychat.entity.po.ChatSessionSummary;
import com.mychat.mapper.ChatSessionSummaryMapper;
import com.mychat.service.ChatSessionSummaryService;
import org.springframework.stereotype.Service;

@Service
public class ChatSessionSummaryServiceImpl
        extends ServiceImpl<ChatSessionSummaryMapper, ChatSessionSummary>
        implements ChatSessionSummaryService {
}
