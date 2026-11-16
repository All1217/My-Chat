package com.mychat.apps.papertrade.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.apps.papertrade.PaperTradeAdminService;
import com.mychat.apps.papertrade.risk.entity.dto.PaperStageSummaryRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 阶段总结工具：列表、详情、新建、修改、删除。 */
@Component
public class PaperStageSummaryTools {

    private final PaperTradeAdminService admin;
    private final ObjectMapper objectMapper;

    public PaperStageSummaryTools(PaperTradeAdminService admin, ObjectMapper objectMapper) {
        this.admin = admin;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "列出模拟炒股阶段总结，按创建时间倒序")
    public String listStageSummaries(
            @ToolParam(description = "条数，默认 20，最大 200", required = false) Integer limit) {
        return call(() -> admin.listStageSummaries(limit == null ? 20 : limit));
    }

    @Tool(description = "按 id 查询一条阶段总结")
    public String getStageSummary(@ToolParam(description = "阶段总结 id") String id) {
        return call(() -> admin.getStageSummary(id));
    }

    @Tool(description = "新建阶段总结。body 为 JSON，至少含 summaryText；时间用 ISO-8601")
    public String createStageSummary(@ToolParam(description = "阶段总结 JSON") String body) {
        return call(() -> admin.createStageSummary(PaperToolJson.read(objectMapper, body, PaperStageSummaryRequest.class)));
    }

    @Tool(description = "按 id 修改阶段总结。body 为 JSON，只填要改的字段")
    public String updateStageSummary(
            @ToolParam(description = "阶段总结 id") String id,
            @ToolParam(description = "要更新的字段 JSON") String body) {
        return call(() -> admin.updateStageSummary(id, PaperToolJson.read(objectMapper, body, PaperStageSummaryRequest.class)));
    }

    @Tool(description = "按 id 删除一条阶段总结")
    public String deleteStageSummary(@ToolParam(description = "阶段总结 id") String id) {
        return call(() -> {
            admin.deleteStageSummary(id);
            return Map.of("ok", true);
        });
    }

    private String call(java.util.function.Supplier<Object> action) {
        try {
            return PaperToolJson.write(objectMapper, action.get());
        } catch (Exception e) {
            return PaperToolJson.fail(e);
        }
    }
}
