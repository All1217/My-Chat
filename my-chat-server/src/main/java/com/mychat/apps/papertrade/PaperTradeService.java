package com.mychat.apps.papertrade;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mychat.apps.market.MarketQuoteClient;
import com.mychat.apps.market.MarketSymbolParser;
import com.mychat.apps.market.ResolvedSymbol;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import com.mychat.apps.papertrade.entity.dto.PaperOrderRequest;
import com.mychat.apps.papertrade.entity.po.PaperAccount;
import com.mychat.apps.papertrade.entity.po.PaperOrder;
import com.mychat.apps.papertrade.entity.po.PaperPosition;
import com.mychat.apps.papertrade.entity.vo.PaperAccountVO;
import com.mychat.apps.papertrade.entity.vo.PaperOrderVO;
import com.mychat.apps.papertrade.entity.vo.PaperPositionVO;
import com.mychat.apps.papertrade.mapper.PaperAccountMapper;
import com.mychat.apps.papertrade.mapper.PaperOrderMapper;
import com.mychat.apps.papertrade.mapper.PaperPositionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 模拟炒股账本：查询、市价撮合、重置。
 */
@Service
public class PaperTradeService {

    private static final Logger log = LoggerFactory.getLogger(PaperTradeService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int ORDER_LIMIT = 50;

    private final PaperAccountMapper accountMapper;
    private final PaperPositionMapper positionMapper;
    private final PaperOrderMapper orderMapper;
    private final MarketQuoteClient quoteClient;
    private final PaperTradeProperties properties;

    /** 注入账本 Mapper 与行情客户端。 */
    public PaperTradeService(PaperAccountMapper accountMapper,
                             PaperPositionMapper positionMapper,
                             PaperOrderMapper orderMapper,
                             MarketQuoteClient quoteClient,
                             PaperTradeProperties properties) {
        this.accountMapper = accountMapper;
        this.positionMapper = positionMapper;
        this.orderMapper = orderMapper;
        this.quoteClient = quoteClient;
        this.properties = properties;
    }

    /** 返回默认模拟账户；不存在则按配置创建。 */
    public PaperAccountVO getAccount() {
        return toAccountVo(requireAccount());
    }

    /** 列出默认账户持仓（市值用最新收盘粗算，拉行情失败则留空市值）。 */
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
            vo.setAvgCost(row.getAvgCost());
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

        // 1) 解析代码与手数规则
        ResolvedSymbol resolved = MarketSymbolParser.parse(request.getSymbol());
        if (resolved.cn() && qty % 100 != 0) {
            throw new IllegalArgumentException("A 股数量须为 100 股整数倍");
        }

        PaperAccount account = requireAccount();
        String orderId = UUID.randomUUID().toString().replace("-", "");

        // 2) 拉行情作成交价（失败落 REJECTED，不抛，避免事务回滚抹掉拒单）
        BigDecimal price;
        try {
            price = lastClose(resolved.symbol());
        } catch (IllegalArgumentException e) {
            return toOrderVo(rejectOrder(orderId, account.getId(), side, resolved, qty, e.getMessage()));
        }

        // 3) 买：验资；卖：验券
        BigDecimal notional = price.multiply(BigDecimal.valueOf(qty)).setScale(4, RoundingMode.HALF_UP);
        if (PaperOrder.SIDE_BUY.equals(side)) {
            if (account.getCash().compareTo(notional) < 0) {
                return toOrderVo(rejectOrder(orderId, account.getId(), side, resolved, qty,
                        "现金不足，需要 " + notional + "，可用 " + account.getCash()));
            }
            applyBuy(account, resolved, qty, price, notional);
        } else {
            PaperPosition pos = findPosition(account.getId(), resolved.symbol());
            if (pos == null || pos.getQty() < qty) {
                int have = pos == null ? 0 : pos.getQty();
                return toOrderVo(rejectOrder(orderId, account.getId(), side, resolved, qty,
                        "持仓不足，持有 " + have + "，卖出 " + qty));
            }
            applySell(account, pos, qty, notional);
        }

        // 4) 写成交单
        PaperOrder filled = new PaperOrder();
        filled.setId(orderId);
        filled.setAccountId(account.getId());
        filled.setSide(side);
        filled.setSymbol(resolved.symbol());
        filled.setMarket(resolved.market());
        filled.setQty(qty);
        filled.setFillPrice(price);
        filled.setStatus(PaperOrder.STATUS_FILLED);
        orderMapper.insert(filled);
        log.info("模拟成交 {} {} x{} @{}", side, resolved.symbol(), qty, price);
        return toOrderVo(filled);
    }

