package com.mychat.apps.market;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.apps.market.entity.vo.MarketStrategyVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/** 投资策略读写本地 JSON，仅在保存时调用 Agent 评价。 */
@Slf4j
@Service
public class MarketStrategyService {

    /** Spring Boot 4 容器是 Jackson 3，这里自建 Jackson 2 写文件。 */
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MarketStrategyProperties properties;
    private final ChatClient chatClient;

    /** 注入落盘路径与无工具 Agent。 */
    public MarketStrategyService(
            MarketStrategyProperties properties,
            @Qualifier("agentWorkflowChatClient") ChatClient chatClient) {
        this.properties = properties;
        this.chatClient = chatClient;
    }

    /** 读文件；不存在或损坏当作空，不调模型。 */
    public MarketStrategyVO load() {
        return toVo(readFile());
    }

    /** 用评价改进策略并覆盖落盘，再评价一次。 */
    public MarketStrategyVO optimizeAndOverwrite() {
        StrategyFile current = readFile();
        String strategyText = current.strategyText == null ? "" : current.strategyText.trim();
        String evaluation = current.evaluation == null ? "" : current.evaluation.trim();
        if (!StringUtils.hasText(strategyText) || !StringUtils.hasText(evaluation)) {
            throw new IllegalArgumentException("请先保存策略并等待评价后再优化");
        }
        try {
            current.strategyText = optimize(strategyText, evaluation);
            current.updatedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            writeFile(current);
            current.evaluation = evaluate(current.strategyText);
            current.updatedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            writeFile(current);
            return toVo(current);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("策略优化失败: {}", e.getMessage());
            throw new IllegalArgumentException("策略优化失败，请稍后重试");
        }
    }

    /** 落盘策略；非空才调一次评价，空文本清空评价且不调模型。 */
    public MarketStrategyVO saveAndEvaluate(String rawText) {
        String strategyText = rawText == null ? "" : rawText.trim();
        StrategyFile current = readFile();
        current.strategyText = strategyText;
        current.updatedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        if (!StringUtils.hasText(strategyText)) {
            current.evaluation = "";
            writeFile(current);
            return toVo(current);
        }
        // 先把策略写出去，评价失败时正文仍在
        writeFile(current);
        try {
            current.evaluation = evaluate(strategyText);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("策略评价失败: {}", e.getMessage());
            throw new IllegalArgumentException("策略评价失败，请稍后重试");
        }
        current.updatedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        writeFile(current);
        return toVo(current);
    }

    /** 用无工具 ChatClient 生成中文评价。 */
    String evaluate(String strategyText) {
        String text = chatClient.prompt()
                .system("""
                        你是投资分析助手。根据用户写下的投资策略给出评价：风险、仓位纪律、适用场景。
                        用简洁中文，不要保证收益，明确这不是投资建议。不要使用 Markdown 标题。
                        """)
                .user(strategyText)
                .call()
                .content();
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("模型未返回有效评价");
        }
        return text.trim();
    }

    /** 按现有策略与评价生成一版可直接落盘的优化策略。 */
    String optimize(String strategyText, String evaluation) {
        String user = """
                现有投资策略：
                %s

                对该策略的评价（供改进时参考）：
                %s

                请输出一版优化后的完整投资策略，直接覆盖原文使用。
                """.formatted(strategyText, evaluation);
        String text = chatClient.prompt()
                .system("""
                        你是投资策略改写助手。保留用户原意，补上仓位、止损、持有周期等可执行规则。
                        只输出优化后的策略正文，不要标题、Markdown 或额外说明。不要保证收益，这不是投资建议。
                        """)
                .user(user)
                .call()
                .content();
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("模型未返回有效策略");
        }
        return text.trim();
    }

    /** 读盘；缺文件或 JSON 坏了返回空记录。 */
    private StrategyFile readFile() {
        Path path = Path.of(properties.getStrategyFile());
        if (!Files.isRegularFile(path)) {
            return new StrategyFile();
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            if (!StringUtils.hasText(json)) {
                return new StrategyFile();
            }
            StrategyFile file = objectMapper.readValue(json, StrategyFile.class);
            return file == null ? new StrategyFile() : file;
        } catch (IOException e) {
            log.warn("读取策略文件失败 path={}: {}", path, e.getMessage());
            return new StrategyFile();
        }
    }

    /** 写盘，必要时创建父目录。 */
    private void writeFile(StrategyFile file) {
        Path path = Path.of(properties.getStrategyFile());
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(file);
            Files.writeString(path, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("写入策略文件失败 path={}: {}", path, e.getMessage());
            throw new IllegalArgumentException("无法保存投资策略");
        }
    }

    /** 转成接口 VO，空字段补成空串。 */
    private static MarketStrategyVO toVo(StrategyFile file) {
        MarketStrategyVO vo = new MarketStrategyVO();
        vo.setStrategyText(file.strategyText == null ? "" : file.strategyText);
        vo.setEvaluation(file.evaluation == null ? "" : file.evaluation);
        vo.setUpdatedAt(file.updatedAt == null ? "" : file.updatedAt);
        return vo;
    }

    /** JSON 落盘结构。 */
    static class StrategyFile {
        public String strategyText = "";
        public String evaluation = "";
        public String updatedAt = "";
    }
}
