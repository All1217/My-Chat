package com.mychat.apps.papertrade.trader.entity.dto;

import com.mychat.apps.papertrade.risk.entity.dto.PaperRiskSettingsRequest;
import lombok.Data;

/** 保存策略：只接受可编辑风格 + 结构化风控；不接受整段 content 覆盖。 */
@Data
public class PaperStrategyRequest {
    /** @deprecated 已忽略；请用 customContent。 */
    @Deprecated
    private String content;
    private String customContent;
    private PaperRiskSettingsRequest risk;
}
