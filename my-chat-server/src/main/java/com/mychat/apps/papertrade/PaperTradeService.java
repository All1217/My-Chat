package com.mychat.apps.papertrade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mychat.apps.market.EtfPremiumClient;
import com.mychat.apps.market.MarketQuoteClient;
import com.mychat.apps.market.MarketSymbolParser;
import com.mychat.apps.market.ResolvedSymbol;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import com.mychat.apps.papertrade.entity.dto.PaperOrderRequest;
import com.mychat.apps.papertrade.entity.po.PaperAccount;
import com.mychat.apps.papertrade.entity.po.PaperCooldown;
import com.mychat.apps.papertrade.entity.po.PaperOrder;
import com.mychat.apps.papertrade.entity.po.PaperPosition;
import com.mychat.apps.papertrade.entity.vo.PaperAccountVO;
import com.mychat.apps.papertrade.entity.vo.PaperOrderVO;
import com.mychat.apps.papertrade.entity.vo.PaperPositionVO;
import com.mychat.apps.papertrade.mapper.PaperAccountMapper;
import com.mychat.apps.papertrade.mapper.PaperCooldownMapper;
import com.mychat.apps.papertrade.mapper.PaperOrderMapper;
import com.mychat.apps.papertrade.mapper.PaperPositionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 模拟炒股账本：查询、市价撮合（含费用/T+1/冷却）、重置。
 */
@Service
public class PaperTradeService {

