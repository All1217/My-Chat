package com.mychat.apps.papertrade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mychat.agent.persistence.AgentRunRepository;
import com.mychat.apps.papertrade.ledger.PaperTradeService;
import com.mychat.apps.papertrade.ledger.entity.vo.PaperAccountVO;
import com.mychat.apps.papertrade.research.entity.po.PaperResearchNote;
import com.mychat.apps.papertrade.research.mapper.PaperResearchNoteMapper;
import com.mychat.apps.papertrade.research.mapper.PaperResearchRunMapper;
import com.mychat.apps.papertrade.risk.mapper.PaperStageSummaryMapper;
import com.mychat.apps.papertrade.trader.mapper.PaperAiDecisionMapper;
import com.mychat.apps.papertrade.trader.mapper.PaperAiRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 注销阶段训练保留自选研究卡片。 */
@ExtendWith(MockitoExtension.class)
class PaperTradeAdminWipeTest {

    @Mock
    private PaperTradeService tradeService;
    @Mock
    private PaperStageSummaryMapper stageSummaryMapper;
    @Mock
    private PaperAiDecisionMapper decisionMapper;
    @Mock
    private PaperAiRunMapper aiRunMapper;
    @Mock
    private PaperResearchRunMapper researchRunMapper;
    @Mock
    private PaperResearchNoteMapper researchNoteMapper;
    @Mock
    private AgentRunRepository agentRunRepository;

    private PaperTradeAdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new PaperTradeAdminService(
                tradeService,
                stageSummaryMapper,
                decisionMapper,
                aiRunMapper,
                researchRunMapper,
                researchNoteMapper,
                agentRunRepository);
    }

    @Test
    void wipeKeepsWatchlistNotesAndDemotesHeldTier() {
        PaperResearchNote watch = new PaperResearchNote();
        watch.setSymbol("600519");
        watch.setWatchlist(true);
        watch.setValueTier(0);
        when(researchNoteMapper.selectList(any())).thenReturn(List.of(watch));
        when(tradeService.resetAccount()).thenReturn(new PaperAccountVO());

        adminService.wipeTraining();

        verify(researchNoteMapper).delete(any());
        // 自选行走 update：持仓等级 0 → 2；不会整表按 id 删自选
        ArgumentCaptor<PaperResearchNote> upd = ArgumentCaptor.forClass(PaperResearchNote.class);
        verify(researchNoteMapper).updateById(upd.capture());
        assertEquals(2, upd.getValue().getValueTier());
        assertEquals("600519", upd.getValue().getSymbol());
        verify(tradeService).resetAccount();
    }
}
