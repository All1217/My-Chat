package com.mychat.apps.papertrade.tools;

import com.mychat.apps.papertrade.PaperTradeAdminService;
import com.mychat.apps.papertrade.research.PaperResearchService;
import com.mychat.apps.papertrade.research.entity.dto.PaperResearchNoteRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 研究卡片工具：列表、按代码查、新建、修改、删除、调价值等级。 */
@Component
public class PaperResearchNoteTools {

    private final PaperTradeAdminService admin;
    private final PaperResearchService researchService;

    public PaperResearchNoteTools(PaperTradeAdminService admin,
                                  @Lazy PaperResearchService researchService) {
        this.admin = admin;
        this.researchService = researchService;
    }

    @Tool(description = "列出模拟炒股研究卡片，含未留下的，按更新时间倒序")
    public String listResearchNotes(
            @ToolParam(description = "条数，默认 20，最大 200", required = false) Integer limit) {
        return call(() -> admin.listResearchNotes(limit == null ? 20 : limit));
    }

    @Tool(description = "按股票代码查询一张研究卡片")
    public String getResearchNote(@ToolParam(description = "股票代码，如 600519") String symbol) {
        return call(() -> admin.getResearchNote(symbol));
    }

    @Tool(description = "新建研究卡片。body 为 JSON，必须含 symbol")
    public String createResearchNote(@ToolParam(description = "研究卡片 JSON") String body) {
        return call(() -> admin.createResearchNote(PaperToolJson.read(body, PaperResearchNoteRequest.class)));
    }

    @Tool(description = "按股票代码修改研究卡片。body 为 JSON，只填要改的字段")
    public String updateResearchNote(
            @ToolParam(description = "股票代码") String symbol,
            @ToolParam(description = "要更新的字段 JSON") String body) {
        return call(() -> admin.updateResearchNote(symbol, PaperToolJson.read(body, PaperResearchNoteRequest.class)));
    }

    @Tool(description = "按股票代码删除一张研究卡片")
    public String deleteResearchNote(@ToolParam(description = "股票代码") String symbol) {
        return call(() -> {
            admin.deleteResearchNote(symbol);
            return Map.of("ok", true);
        });
    }

    @Tool(description = "调整研究卡片价值等级。仅允许 1应该买入、2继续观察、3移出票仓；禁止改已持仓(0)")
    public String updateValueTier(
            @ToolParam(description = "股票代码") String symbol,
            @ToolParam(description = "目标等级：1、2 或 3") Integer tier) {
        return call(() -> researchService.updateValueTierByTool(symbol, tier));
    }

    private String call(java.util.function.Supplier<Object> action) {
        try {
            return PaperToolJson.write(action.get());
        } catch (Exception e) {
            return PaperToolJson.fail(e);
        }
    }
}
