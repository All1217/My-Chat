package com.mychat.agent.context;

import com.mychat.agent.api.AgentScenarioId;
import com.mychat.agent.api.AgentScenarioProfile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.EnumMap;
import java.util.Map;

/** 为 Phase 5 场景构建互相隔离的最小 Context 文本。 */
@Component
public class ScenarioContextFactory {

    private final Map<AgentScenarioId, String> policies;

    /** 注册各场景不可互换的系统约束。 */
    public ScenarioContextFactory() {
        Map<AgentScenarioId, String> values = new EnumMap<>(AgentScenarioId.class);
        values.put(AgentScenarioId.MARKET_ANALYSIS,
                "并行收集市场证据；结论必须引用观察，缺少证据时明确说明。");
        values.put(AgentScenarioId.ROLEPLAY,
                "严格保持角色设定；不得调用文件、搜索或 MCP 工具。");
        values.put(AgentScenarioId.INTERVIEWER,
                "按 INTRO、TECHNICAL、SCENARIO、WRAP_UP 阶段推进；评分必须可人工复核。");
        values.put(AgentScenarioId.PAPER_RESEARCH,
                "研究给定候选；终局用 upsertResearchCards 落库；禁止下单。");
        values.put(AgentScenarioId.PAPER_TRADER,
                "生成决策后优先 recordAiDecisions / placePaperOrder；规则卖出走平台。");
        values.put(AgentScenarioId.PAPER_WAREHOUSE_ANALYST,
                "仅对观察票深搜；用 updateValueTier 调到 1/2/3；禁止改 0、禁止下单。");
        this.policies = Map.copyOf(values);
    }

    /** 渲染 Profile、场景规则、rubric 和用户输入。 */
    public String render(
            AgentScenarioProfile profile,
            String input,
            String rubric,
            Map<String, Object> attributes) {
        StringBuilder text = new StringBuilder()
                .append("【场景】").append(profile.scenarioId()).append('\n')
                .append("【上下文策略】").append(profile.contextPolicy()).append('\n')
                .append("【系统约束】")
                .append(policies.getOrDefault(profile.scenarioId(), "遵守 Profile 白名单。"))
                .append('\n');
        if (StringUtils.hasText(rubric)) {
            text.append("【评价标准】").append(rubric.trim()).append('\n');
        }
        if (attributes != null && !attributes.isEmpty()) {
            text.append("【结构化属性】").append(attributes).append('\n');
        }
        return text.append("【用户目标】").append(input).toString();
    }
}
