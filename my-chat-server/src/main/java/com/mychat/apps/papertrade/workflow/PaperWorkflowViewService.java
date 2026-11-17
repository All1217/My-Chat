package com.mychat.apps.papertrade.workflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.apps.papertrade.research.entity.po.PaperResearchRun;
import com.mychat.apps.papertrade.research.mapper.PaperResearchRunMapper;
import com.mychat.apps.papertrade.trader.entity.po.PaperAiRun;
import com.mychat.apps.papertrade.trader.mapper.PaperAiRunMapper;
import com.mychat.apps.papertrade.workflow.entity.vo.PaperWorkflowAgentStepVO;
import com.mychat.apps.papertrade.workflow.entity.vo.PaperWorkflowGraphVO;
import com.mychat.apps.papertrade.workflow.entity.vo.PaperWorkflowLatestRunVO;
import com.mychat.apps.papertrade.workflow.entity.vo.PaperWorkflowOverviewVO;
import com.mychat.apps.papertrade.workflow.entity.vo.PaperWorkflowStepStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 只读工作流视图：固定蓝图 + 最近一轮 framework_trace。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperWorkflowViewService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaperResearchRunMapper researchRunMapper;
    private final PaperAiRunMapper aiRunMapper;
    private final ObjectMapper objectMapper;

    /** 搜集员 + 交易员总览。 */
    public PaperWorkflowOverviewVO overview() {
        PaperWorkflowOverviewVO out = new PaperWorkflowOverviewVO();
        PaperWorkflowGraphVO researcher = PaperWorkflowBlueprints.researcher();
        researcher.setLatest(latestResearch());
        PaperWorkflowGraphVO trader = PaperWorkflowBlueprints.trader();
        trader.setLatest(latestTrader());
        out.setGraphs(List.of(researcher, trader));
        return out;
    }

    /** 最近一次搜集轮。 */
    private PaperWorkflowLatestRunVO latestResearch() {
        PaperResearchRun run = researchRunMapper.selectOne(new LambdaQueryWrapper<PaperResearchRun>()
                .orderByDesc(PaperResearchRun::getCreatedAt)
                .last("LIMIT 1"));
        if (run == null) {
            return null;
        }
        return toLatest(run.getId(), run.getStatus(), run.getCreatedAt(), run.getFinishedAt(),
                run.getErrorMessage(), run.getFrameworkTrace());
    }

    /** 最近一次交易员轮。 */
    private PaperWorkflowLatestRunVO latestTrader() {
        PaperAiRun run = aiRunMapper.selectOne(new LambdaQueryWrapper<PaperAiRun>()
                .orderByDesc(PaperAiRun::getCreatedAt)
                .last("LIMIT 1"));
        if (run == null) {
            return null;
        }
        return toLatest(run.getId(), run.getStatus(), run.getCreatedAt(), run.getFinishedAt(),
                run.getErrorMessage(), run.getFrameworkTrace());
    }

    /** 组装 latest + 解析 domainSteps / agent.steps。 */
    private PaperWorkflowLatestRunVO toLatest(String id, String status, LocalDateTime createdAt,
                                              LocalDateTime finishedAt, String errorMessage,
                                              String frameworkTrace) {
        PaperWorkflowLatestRunVO vo = new PaperWorkflowLatestRunVO();
        vo.setRunId(id);
        vo.setStatus(status);
        vo.setCreatedAt(fmt(createdAt));
        vo.setFinishedAt(fmt(finishedAt));
        vo.setErrorMessage(errorMessage);
        parseTrace(frameworkTrace, vo);
        return vo;
    }

    /** 解析 framework_trace JSON。 */
    private void parseTrace(String frameworkTrace, PaperWorkflowLatestRunVO vo) {
        if (!StringUtils.hasText(frameworkTrace)) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(frameworkTrace);
            if (root.hasNonNull("agentRunId")) {
                vo.setAgentRunId(root.get("agentRunId").asText());
            }
            List<PaperWorkflowStepStatusVO> steps = new ArrayList<>();
            JsonNode domain = root.path("domainSteps");
            if (domain.isArray()) {
                for (JsonNode n : domain) {
                    PaperWorkflowStepStatusVO s = new PaperWorkflowStepStatusVO();
                    s.setStep(text(n, "step"));
                    s.setStatus(text(n, "status"));
                    s.setSummary(text(n, "summary"));
                    s.setError(text(n, "error"));
                    steps.add(s);
                }
            }
            vo.setDomainSteps(steps);

            JsonNode agent = root.path("agent");
            if (agent.isObject()) {
                if (!StringUtils.hasText(vo.getAgentRunId()) && agent.hasNonNull("agentRunId")) {
                    vo.setAgentRunId(agent.get("agentRunId").asText());
                }
                List<PaperWorkflowAgentStepVO> agentSteps = new ArrayList<>();
                JsonNode arr = agent.path("steps");
                if (arr.isArray()) {
                    for (JsonNode n : arr) {
                        PaperWorkflowAgentStepVO a = new PaperWorkflowAgentStepVO();
                        if (n.has("index") && n.get("index").canConvertToInt()) {
                            a.setIndex(n.get("index").asInt());
                        }
                        a.setAction(text(n, "action"));
                        a.setStatus(text(n, "status"));
                        a.setObservation(text(n, "observation"));
                        agentSteps.add(a);
                    }
                }
                vo.setAgentSteps(agentSteps);
            }
        } catch (Exception e) {
            log.debug("解析 framework_trace 失败: {}", e.getMessage());
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? null : t.format(TS);
    }
}
