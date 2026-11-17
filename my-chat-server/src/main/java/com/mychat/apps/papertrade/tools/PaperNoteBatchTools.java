package com.mychat.apps.papertrade.tools;

import com.mychat.apps.papertrade.research.PaperResearchService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/** 搜集员批量落库研究卡片（写工具）。 */
@Component
public class PaperNoteBatchTools {

    private final PaperResearchService researchService;

    public PaperNoteBatchTools(@Lazy PaperResearchService researchService) {
        this.researchService = researchService;
    }

    @Tool(description = "批量写入/更新研究卡片。cardsJson 为搜集员输出的 cards 数组或 ResearchCardBatch JSON；runId 为当前搜集轮次")
    public String upsertResearchCards(
            @ToolParam(description = "搜集轮次 ID") String runId,
            @ToolParam(description = "卡片 JSON 数组或含 cards 字段的对象") String cardsJson) {
        try {
            return PaperToolJson.write(researchService.upsertCardsFromTool(runId, cardsJson));
        } catch (Exception e) {
            return PaperToolJson.fail(e);
        }
    }
}