    private static final Logger log = LoggerFactory.getLogger(PaperTradeService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int ORDER_LIMIT = 50;

    private final PaperAccountMapper accountMapper;
    private final PaperPositionMapper positionMapper;
    private final PaperOrderMapper orderMapper;
    private final PaperCooldownMapper cooldownMapper;
    private final MarketQuoteClient quoteClient;
    private final PaperTradeProperties properties;

    /** 注入账本 Mapper 与行情客户端。 */
    public PaperTradeService(PaperAccountMapper accountMapper,
                             PaperPositionMapper positionMapper,
                             PaperOrderMapper orderMapper,
                             PaperCooldownMapper cooldownMapper,
                             MarketQuoteClient quoteClient,
                             PaperTradeProperties properties) {
        this.accountMapper = accountMapper;
        this.positionMapper = positionMapper;
        this.orderMapper = orderMapper;
        this.cooldownMapper = cooldownMapper;
        this.quoteClient = quoteClient;
        this.properties = properties;
    }

    /** 返回默认模拟账户；不存在则按配置创建。 */
    public PaperAccountVO getAccount() {
        return toAccountVo(requireAccount());
    }

    /** 列出默认账户持仓（市值用最新收盘粗算）。 */
    public List<PaperPositionVO> listPositions() {
        PaperAccount account = requireAccount();
        List<PaperPosition> rows = positionMapper.selectList(new LambdaQueryWrapper<PaperPosition>()
                .eq(PaperPosition::getAccountId, account.getId())
                .orderByAsc(PaperPosition::getSymbol));
        List<PaperPositionVO> out = new ArrayList<>();
        for (PaperPosition row : rows) {
            PaperPositionVO vo = new PaperPositionVO();
            vo.setSymbol(row.getSymbol());
            vo.setMarket(row.getMarket());
            vo.setQty(row.getQty());
            vo.setAvailableQty(row.getAvailableQty() == null ? 0 : row.getAvailableQty());
            vo.setAvgCost(row.getAvgCost());
            vo.setTakeProfitPct(row.getTakeProfitPct());
            vo.setStopLossPct(row.getStopLossPct());
            vo.setMaxHoldDays(row.getMaxHoldDays());
            if (row.getOpenedAt() != null) {
                vo.setOpenedAt(row.getOpenedAt().format(TS));
            }
            try {
                BigDecimal last = lastClose(row.getSymbol());
                vo.setMarketValue(last.multiply(BigDecimal.valueOf(row.getQty()))
                        .setScale(4, RoundingMode.HALF_UP));
            } catch (Exception e) {
                log.debug("持仓市值行情失败 symbol={}: {}", row.getSymbol(), e.getMessage());
            }
            out.add(vo);
        }
        return out;
    }

    /** 实体持仓列表（AI / 出场扫描用）。 */
    public List<PaperPosition> listPositionEntities() {
        PaperAccount account = requireAccount();
        return positionMapper.selectList(new LambdaQueryWrapper<PaperPosition>()
                .eq(PaperPosition::getAccountId, account.getId())
                .orderByAsc(PaperPosition::getSymbol));
    }

    /** 最近订单（新→旧）。 */
    public List<PaperOrderVO> listOrders() {
        PaperAccount account = requireAccount();
        List<PaperOrder> rows = orderMapper.selectList(new LambdaQueryWrapper<PaperOrder>()
                .eq(PaperOrder::getAccountId, account.getId())
                .orderByDesc(PaperOrder::getCreatedAt)
                .last("LIMIT " + ORDER_LIMIT));
        List<PaperOrderVO> out = new ArrayList<>();
        for (PaperOrder row : rows) {
            out.add(toOrderVo(row));
        }
        return out;
    }

    /**
     * AI 轮开始前：把非当日买入转为可卖（MVP T+1 释放）。
     */
    @Transactional
    public void releaseT1() {
        PaperAccount account = requireAccount();
        LocalDate today = LocalDate.now();
        List<PaperPosition> rows = positionMapper.selectList(new LambdaQueryWrapper<PaperPosition>()
                .eq(PaperPosition::getAccountId, account.getId()));
        for (PaperPosition pos : rows) {
            int qty = pos.getQty() == null ? 0 : pos.getQty();
            int avail = pos.getAvailableQty() == null ? 0 : pos.getAvailableQty();
            if (qty <= 0 || avail >= qty) {
                continue;
            }
            LocalDateTime opened = pos.getOpenedAt();
            // 无开仓时间或开仓日早于今天 → 全部可卖
            if (opened == null || opened.toLocalDate().isBefore(today)) {
                pos.setAvailableQty(qty);
                positionMapper.updateById(pos);
            }
        }
    }

    /** 当前仍在冷却中的标的代码集合。 */
    public Set<String> activeCooldownSymbols() {
        PaperAccount account = requireAccount();
        LocalDate today = LocalDate.now();
        List<PaperCooldown> rows = cooldownMapper.selectList(new LambdaQueryWrapper<PaperCooldown>()
                .eq(PaperCooldown::getAccountId, account.getId())
                .ge(PaperCooldown::getUntilDate, today));
        Set<String> out = new HashSet<>();
        for (PaperCooldown row : rows) {
            out.add(row.getSymbol());
        }
        return out;
    }

    /** 冷却期列表（供上下文）。 */
    public List<PaperCooldown> listActiveCooldowns() {
        PaperAccount account = requireAccount();
        LocalDate today = LocalDate.now();
        return cooldownMapper.selectList(new LambdaQueryWrapper<PaperCooldown>()
                .eq(PaperCooldown::getAccountId, account.getId())
                .ge(PaperCooldown::getUntilDate, today)
                .orderByAsc(PaperCooldown::getSymbol));
    }

    /**
     * 市价下单：东财最新收盘成交；资金/持仓/行情失败写 REJECTED 并返回订单体。
     */
    @Transactional
    public PaperOrderVO placeOrder(PaperOrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        String side = normalizeSide(request.getSide());
        Integer qty = request.getQty();
        if (qty == null || qty <= 0) {
            throw new IllegalArgumentException("数量必须为正整数");
        }

        ResolvedSymbol resolved = MarketSymbolParser.parse(request.getSymbol());
        if (resolved.cn() && qty % 100 != 0) {
            throw new IllegalArgumentException("A 股数量须为 100 股整数倍");
        }

        PaperAccount account = requireAccount();
        String orderId = UUID.randomUUID().toString().replace("-", "");
        String source = StringUtils.hasText(request.getSource())
                ? request.getSource().trim().toUpperCase()
                : PaperOrder.SOURCE_MANUAL;

        BigDecimal price;
        try {
            price = lastClose(resolved.symbol());
        } catch (IllegalArgumentException e) {
            return toOrderVo(rejectOrder(orderId, account.getId(), side, resolved, qty, source,
                    request.getDecisionId(), e.getMessage()));
        }

        BigDecimal notional = price.multiply(BigDecimal.valueOf(qty)).setScale(4, RoundingMode.HALF_UP);
        BigDecimal fee = calcFee(side, resolved, notional);

        if (PaperOrder.SIDE_BUY.equals(side)) {
            if (isInCooldown(account.getId(), resolved.symbol())) {
                return toOrderVo(rejectOrder(orderId, account.getId(), side, resolved, qty, source,
                        request.getDecisionId(), "标的仍在冷却期"));
            }
            BigDecimal need = notional.add(fee);
            if (account.getCash().compareTo(need) < 0) {
                return toOrderVo(rejectOrder(orderId, account.getId(), side, resolved, qty, source,
                        request.getDecisionId(),
                        "现金不足，需要 " + need + "（含费用），可用 " + account.getCash()));
            }
            applyBuy(account, resolved, qty, price, notional, fee, request);
        } else {
            PaperPosition pos = findPosition(account.getId(), resolved.symbol());
            int avail = pos == null || pos.getAvailableQty() == null ? 0 : pos.getAvailableQty();
            if (pos == null || avail < qty) {
                int have = pos == null ? 0 : avail;
                return toOrderVo(rejectOrder(orderId, account.getId(), side, resolved, qty, source,
                        request.getDecisionId(),
                        "可卖不足（T+1），可卖 " + have + "，卖出 " + qty));
            }
            applySell(account, pos, qty, notional, fee, request.getCooldownDays());
        }

        PaperOrder filled = new PaperOrder();
        filled.setId(orderId);
        filled.setAccountId(account.getId());
        filled.setSide(side);
        filled.setSymbol(resolved.symbol());
        filled.setMarket(resolved.market());
        filled.setQty(qty);
        filled.setFillPrice(price);
        filled.setFee(fee);
        filled.setSource(source);
        filled.setDecisionId(request.getDecisionId());
        filled.setStatus(PaperOrder.STATUS_FILLED);
        orderMapper.insert(filled);
        log.info("模拟成交 {} {} x{} @{} fee={} source={}",
                side, resolved.symbol(), qty, price, fee, source);
        return toOrderVo(filled);
    }

    /** 清空持仓、订单与冷却，现金回到 initial_cash。 */
    @Transactional
    public PaperAccountVO resetAccount() {
        PaperAccount account = requireAccount();
        positionMapper.delete(new LambdaQueryWrapper<PaperPosition>()
                .eq(PaperPosition::getAccountId, account.getId()));
        orderMapper.delete(new LambdaQueryWrapper<PaperOrder>()
                .eq(PaperOrder::getAccountId, account.getId()));
        cooldownMapper.delete(new LambdaQueryWrapper<PaperCooldown>()
                .eq(PaperCooldown::getAccountId, account.getId()));
        BigDecimal cash = account.getInitialCash() != null
                ? account.getInitialCash()
                : (properties.getInitialCash() == null
                ? new BigDecimal("1000000")
                : properties.getInitialCash());
        account.setCash(cash.setScale(4, RoundingMode.HALF_UP));
        accountMapper.updateById(account);
        return toAccountVo(account);
    }

    /** 确保默认账户存在。 */
    public PaperAccount requireAccount() {
        PaperAccount account = accountMapper.selectById(PaperAccount.DEFAULT_ID);
        if (account != null) {
            return account;
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
        return seed;
    }

    /** 取东财 1M 行情最新收盘价。 */
    public BigDecimal lastClose(String symbol) {
        MarketQuoteVO quote = quoteClient.fetch(symbol, "1M");
        if (quote.getLastPrice() != null && quote.getLastPrice() > 0) {
            return BigDecimal.valueOf(quote.getLastPrice()).setScale(4, RoundingMode.HALF_UP);
        }
        if (quote.getHistory() != null && !quote.getHistory().isEmpty()) {
            Double close = quote.getHistory().get(quote.getHistory().size() - 1).getClose();
            if (close != null && close > 0) {
                return BigDecimal.valueOf(close).setScale(4, RoundingMode.HALF_UP);
            }
        }
        throw new IllegalArgumentException("无法取得最新收盘价：" + symbol);
    }

    static String normalizeSide(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("请指定买卖方向 BUY 或 SELL");
        }
        String side = raw.trim().toUpperCase();
        if (!PaperOrder.SIDE_BUY.equals(side) && !PaperOrder.SIDE_SELL.equals(side)) {
            throw new IllegalArgumentException("买卖方向须为 BUY 或 SELL");
        }
        return side;
    }

    private BigDecimal calcFee(String side, ResolvedSymbol resolved, BigDecimal notional) {
        BigDecimal commissionRate = properties.getCommissionRate() == null
                ? BigDecimal.ZERO : properties.getCommissionRate();
        BigDecimal fee = notional.multiply(commissionRate).setScale(4, RoundingMode.HALF_UP);
        if (PaperOrder.SIDE_SELL.equals(side) && resolved.cn()) {
            BigDecimal stamp = properties.getStampDutyRate() == null
                    ? BigDecimal.ZERO : properties.getStampDutyRate();
            fee = fee.add(notional.multiply(stamp).setScale(4, RoundingMode.HALF_UP));
        }
        return fee;
    }

    private void applyBuy(PaperAccount account, ResolvedSymbol resolved, int qty,
                          BigDecimal price, BigDecimal notional, BigDecimal fee,
                          PaperOrderRequest request) {
        account.setCash(account.getCash().subtract(notional).subtract(fee)
                .setScale(4, RoundingMode.HALF_UP));
        accountMapper.updateById(account);

        PaperPosition pos = findPosition(account.getId(), resolved.symbol());
        LocalDateTime now = LocalDateTime.now();
        if (pos == null) {
            pos = new PaperPosition();
            pos.setId(UUID.randomUUID().toString().replace("-", ""));
            pos.setAccountId(account.getId());
            pos.setSymbol(resolved.symbol());
            pos.setMarket(resolved.market());
            pos.setQty(qty);
            pos.setAvailableQty(0);
            pos.setAvgCost(price);
            pos.setOpenedAt(now);
            applyExitTargets(pos, request);
            positionMapper.insert(pos);
            return;
        }
        BigDecimal oldCost = pos.getAvgCost().multiply(BigDecimal.valueOf(pos.getQty()));
        int newQty = pos.getQty() + qty;
        BigDecimal avg = oldCost.add(notional)
                .divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP);
        pos.setQty(newQty);
        // 当日买入不可卖：可卖不变
        if (pos.getAvailableQty() == null) {
            pos.setAvailableQty(0);
        }
        pos.setAvgCost(avg);
        pos.setOpenedAt(now);
        applyExitTargets(pos, request);
        positionMapper.updateById(pos);
    }