    /** 清空持仓与订单，现金回到 initial_cash。 */
    @Transactional
    public PaperAccountVO resetAccount() {
        PaperAccount account = requireAccount();
        positionMapper.delete(new LambdaQueryWrapper<PaperPosition>()
                .eq(PaperPosition::getAccountId, account.getId()));
        orderMapper.delete(new LambdaQueryWrapper<PaperOrder>()
                .eq(PaperOrder::getAccountId, account.getId()));
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
    PaperAccount requireAccount() {
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
    BigDecimal lastClose(String symbol) {
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

    /** 规范化买卖方向。 */
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

    /** 买入：扣现金、增持仓/调均价。 */
    private void applyBuy(PaperAccount account, ResolvedSymbol resolved, int qty,
                          BigDecimal price, BigDecimal notional) {
        account.setCash(account.getCash().subtract(notional).setScale(4, RoundingMode.HALF_UP));
        accountMapper.updateById(account);

        PaperPosition pos = findPosition(account.getId(), resolved.symbol());
        if (pos == null) {
            pos = new PaperPosition();
            pos.setId(UUID.randomUUID().toString().replace("-", ""));
            pos.setAccountId(account.getId());
            pos.setSymbol(resolved.symbol());
            pos.setMarket(resolved.market());
            pos.setQty(qty);
            pos.setAvgCost(price);
            positionMapper.insert(pos);
            return;
        }
        BigDecimal oldCost = pos.getAvgCost().multiply(BigDecimal.valueOf(pos.getQty()));
        int newQty = pos.getQty() + qty;
        BigDecimal avg = oldCost.add(notional)
                .divide(BigDecimal.valueOf(newQty), 4, RoundingMode.HALF_UP);
        pos.setQty(newQty);
        pos.setAvgCost(avg);
        positionMapper.updateById(pos);
    }

    /** 卖出：加现金、减持仓（清零则删行）。 */
    private void applySell(PaperAccount account, PaperPosition pos, int qty, BigDecimal notional) {
        account.setCash(account.getCash().add(notional).setScale(4, RoundingMode.HALF_UP));
        accountMapper.updateById(account);
        int left = pos.getQty() - qty;
        if (left == 0) {
            positionMapper.deleteById(pos.getId());
        } else {
            pos.setQty(left);
            positionMapper.updateById(pos);
        }
    }

    /** 按账户+代码查持仓。 */
    private PaperPosition findPosition(String accountId, String symbol) {
        return positionMapper.selectOne(new LambdaQueryWrapper<PaperPosition>()
                .eq(PaperPosition::getAccountId, accountId)
                .eq(PaperPosition::getSymbol, symbol)
                .last("LIMIT 1"));
    }

    /** 落一条拒单记录。 */
    private PaperOrder rejectOrder(String id, String accountId, String side,
                                   ResolvedSymbol resolved, int qty, String reason) {
        PaperOrder rejected = new PaperOrder();
        rejected.setId(id);
        rejected.setAccountId(accountId);
        rejected.setSide(side);
        rejected.setSymbol(resolved.symbol());
        rejected.setMarket(resolved.market());
        rejected.setQty(qty);
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
        vo.setStatus(row.getStatus());
        vo.setRejectReason(row.getRejectReason());
        if (row.getCreatedAt() != null) {
            vo.setCreatedAt(row.getCreatedAt().format(TS));
        }
        return vo;
    }
}
