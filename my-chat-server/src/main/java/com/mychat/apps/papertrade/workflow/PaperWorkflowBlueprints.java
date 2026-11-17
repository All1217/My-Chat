package com.mychat.apps.papertrade.workflow;

import com.mychat.apps.papertrade.workflow.entity.vo.PaperWorkflowEdgeVO;
import com.mychat.apps.papertrade.workflow.entity.vo.PaperWorkflowGraphVO;
import com.mychat.apps.papertrade.workflow.entity.vo.PaperWorkflowNodeVO;

import java.util.List;

/**
 * 固定蓝图（与现网 PaperResearchService / PaperAiTraderService 打点对齐）。
 * 节点 id = {@link PaperWorkflowStep#name()}。
 */
public final class PaperWorkflowBlueprints {

    private PaperWorkflowBlueprints() {
    }

    /** 搜集员流水线。 */
    public static PaperWorkflowGraphVO researcher() {
        PaperWorkflowGraphVO g = new PaperWorkflowGraphVO();
        g.setId("researcher");
        g.setTitle("搜集员");
        g.setNodes(List.of(
                node(PaperWorkflowStep.START_RUN, "启动搜集任务", "start", 0, 80),
                node(PaperWorkflowStep.DISCOVER_CANDIDATES, "发现候选股票", "process", 220, 80),
                node(PaperWorkflowStep.VALIDATE_MARKET_DATA, "校验行情", "process", 440, 80),
                node(PaperWorkflowStep.AI_COLLECT_AND_ANALYZE, "AI 搜集分析", "ai", 660, 80),
                node(PaperWorkflowStep.VALIDATE_CARDS, "校验卡片", "process", 880, 80),
                node(PaperWorkflowStep.UPSERT_NOTES, "写入研究卡片", "process", 1100, 80),
                node(PaperWorkflowStep.FINISH_RUN, "结束", "end", 1320, 80)
        ));
        g.setEdges(chain(
                PaperWorkflowStep.START_RUN,
                PaperWorkflowStep.DISCOVER_CANDIDATES,
                PaperWorkflowStep.VALIDATE_MARKET_DATA,
                PaperWorkflowStep.AI_COLLECT_AND_ANALYZE,
                PaperWorkflowStep.VALIDATE_CARDS,
                PaperWorkflowStep.UPSERT_NOTES,
                PaperWorkflowStep.FINISH_RUN
        ));
        return g;
    }

    /** 交易员流水线（不含未单独打点的 RISK/EXECUTE）。 */
    public static PaperWorkflowGraphVO trader() {
        PaperWorkflowGraphVO g = new PaperWorkflowGraphVO();
        g.setId("trader");
        g.setTitle("交易员");
        g.setNodes(List.of(
                node(PaperWorkflowStep.START_RUN, "启动交易轮", "start", 0, 80),
                node(PaperWorkflowStep.PROCESS_RULE_EXITS, "规则卖出", "process", 240, 80),
                node(PaperWorkflowStep.BUILD_CONTEXT, "组装上下文", "process", 480, 80),
                node(PaperWorkflowStep.AI_REVIEW_AND_DECIDE, "AI 复盘决策", "ai", 720, 80),
                node(PaperWorkflowStep.VALIDATE_DECISIONS, "校验决策", "process", 960, 80),
                node(PaperWorkflowStep.SETTLE_AND_FINISH, "结算结束", "end", 1200, 80)
        ));
        g.setEdges(chain(
                PaperWorkflowStep.START_RUN,
                PaperWorkflowStep.PROCESS_RULE_EXITS,
                PaperWorkflowStep.BUILD_CONTEXT,
                PaperWorkflowStep.AI_REVIEW_AND_DECIDE,
                PaperWorkflowStep.VALIDATE_DECISIONS,
                PaperWorkflowStep.SETTLE_AND_FINISH
        ));
        return g;
    }

    private static PaperWorkflowNodeVO node(PaperWorkflowStep step, String label, String type,
                                            double x, double y) {
        PaperWorkflowNodeVO n = new PaperWorkflowNodeVO();
        n.setId(step.name());
        n.setLabel(label);
        n.setType(type);
        n.setX(x);
        n.setY(y);
        return n;
    }

    private static List<PaperWorkflowEdgeVO> chain(PaperWorkflowStep... steps) {
        return java.util.stream.IntStream.range(0, steps.length - 1)
                .mapToObj(i -> {
                    PaperWorkflowEdgeVO e = new PaperWorkflowEdgeVO();
                    e.setId(steps[i].name() + "__" + steps[i + 1].name());
                    e.setSource(steps[i].name());
                    e.setTarget(steps[i + 1].name());
                    return e;
                })
                .toList();
    }
}
