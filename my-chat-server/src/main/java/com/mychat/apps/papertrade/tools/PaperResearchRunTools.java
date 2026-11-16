package com.mychat.apps.papertrade.tools;

import com.mychat.apps.papertrade.PaperTradeAdminService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** 搜集轮次工具：只读列表与详情。 */
@Component
public class PaperResearchRunTools {

    private final PaperTradeAdminService admin;

    public PaperResearchRunTools(PaperTradeAdminService admin) {
        this.admin = admin;
    }

    @Tool(description = "列出模拟炒股搜集轮次，按创建时间倒序。不能修改或删除")
    public String listResearchRuns(
            @ToolParam(description = "条数，默认 20，最大 200", required = false) Integer limit) {
        return call(() -> admin.listResearchRuns(limit == null ? 20 : limit));
    }

    @Tool(description = "按 id 查询一轮搜集。不能修改或删除")
    public String getResearchRun(@ToolParam(description = "搜集轮次 id") String id) {
        return call(() -> admin.getResearchRun(id));
    }

    private String call(java.util.function.Supplier<Object> action) {
        try {
            return PaperToolJson.write(action.get());
        } catch (Exception e) {
            return PaperToolJson.fail(e);
        }
    }
}
