package com.mychat.apps.papertrade.tools;

import com.mychat.apps.papertrade.PaperTradeAdminService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** AI 决策工具：只读列表与详情。 */
@Component
public class PaperDecisionTools {

    private final PaperTradeAdminService admin;

    public PaperDecisionTools(PaperTradeAdminService admin) {
        this.admin = admin;
    }

    @Tool(description = "列出模拟炒股 AI 决策，按创建时间倒序。不能修改或删除")
    public String listAiDecisions(
            @ToolParam(description = "条数，默认 20，最大 200", required = false) Integer limit) {
        return call(() -> admin.listDecisions(limit == null ? 20 : limit));
    }

    @Tool(description = "按 id 查询一条 AI 决策。不能修改或删除")
    public String getAiDecision(@ToolParam(description = "决策 id") String id) {
        return call(() -> admin.getDecision(id));
    }

    private String call(java.util.function.Supplier<Object> action) {
        try {
            return PaperToolJson.write(action.get());
        } catch (Exception e) {
            return PaperToolJson.fail(e);
        }
    }
}
