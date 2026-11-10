package com.mychat.apps.papertrade;

import com.mychat.apps.market.MarketQuoteClient;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import com.mychat.apps.papertrade.entity.dto.PaperOrderRequest;
import com.mychat.apps.papertrade.entity.po.PaperAccount;
import com.mychat.apps.papertrade.entity.po.PaperOrder;
import com.mychat.apps.papertrade.entity.po.PaperPosition;
import com.mychat.apps.papertrade.entity.vo.PaperOrderVO;
import com.mychat.apps.papertrade.mapper.PaperAccountMapper;
import com.mychat.apps.papertrade.mapper.PaperOrderMapper;
import com.mychat.apps.papertrade.mapper.PaperPositionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 市价买成功与现金不足拒单。
 */
@ExtendWith(MockitoExtension.class)
class PaperTradeServiceTest {

    @Mock
    private PaperAccountMapper accountMapper;
    @Mock
    private PaperPositionMapper positionMapper;
    @Mock
    private PaperOrderMapper orderMapper;
    @Mock
    private MarketQuoteClient quoteClient;

    private PaperTradeService service;

    @BeforeEach
    void setUp() {
        PaperTradeProperties props = new PaperTradeProperties();
        props.setInitialCash(new BigDecimal("1000000"));
        service = new PaperTradeService(accountMapper, positionMapper, orderMapper, quoteClient, props);
    }

    @Test
    void buyFillsAndDeductsCash() {
        PaperAccount account = account(new BigDecimal("1000000"));
        when(accountMapper.selectById(PaperAccount.DEFAULT_ID)).thenReturn(account);
        when(quoteClient.fetch("600519", "1M")).thenReturn(quote(1700.0));
        when(positionMapper.selectOne(any())).thenReturn(null);

        PaperOrderRequest req = new PaperOrderRequest();
        req.setSymbol("600519");
        req.setSide("BUY");
        req.setQty(100);

        PaperOrderVO vo = service.placeOrder(req);
        assertEquals(PaperOrder.STATUS_FILLED, vo.getStatus());
        assertEquals(0, new BigDecimal("1700.0000").compareTo(vo.getFillPrice()));
        assertEquals(0, new BigDecimal("830000.0000").compareTo(account.getCash()));

        ArgumentCaptor<PaperPosition> posCap = ArgumentCaptor.forClass(PaperPosition.class);
        verify(positionMapper).insert(posCap.capture());
        assertEquals(100, posCap.getValue().getQty());
        verify(orderMapper).insert(any(PaperOrder.class));
    }

    @Test
    void buyRejectsWhenCashInsufficient() {
        PaperAccount account = account(new BigDecimal("1000"));
        when(accountMapper.selectById(PaperAccount.DEFAULT_ID)).thenReturn(account);
        when(quoteClient.fetch("600519", "1M")).thenReturn(quote(1700.0));

        PaperOrderRequest req = new PaperOrderRequest();
        req.setSymbol("600519");
        req.setSide("BUY");
        req.setQty(100);

        PaperOrderVO vo = service.placeOrder(req);
        assertEquals(PaperOrder.STATUS_REJECTED, vo.getStatus());
        verify(positionMapper, never()).insert(any(PaperPosition.class));
        verify(orderMapper).insert(any(PaperOrder.class));
        assertEquals(0, new BigDecimal("1000").compareTo(account.getCash()));
    }

    @Test
    void cnLotMustBeMultipleOf100() {
        PaperOrderRequest req = new PaperOrderRequest();
        req.setSymbol("600519");
        req.setSide("BUY");
        req.setQty(50);
        assertThrows(IllegalArgumentException.class, () -> service.placeOrder(req));
    }

    private static PaperAccount account(BigDecimal cash) {
        PaperAccount a = new PaperAccount();
        a.setId(PaperAccount.DEFAULT_ID);
        a.setName("默认模拟账户");
        a.setCash(cash);
        a.setInitialCash(new BigDecimal("1000000"));
        return a;
    }

    private static MarketQuoteVO quote(double last) {
        MarketQuoteVO q = new MarketQuoteVO();
        q.setSymbol("600519");
        q.setMarket("CN");
        q.setLastPrice(last);
        return q;
    }
}
