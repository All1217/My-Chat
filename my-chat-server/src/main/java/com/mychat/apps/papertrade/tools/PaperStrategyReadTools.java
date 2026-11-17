package com.mychat.apps.papertrade.tools;

import com.mychat.apps.papertrade.trader.PaperStrategyService;
import com.mychat.apps.papertrade.trader.entity.po.PaperStrategy;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 默认投资策略只读工具。 */
@Component
public class PaperStrategyReadTools {

    private final PaperStrategyService strategyService;

    public PaperStrategyReadTools(PaperStrategyService strategyService) {
        this.strategyService = strategyService;
    }

    /** 读取当前默认策略正文。 */
    @Tool(description = "读取当前默认投资策略正文，只读")
    public String readDefaultStrategy() {
        try {
            PaperStrategy strategy = strategyService.requireDefault();
            return PaperToolJson.write(Map.of(
                    "id", strategy.getId(),
                    "content", strategy.getContent() == null ? "" : strategy.getContent()));
        } catch (Exception e) {
            return PaperToolJson.fail(e);
        }
    }
}