    private static void applyExitTargets(PaperPosition pos, PaperOrderRequest request) {
        if (request.getTakeProfitPct() != null) {
            pos.setTakeProfitPct(request.getTakeProfitPct());
        }
        if (request.getStopLossPct() != null) {
            pos.setStopLossPct(request.getStopLossPct());
        }
        if (request.getMaxHoldDays() != null) {
            pos.setMaxHoldDays(request.getMaxHoldDays());
        }
    }

    private void applySell(PaperAccount account, PaperPosition pos, int qty,
                           BigDecimal notional, BigDecimal fee, Integer cooldownDays) {
        account.setCash(account.getCash().add(notional).subtract(fee)
                .setScale(4, RoundingMode.HALF_UP));
        accountMapper.updateById(account);
        int left = pos.getQty() - qty;
        int availLeft = (pos.getAvailableQty() == null ? 0 : pos.getAvailableQty()) - qty;
        if (left == 0) {
            positionMapper.deleteById(pos.getId());
        } else {
            pos.setQty(left);
            pos.setAvailableQty(Math.max(0, availLeft));
            positionMapper.updateById(pos);
        }
        writeCooldown(account.getId(), pos.getSymbol(), cooldownDays);
    }

    private void writeCooldown(String accountId, String symbol, Integer cooldownDays) {
        int days = cooldownDays != null && cooldownDays > 0
                ? cooldownDays
                : (EtfPremiumClient.looksLikeEtf(symbol, null) ? 1 : 2);
        LocalDate until = LocalDate.now().plusDays(days);
        PaperCooldown existing = cooldownMapper.selectOne(new LambdaQueryWrapper<PaperCooldown>()
                .eq(PaperCooldown::getAccountId, accountId)
                .eq(PaperCooldown::getSymbol, symbol)
                .last("LIMIT 1"));
        if (existing == null) {
            PaperCooldown row = new PaperCooldown();
            row.setAccountId(accountId);
            row.setSymbol(symbol);
            row.setUntilDate(until);
            cooldownMapper.insert(row);
        } else {
            existing.setUntilDate(until);
            cooldownMapper.update(existing, new LambdaQueryWrapper<PaperCooldown>()
                    .eq(PaperCooldown::getAccountId, accountId)
                    .eq(PaperCooldown::getSymbol, symbol));
        }
    }

