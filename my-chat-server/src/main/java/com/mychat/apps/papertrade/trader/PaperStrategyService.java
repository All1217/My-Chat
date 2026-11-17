package com.mychat.apps.papertrade.trader;

import com.mychat.apps.papertrade.risk.PaperRiskSettingsService;
import com.mychat.apps.papertrade.risk.entity.po.PaperRiskSettings;
import com.mychat.apps.papertrade.trader.entity.dto.PaperStrategyRequest;
import com.mychat.apps.papertrade.trader.entity.po.PaperStrategy;
import com.mychat.apps.papertrade.trader.entity.vo.PaperStrategyVO;
import com.mychat.apps.papertrade.trader.mapper.PaperStrategyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 默认策略：锁定区由风控渲染，用户只改 custom；content 为拼接结果。 */
@Service
@RequiredArgsConstructor
public class PaperStrategyService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String LEGACY_PREFIX = "你是一个股票交易员，根据平台提供的行情";

    private final PaperStrategyMapper strategyMapper;
    private final PaperRiskSettingsService riskSettingsService;
    private final PaperLockedStrategyRenderer lockedRenderer;

    /** 读取默认策略；不存在则 seed 并拼接。 */
    @Transactional
    public PaperStrategyVO get() {
        return toVo(requireDefault());
    }

    /** 保存可编辑风格与风控；重算锁定区与拼接 content。 */
    @Transactional
    public PaperStrategyVO save(PaperStrategyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String custom = request.getCustomContent();
        if (!StringUtils.hasText(custom) && StringUtils.hasText(request.getContent())) {
            // 兼容旧前端误传 content：当作 custom（仍不覆盖锁定）
            custom = request.getContent();
        }
        if (!StringUtils.hasText(custom)) {
            throw new IllegalArgumentException("可编辑策略风格不能为空");
        }
        if (request.getRisk() != null) {
            riskSettingsService.update(request.getRisk());
        }
        PaperRiskSettings risk = riskSettingsService.requireSettings();
        String locked = lockedRenderer.render(risk);
        String composed = lockedRenderer.compose(locked, custom.trim());

        PaperStrategy row = requireDefault();
        row.setCustomContent(custom.trim());
        row.setContent(composed);
        row.setUpdatedAt(LocalDateTime.now());
        strategyMapper.updateById(row);
        return toVo(strategyMapper.selectById(PaperStrategy.DEFAULT_ID));
    }

    /** @deprecated 请用 {@link #save(PaperStrategyRequest)}。 */
    @Deprecated
    @Transactional
    public PaperStrategyVO save(String content) {
        PaperStrategyRequest req = new PaperStrategyRequest();
        req.setCustomContent(content);
        return save(req);
    }

    /** 确保默认策略行存在，并保证 custom/content 与锁定区一致。 */
    @Transactional
    public PaperStrategy requireDefault() {
        riskSettingsService.seedIfAbsent();
        PaperRiskSettings risk = riskSettingsService.requireSettings();
        String locked = lockedRenderer.render(risk);

        PaperStrategy row = strategyMapper.selectById(PaperStrategy.DEFAULT_ID);
        if (row != null && StringUtils.hasText(row.getContent()) && isLegacy(row.getContent())) {
            row.setCustomContent(loadCustomSeed());
            row.setContent(lockedRenderer.compose(locked, row.getCustomContent()));
            row.setUpdatedAt(LocalDateTime.now());
            strategyMapper.updateById(row);
            return strategyMapper.selectById(PaperStrategy.DEFAULT_ID);
        }
        if (row == null) {
            row = new PaperStrategy();
            row.setId(PaperStrategy.DEFAULT_ID);
            row.setCustomContent(loadCustomSeed());
            row.setContent(lockedRenderer.compose(locked, row.getCustomContent()));
            row.setUpdatedAt(LocalDateTime.now());
            strategyMapper.insert(row);
            return strategyMapper.selectById(PaperStrategy.DEFAULT_ID);
        }
        if (!StringUtils.hasText(row.getCustomContent())) {
            // 旧行无 custom：用风格种子初始化，避免把整段旧 seed 再叠进锁定区
            row.setCustomContent(loadCustomSeed());
            row.setContent(lockedRenderer.compose(locked, row.getCustomContent()));
            row.setUpdatedAt(LocalDateTime.now());
            strategyMapper.updateById(row);
            return strategyMapper.selectById(PaperStrategy.DEFAULT_ID);
        }
        // 每次读取用最新 risk 重算锁定段，避免配置改后文案陈旧
        String composed = lockedRenderer.compose(locked, row.getCustomContent());
        if (!composed.equals(row.getContent())) {
            row.setContent(composed);
            row.setUpdatedAt(LocalDateTime.now());
            strategyMapper.updateById(row);
        }
        return strategyMapper.selectById(PaperStrategy.DEFAULT_ID);
    }

    private PaperStrategyVO toVo(PaperStrategy row) {
        PaperRiskSettings risk = riskSettingsService.requireSettings();
        String locked = lockedRenderer.render(risk);
        PaperStrategyVO vo = new PaperStrategyVO();
        vo.setId(row.getId());
        vo.setLockedContent(locked);
        vo.setCustomContent(row.getCustomContent());
        vo.setContent(lockedRenderer.compose(locked, row.getCustomContent()));
        vo.setRisk(riskSettingsService.toVo(risk));
        if (row.getUpdatedAt() != null) {
            vo.setUpdatedAt(row.getUpdatedAt().format(TS));
        }
        return vo;
    }

    private static boolean isLegacy(String content) {
        String text = content.trim();
        return text.startsWith(LEGACY_PREFIX) && !text.contains("【一、身份与风格】");
    }

    private static String loadCustomSeed() {
        try {
            ClassPathResource res = new ClassPathResource("paper-strategy-custom-seed.txt");
            return res.getContentAsString(StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "【一、身份与风格】\n你是低吸高抛短线波段模拟交易员。";
        }
    }
}
