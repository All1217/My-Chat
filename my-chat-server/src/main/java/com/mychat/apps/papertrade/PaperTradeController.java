package com.mychat.apps.papertrade;

import com.mychat.apps.papertrade.ledger.PaperTradeService;
import com.mychat.apps.papertrade.ledger.entity.dto.PaperOrderRequest;
import com.mychat.apps.papertrade.research.PaperResearchService;
import com.mychat.apps.papertrade.risk.PaperStageService;
import com.mychat.apps.papertrade.trader.PaperAiTraderService;
import com.mychat.apps.papertrade.trader.PaperStrategyService;
import com.mychat.apps.papertrade.trader.entity.dto.PaperStrategyRequest;
import com.mychat.apps.papertrade.ledger.entity.vo.PaperAccountVO;
import com.mychat.apps.papertrade.trader.entity.vo.PaperAiDecisionVO;
import com.mychat.apps.papertrade.trader.entity.vo.PaperAiRunVO;
import com.mychat.apps.papertrade.trader.entity.vo.PaperAiSubmitVO;
import com.mychat.apps.papertrade.ledger.entity.vo.PaperOrderVO;
import com.mychat.apps.papertrade.ledger.entity.vo.PaperPositionVO;
import com.mychat.apps.papertrade.research.entity.po.PaperResearchRun;
import com.mychat.apps.papertrade.research.entity.vo.PaperResearchNoteVO;
import com.mychat.apps.papertrade.trader.entity.vo.PaperStrategyVO;
import com.mychat.common.result.Result;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 模拟炒股 API：账户、持仓、市价单、策略与 AI 轮次（虚拟盘，非实盘）。 */
@RestController
@RequestMapping("/ai/apps/paper-trade")
@AllArgsConstructor
public class PaperTradeController {

    private final PaperTradeService paperTradeService;
    private final PaperStrategyService strategyService;
    private final PaperAiTraderService aiTraderService;
    private final PaperResearchService researchService;
    private final PaperStageService stageService;

    /** 默认模拟账户。 */
    @GetMapping("/account")
    public Result<PaperAccountVO> account() {
        return Result.ok(stageService.attachLatest(paperTradeService.getAccount()));
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
        return Result.ok(stageService.attachLatest(paperTradeService.resetAccount()));
    }

    /** 读取默认 AI 策略。 */
    @GetMapping("/strategy")
    public Result<PaperStrategyVO> strategy() {
        return Result.ok(strategyService.get());
    }

    /** 保存默认 AI 策略。 */
    @PutMapping("/strategy")
    public Result<PaperStrategyVO> saveStrategy(@RequestBody PaperStrategyRequest request) {
        try {
            return Result.ok(strategyService.save(request == null ? null : request.getContent()));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /** 提交 AI 一轮（异步）。 */
    @PostMapping("/ai/run")
    public Result<PaperAiSubmitVO> aiRun() {
        return Result.ok(aiTraderService.submitRun());
    }

    /** 最近 AI 轮次。 */
    @GetMapping("/ai/runs")
    public Result<List<PaperAiRunVO>> aiRuns(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(aiTraderService.listRuns(limit));
    }

    /** AI 轮次详情。 */
    @GetMapping("/ai/runs/{id}")
    public Result<PaperAiRunVO> aiRunDetail(@PathVariable String id) {
        try {
            return Result.ok(aiTraderService.getRun(id));
        } catch (IllegalArgumentException e) {
            return Result.fail(404, e.getMessage());
        }
    }

    /** 最近决策列表。 */
    @GetMapping("/ai/decisions")
    public Result<List<PaperAiDecisionVO>> decisions(@RequestParam(defaultValue = "30") int limit) {
        return Result.ok(aiTraderService.listRecentDecisions(limit));
    }

    /** 确认待执行决策。 */
    @PostMapping("/ai/decisions/{id}/confirm")
    public Result<PaperAiDecisionVO> confirm(@PathVariable String id) {
        try {
            return Result.ok(aiTraderService.confirm(id));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /** 忽略待确认决策。 */
    @PostMapping("/ai/decisions/{id}/dismiss")
    public Result<PaperAiDecisionVO> dismiss(@PathVariable String id) {
        try {
            return Result.ok(aiTraderService.dismiss(id));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /** 已留下的研究卡片。 */
    @GetMapping("/research/notes")
    public Result<List<PaperResearchNoteVO>> researchNotes(@RequestParam(defaultValue = "40") int limit) {
        return Result.ok(researchService.listKept(limit));
    }

    /** 提交搜集一轮（异步）。 */
    @PostMapping("/research/run")
    public Result<PaperAiSubmitVO> researchRun() {
        return Result.ok(researchService.submitRun());
    }

    /** 搜集轮次状态。 */
    @GetMapping("/research/runs/{id}")
    public Result<PaperResearchRun> researchRunDetail(@PathVariable String id) {
        try {
            return Result.ok(researchService.getRun(id));
        } catch (IllegalArgumentException e) {
            return Result.fail(404, e.getMessage());
        }
    }
}
