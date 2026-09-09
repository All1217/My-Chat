package com.mychat.apps.market;

import com.mychat.apps.market.entity.dto.MarketForecastRequest;
import com.mychat.apps.market.entity.dto.MarketStrategyRequest;
import com.mychat.apps.market.entity.dto.MarketWatchlistRequest;
import com.mychat.apps.market.entity.vo.MarketForecastVO;
import com.mychat.apps.market.entity.vo.MarketQuoteVO;
import com.mychat.apps.market.entity.vo.MarketStrategyVO;
import com.mychat.apps.market.entity.vo.MarketWatchlistAlertVO;
import com.mychat.apps.market.entity.vo.MarketWatchlistVO;
import com.mychat.common.result.Result;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 股市分析 API：同步行情、异步预测、策略读写与评价。 */
@Slf4j
@RestController
@RequestMapping("/ai/apps/market")
@AllArgsConstructor
public class MarketController {

    private final MarketForecastService marketForecastService;
    private final MarketStrategyService marketStrategyService;
    private final MarketWatchlistService marketWatchlistService;

    /** 拉取基本信息与历史 K 线，不写库。 */
    @GetMapping("/quote")
    public Result<MarketQuoteVO> quote(
            @RequestParam String symbol,
            @RequestParam String range) {
        try {
            return Result.ok(marketForecastService.quote(symbol, range));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /** 固化历史快照并提交预测任务，HTTP 不等待 Agent。 */
    @PostMapping("/forecast")
    public Result<MarketForecastVO> forecast(@RequestBody MarketForecastRequest request) {
        try {
            if (request == null) {
                throw new IllegalArgumentException("请求体不能为空");
            }
            // #region agent log
            dbg("A", "MarketController.forecast", "forecast entry",
                    "{\"symbol\":\"" + String.valueOf(request.getSymbol())
                            + "\",\"range\":\"" + String.valueOf(request.getRange()) + "\"}");
            // #endregion
            return Result.ok(marketForecastService.submit(request.getSymbol(), request.getRange()));
        } catch (IllegalArgumentException e) {
            // #region agent log
            dbg("C", "MarketController.forecast", "forecast fail",
                    "{\"msg\":\"" + String.valueOf(e.getMessage()).replace('"', '\'') + "\"}");
            // #endregion
            return Result.fail(400, e.getMessage());
        }
    }

    /** 查询一次预测（预测未完成时 forecast 为空）。 */
    @GetMapping("/forecast/{id}")
    public Result<MarketForecastVO> get(@PathVariable String id) {
        try {
            return Result.ok(marketForecastService.get(id));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /** 读取已保存策略与评价，不调模型。 */
    @GetMapping("/strategy")
    public Result<MarketStrategyVO> getStrategy() {
        return Result.ok(marketStrategyService.load());
    }

    /** 保存策略；有正文才评价一次。 */
    @PostMapping("/strategy")
    public Result<MarketStrategyVO> saveStrategy(@RequestBody MarketStrategyRequest request) {
        try {
            String text = request == null ? "" : request.getStrategyText();
            return Result.ok(marketStrategyService.saveAndEvaluate(text));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /** 用评价优化策略并覆盖旧正文。 */
    @PostMapping("/strategy/optimize")
    public Result<MarketStrategyVO> optimizeStrategy() {
        try {
            return Result.ok(marketStrategyService.optimizeAndOverwrite());
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /** 读取自选股，不调模型。 */
    @GetMapping("/watchlist")
    public Result<MarketWatchlistVO> getWatchlist() {
        return Result.ok(marketWatchlistService.load());
    }

    /** 覆盖保存自选股并清空提醒缓存。 */
    @PutMapping("/watchlist")
    public Result<MarketWatchlistVO> saveWatchlist(@RequestBody MarketWatchlistRequest request) {
        try {
            List<String> symbols = request == null ? List.of() : request.getSymbols();
            return Result.ok(marketWatchlistService.save(symbols));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /** 当天抄底提醒，可能命中缓存。 */
    @GetMapping("/watchlist/alert")
    public Result<MarketWatchlistAlertVO> watchlistAlert() {
        try {
            return Result.ok(marketWatchlistService.alert());
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    // #region agent log
    private static void dbg(String hid, String loc, String msg, String data) {
        try {
            String line = "{\"sessionId\":\"1a3bec\",\"runId\":\"pre-fix\",\"hypothesisId\":\"" + hid
                    + "\",\"location\":\"" + loc + "\",\"message\":\"" + msg
                    + "\",\"data\":" + data + ",\"timestamp\":" + System.currentTimeMillis() + "}\n";
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of("E:\\Program files\\projects\\My-Chat\\debug-1a3bec.log"),
                    line,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }
    // #endregion
}
