package com.mychat.apps.papertrade;

import com.mychat.apps.papertrade.entity.dto.PaperOrderRequest;
import com.mychat.apps.papertrade.entity.vo.PaperAccountVO;
import com.mychat.apps.papertrade.entity.vo.PaperOrderVO;
import com.mychat.apps.papertrade.entity.vo.PaperPositionVO;
import com.mychat.common.result.Result;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 模拟炒股 API：账户、持仓、市价单（虚拟盘，非实盘）。 */
@RestController
@RequestMapping("/ai/apps/paper-trade")
@AllArgsConstructor
public class PaperTradeController {

    private final PaperTradeService paperTradeService;

    /** 默认模拟账户。 */
    @GetMapping("/account")
    public Result<PaperAccountVO> account() {
        return Result.ok(paperTradeService.getAccount());
    }

    /** 当前持仓。 */
    @GetMapping("/positions")
    public Result<List<PaperPositionVO>> positions() {
        return Result.ok(paperTradeService.listPositions());
    }

    /** 最近订单。 */
    @GetMapping("/orders")
    public Result<List<PaperOrderVO>> orders() {
        return Result.ok(paperTradeService.listOrders());
    }

    /** 市价买入或卖出；拒单时仍返回订单体（status=REJECTED）。 */
    @PostMapping("/orders")
    public Result<PaperOrderVO> place(@RequestBody PaperOrderRequest request) {
        try {
            PaperOrderVO order = paperTradeService.placeOrder(request);
            if ("REJECTED".equals(order.getStatus())) {
                return Result.fail(400, order.getRejectReason() == null ? "下单被拒绝" : order.getRejectReason());
            }
            return Result.ok(order);
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /** 清空持仓与订单，现金回到初始值。 */
    @PostMapping("/account/reset")
    public Result<PaperAccountVO> reset() {
        return Result.ok(paperTradeService.resetAccount());
    }
}
