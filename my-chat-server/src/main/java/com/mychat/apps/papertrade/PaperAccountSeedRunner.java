package com.mychat.apps.papertrade;

import com.mychat.apps.papertrade.entity.po.PaperAccount;
import com.mychat.apps.papertrade.mapper.PaperAccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 空表时写入默认模拟账户，保证大厅页可直接连通。
 */
@Component
@Order(20)
public class PaperAccountSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PaperAccountSeedRunner.class);

    private final PaperAccountMapper accountMapper;
    private final PaperTradeProperties properties;

    /** 注入 Mapper 与初始资金配置。 */
    public PaperAccountSeedRunner(PaperAccountMapper accountMapper, PaperTradeProperties properties) {
        this.accountMapper = accountMapper;
        this.properties = properties;
    }

    /** 无账户时 seed 一条 default。 */
    @Override
    public void run(ApplicationArguments args) {
        try {
            Long count = accountMapper.selectCount(null);
            if (count != null && count > 0) {
                return;
            }
            BigDecimal cash = properties.getInitialCash() == null
                    ? new BigDecimal("1000000")
                    : properties.getInitialCash().setScale(4, RoundingMode.HALF_UP);
            PaperAccount seed = new PaperAccount();
            seed.setId(PaperAccount.DEFAULT_ID);
            seed.setName("默认模拟账户");
            seed.setCash(cash);
            seed.setInitialCash(cash);
            accountMapper.insert(seed);
            log.info("已 seed 模拟炒股默认账户 cash={}", cash);
        } catch (Exception e) {
            log.warn("paper_account 不可用（请执行 schema.sql），跳过种子：{}", e.getMessage());
        }
    }
}
