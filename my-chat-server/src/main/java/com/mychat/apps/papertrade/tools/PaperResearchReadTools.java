package com.mychat.apps.papertrade.tools;

import com.mychat.apps.papertrade.research.PaperMacroFeed;
import com.mychat.apps.papertrade.research.PaperStoryFeed;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 搜集员按需拉取公告/新闻/板块材料的只读工具。 */
@Component
public class PaperResearchReadTools {

    private final PaperStoryFeed feed;
    private final PaperMacroFeed macroFeed;

    public PaperResearchReadTools(PaperStoryFeed feed, PaperMacroFeed macroFeed) {
        this.feed = feed;
        this.macroFeed = macroFeed;
    }

    /** 读取个股公告标题。 */
    @Tool(description = "读取个股近期公告标题与链接，不下单不写库")
    public String getAnnouncements(@ToolParam(description = "股票代码") String symbol) {
        return call(() -> Map.of("symbol", symbol, "items", feed.announcements(symbol)));
    }

    /** 读取个股新闻标题。 */
    @Tool(description = "读取个股近期新闻标题与链接，不下单不写库")
    public String getNews(@ToolParam(description = "股票代码") String symbol) {
        return call(() -> Map.of("symbol", symbol, "items", feed.news(symbol)));
    }

    /** 按名称检索板块/主题材料。 */
    @Tool(description = "按标的名称检索相关板块或主题简报")
    public String getSectorBriefs(@ToolParam(description = "股票或 ETF 名称") String name) {
        return call(() -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", name);
            out.put("items", macroFeed.sectorBriefs(name));
            return out;
        });
    }

    private String call(java.util.function.Supplier<Object> action) {
        try {
            return PaperToolJson.write(action.get());
        } catch (Exception e) {
            return PaperToolJson.fail(e);
        }
    }
}
