package com.mychat.agent.capability;

import com.mychat.tools.WebSearchTools;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** MARKET_ANALYSIS 并行网页研究能力测试。 */
class WebResearchCapabilityTest {

    /** 最多并行四个查询，并按输入顺序汇总。 */
    @Test
    void researchesDistinctQueriesInOrderWithCap() {
        WebSearchTools tools = mock(WebSearchTools.class);
        AtomicInteger counter = new AtomicInteger();
        when(tools.searchWeb(anyString())).thenAnswer(inv ->
                "证据-" + counter.incrementAndGet() + ":" + inv.getArgument(0));
        WebResearchCapability capability = new WebResearchCapability(tools);

        WebResearchCapability.ResearchResult result = capability.research(List.of(
                "A", "B", "A", "C", "D", "E"));

        assertThat(result.queries()).containsExactly("A", "B", "C", "D");
        assertThat(result.render()).contains("证据-", "【研究主题 1】A", "【研究主题 4】D");
    }
}
