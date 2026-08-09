package com.mychat.service;

import com.mychat.vo.OrchestrateStepVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 弱最终答复检测与 observation 合成兜底（不依赖 Spring 上下文）。
 */
class AgentOrchestratorFinalAnswerTest {

    @Test
    void metaOutlineIsWeakAndGetsComposed() {
        String outline = "结合知识库定义和搜索到的实践案例，向用户完整作答：首先给出Java三大特性，接着提供案例代码。";
        String kb = """
                ## 一、封装
                定义：隐藏细节。
                """;
        String search = """
                ## 案例
                ```java
                class Animal {}
                ```
                """;
        List<OrchestrateStepVO> steps = List.of(
                new OrchestrateStepVO(1, "retrieve_kb", "r", "i1", kb),
                new OrchestrateStepVO(2, "search", "r", "i2", search)
        );

        int obsChars = kb.length() + search.length();
        assertTrue(AgentOrchestratorService.isWeakFinalAnswer(outline, obsChars, steps));

        String answer = AgentOrchestratorService.resolveFinalAnswer(
                "根据知识库回答Java三大特性并联网搜索案例", outline, steps);
        assertTrue(answer.contains("知识库要点"));
        assertTrue(answer.contains("联网补充"));
        assertTrue(answer.contains("封装") || answer.contains("Animal"));
        assertFalse(answer.contains("向用户完整作答"));
    }

    @Test
    void strongFinishKeptAsIs() {
        String strong = """
                ## Java 三大特性

                - **封装**：隐藏实现
                - **继承**：复用父类
                - **多态**：同一接口多种实现

                ```java
                class Demo {}
                ```
                """;
        List<OrchestrateStepVO> steps = List.of(
                new OrchestrateStepVO(1, "retrieve_kb", "r", "i", "少量摘录")
        );
        assertFalse(AgentOrchestratorService.isWeakFinalAnswer(strong, 10, steps));
        String answer = AgentOrchestratorService.resolveFinalAnswer("问三大特性", strong, steps);
        assertTrue(answer.startsWith("## Java 三大特性"));
    }
}
