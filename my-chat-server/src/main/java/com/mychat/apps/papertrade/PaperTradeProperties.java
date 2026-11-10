package com.mychat.apps.papertrade;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 模拟炒股配置（前缀 {@code app.paper-trade}）。 */
@Data
@Component
@ConfigurationProperties(prefix = "app.paper-trade")
public class PaperTradeProperties {

    /** 默认账户初始现金。 */
    private BigDecimal initialCash = new BigDecimal("1000000");
}
