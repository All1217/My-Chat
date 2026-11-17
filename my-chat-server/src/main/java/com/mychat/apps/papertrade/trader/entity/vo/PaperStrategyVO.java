package com.mychat.apps.papertrade.trader.entity.vo;

import com.mychat.apps.papertrade.risk.entity.vo.PaperRiskSettingsVO;
import lombok.Data;

/** 策略视图：锁定区 + 可编辑区 + 拼接正文 + 风控。 */
@Data
public class PaperStrategyVO {
    private String id;
    /** 拼接后的完整正文（只读产物）。 */
    private String content;
    /** 平台锁定文案（只读）。 */
    private String lockedContent;
    /** 用户可编辑风格。 */
    private String customContent;
    private PaperRiskSettingsVO risk;
    private String updatedAt;
}
