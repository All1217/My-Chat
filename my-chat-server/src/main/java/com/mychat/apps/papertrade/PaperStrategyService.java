package com.mychat.apps.papertrade;

import com.mychat.apps.papertrade.entity.po.PaperStrategy;
import com.mychat.apps.papertrade.entity.vo.PaperStrategyVO;
import com.mychat.apps.papertrade.mapper.PaperStrategyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

/** 默认纸面策略读/写；缺失时从 classpath 种子文件写入。 */
@Service
@RequiredArgsConstructor
public class PaperStrategyService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaperStrategyMapper strategyMapper;

    /** 读取默认策略；不存在则 seed。 */
    public PaperStrategyVO get() {
        return toVo(requireDefault());
    }

    /** 覆盖保存默认策略正文。 */
    @Transactional
    public PaperStrategyVO save(String content) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("策略正文不能为空");
        }
        PaperStrategy row = requireDefault();
        row.setContent(content.trim());
        strategyMapper.updateById(row);
        return toVo(strategyMapper.selectById(PaperStrategy.DEFAULT_ID));
    }

    /** 确保默认策略行存在。 */
    public PaperStrategy requireDefault() {
        PaperStrategy row = strategyMapper.selectById(PaperStrategy.DEFAULT_ID);
        if (row != null && StringUtils.hasText(row.getContent())) {
            return row;
        }
        String seed = loadSeed();
        if (row == null) {
            row = new PaperStrategy();
            row.setId(PaperStrategy.DEFAULT_ID);
            row.setContent(seed);
            strategyMapper.insert(row);
        } else {
            row.setContent(seed);
            strategyMapper.updateById(row);
        }
        return strategyMapper.selectById(PaperStrategy.DEFAULT_ID);
    }

    private static String loadSeed() {
        try {
            ClassPathResource res = new ClassPathResource("paper-strategy-seed.txt");
            return res.getContentAsString(StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "你是一个股票交易员，执行低吸高抛短线波段模拟交易。只输出 JSON 决策数组。";
        }
    }

    private static PaperStrategyVO toVo(PaperStrategy row) {
        PaperStrategyVO vo = new PaperStrategyVO();
        vo.setId(row.getId());
        vo.setContent(row.getContent());
        if (row.getUpdatedAt() != null) {
            vo.setUpdatedAt(row.getUpdatedAt().format(TS));
        }
        return vo;
    }
}