    private boolean isInCooldown(String accountId, String symbol) {
        PaperCooldown row = cooldownMapper.selectOne(new LambdaQueryWrapper<PaperCooldown>()
                .eq(PaperCooldown::getAccountId, accountId)
                .eq(PaperCooldown::getSymbol, symbol)
                .ge(PaperCooldown::getUntilDate, LocalDate.now())
                .last("LIMIT 1"));
        return row != null;
    }

    private PaperPosition findPosition(String accountId, String symbol) {
        return positionMapper.selectOne(new LambdaQueryWrapper<PaperPosition>()
                .eq(PaperPosition::getAccountId, accountId)
                .eq(PaperPosition::getSymbol, symbol)
                .last("LIMIT 1"));
    }

    private PaperOrder rejectOrder(String id, String accountId, String side,
                                   ResolvedSymbol resolved, int qty, String source,
                                   String decisionId, String reason) {
        PaperOrder rejected = new PaperOrder();
        rejected.setId(id);
        rejected.setAccountId(accountId);
        rejected.setSide(side);
        rejected.setSymbol(resolved.symbol());
        rejected.setMarket(resolved.market());
        rejected.setQty(qty);
        rejected.setFee(BigDecimal.ZERO);
        rejected.setSource(source);
        rejected.setDecisionId(decisionId);
        rejected.setStatus(PaperOrder.STATUS_REJECTED);
        rejected.setRejectReason(reason == null ? "拒单" : truncate(reason, 500));
        orderMapper.insert(rejected);
        return rejected;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static PaperAccountVO toAccountVo(PaperAccount account) {
        PaperAccountVO vo = new PaperAccountVO();
        vo.setId(account.getId());
        vo.setName(account.getName());
        vo.setCash(account.getCash());
        vo.setInitialCash(account.getInitialCash());
        return vo;
    }

    private static PaperOrderVO toOrderVo(PaperOrder row) {
        PaperOrderVO vo = new PaperOrderVO();
        vo.setId(row.getId());
        vo.setSide(row.getSide());
        vo.setSymbol(row.getSymbol());
        vo.setMarket(row.getMarket());
        vo.setQty(row.getQty());
        vo.setFillPrice(row.getFillPrice());
        vo.setFee(row.getFee());
        vo.setSource(row.getSource());
        vo.setDecisionId(row.getDecisionId());
        vo.setStatus(row.getStatus());
        vo.setRejectReason(row.getRejectReason());
        if (row.getCreatedAt() != null) {
            vo.setCreatedAt(row.getCreatedAt().format(TS));
        }
        return vo;
    }
}
