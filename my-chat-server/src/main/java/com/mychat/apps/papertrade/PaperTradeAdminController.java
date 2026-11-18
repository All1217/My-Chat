package com.mychat.apps.papertrade;

import com.mychat.apps.papertrade.entity.vo.PaperSqlExportVO;
import com.mychat.apps.papertrade.ledger.PaperTradeService;
import com.mychat.apps.papertrade.ledger.entity.dto.PaperInitialCashRequest;
import com.mychat.apps.papertrade.ledger.entity.vo.PaperAccountVO;
import com.mychat.apps.papertrade.research.entity.dto.PaperResearchNoteRequest;
import com.mychat.apps.papertrade.research.entity.po.PaperResearchRun;
import com.mychat.apps.papertrade.research.entity.vo.PaperResearchNoteAdminVO;
import com.mychat.apps.papertrade.risk.PaperStageService;
import com.mychat.apps.papertrade.risk.entity.dto.PaperStageSummaryRequest;
import com.mychat.apps.papertrade.risk.entity.po.PaperStageSummary;
import com.mychat.apps.papertrade.trader.entity.dto.PaperAiDecisionUpdateRequest;
import com.mychat.apps.papertrade.trader.entity.vo.PaperAiDecisionVO;
import com.mychat.common.result.Result;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 模拟炒股管理端：初始资金、注销训练、历史表 CRUD。 */
@RestController
@RequestMapping("/ai/apps/paper-trade")
@AllArgsConstructor
public class PaperTradeAdminController {

    private final PaperTradeService paperTradeService;
    private final PaperTradeAdminService adminService;
    private final PaperStageService stageService;
    private final PaperSqlExportService sqlExportService;

    /** 修改初始资金。 */
    @PutMapping("/account/initial-cash")
    public Result<PaperAccountVO> updateInitialCash(@RequestBody PaperInitialCashRequest request) {
        try {
            PaperAccountVO vo = paperTradeService.updateInitialCash(
                    request == null ? null : request.getInitialCash());
            return Result.ok(stageService.attachLatest(vo));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /** 注销阶段训练：清空业务数据并重置账户，保留策略。 */
    @PostMapping("/account/wipe")
    public Result<PaperAccountVO> wipe() {
        return Result.ok(stageService.attachLatest(adminService.wipeTraining()));
    }

    // —— 阶段总结 ——

    @GetMapping("/admin/stage-summaries")
    public Result<List<PaperStageSummary>> listStageSummaries(
            @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(adminService.listStageSummaries(limit));
    }

    @GetMapping("/admin/stage-summaries/{id}")
    public Result<PaperStageSummary> getStageSummary(@PathVariable String id) {
        try {
            return Result.ok(adminService.getStageSummary(id));
        } catch (IllegalArgumentException e) {
            return Result.fail(404, e.getMessage());
        }
    }

    @PostMapping("/admin/stage-summaries")
    public Result<PaperStageSummary> createStageSummary(@RequestBody PaperStageSummaryRequest request) {
        try {
            return Result.ok(adminService.createStageSummary(request));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @PutMapping("/admin/stage-summaries/{id}")
    public Result<PaperStageSummary> updateStageSummary(@PathVariable String id,
                                                        @RequestBody PaperStageSummaryRequest request) {
        try {
            return Result.ok(adminService.updateStageSummary(id, request));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @DeleteMapping("/admin/stage-summaries/{id}")
    public Result<Void> deleteStageSummary(@PathVariable String id) {
        try {
            adminService.deleteStageSummary(id);
            return Result.ok(null);
        } catch (IllegalArgumentException e) {
            return Result.fail(404, e.getMessage());
        }
    }

    // —— AI 决策 ——

    @GetMapping("/admin/decisions")
    public Result<List<PaperAiDecisionVO>> listDecisions(@RequestParam(defaultValue = "50") int limit) {
        return Result.ok(adminService.listDecisions(limit));
    }

    @GetMapping("/admin/decisions/{id}")
    public Result<PaperAiDecisionVO> getDecision(@PathVariable String id) {
        try {
            return Result.ok(adminService.getDecision(id));
        } catch (IllegalArgumentException e) {
            return Result.fail(404, e.getMessage());
        }
    }

    @PutMapping("/admin/decisions/{id}")
    public Result<PaperAiDecisionVO> updateDecision(@PathVariable String id,
                                                    @RequestBody PaperAiDecisionUpdateRequest request) {
        try {
            return Result.ok(adminService.updateDecision(id, request));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @DeleteMapping("/admin/decisions/{id}")
    public Result<Void> deleteDecision(@PathVariable String id) {
        try {
            adminService.deleteDecision(id);
            return Result.ok(null);
        } catch (IllegalArgumentException e) {
            return Result.fail(404, e.getMessage());
        }
    }

    /** 删除交易轮次（含下属决策）。 */
    @DeleteMapping("/admin/ai-runs/{id}")
    public Result<Void> deleteAiRun(@PathVariable String id) {
        try {
            adminService.deleteAiRun(id);
            return Result.ok(null);
        } catch (IllegalArgumentException e) {
            return Result.fail(404, e.getMessage());
        }
    }

    // —— 搜集轮次 ——

    @GetMapping("/admin/research-runs")
    public Result<List<PaperResearchRun>> listResearchRuns(@RequestParam(defaultValue = "50") int limit) {
        return Result.ok(adminService.listResearchRuns(limit));
    }

    @GetMapping("/admin/research-runs/{id}")
    public Result<PaperResearchRun> getResearchRun(@PathVariable String id) {
        try {
            return Result.ok(adminService.getResearchRun(id));
        } catch (IllegalArgumentException e) {
            return Result.fail(404, e.getMessage());
        }
    }

    @DeleteMapping("/admin/research-runs/{id}")
    public Result<Void> deleteResearchRun(@PathVariable String id) {
        try {
            adminService.deleteResearchRun(id);
            return Result.ok(null);
        } catch (IllegalArgumentException e) {
            return Result.fail(404, e.getMessage());
        }
    }

    // —— 研究卡片 ——

    @GetMapping("/admin/research-notes")
    public Result<List<PaperResearchNoteAdminVO>> listResearchNotes(
            @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(adminService.listResearchNotes(limit));
    }

    @GetMapping("/admin/research-notes/{symbol}")
    public Result<PaperResearchNoteAdminVO> getResearchNote(@PathVariable String symbol) {
        try {
            return Result.ok(adminService.getResearchNote(symbol));
        } catch (IllegalArgumentException e) {
            return Result.fail(404, e.getMessage());
        }
    }

    @PostMapping("/admin/research-notes")
    public Result<PaperResearchNoteAdminVO> createResearchNote(
            @RequestBody PaperResearchNoteRequest request) {
        try {
            return Result.ok(adminService.createResearchNote(request));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @PutMapping("/admin/research-notes/{symbol}")
    public Result<PaperResearchNoteAdminVO> updateResearchNote(
            @PathVariable String symbol,
            @RequestBody PaperResearchNoteRequest request) {
        try {
            return Result.ok(adminService.updateResearchNote(symbol, request));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @DeleteMapping("/admin/research-notes/{symbol}")
    public Result<Void> deleteResearchNote(@PathVariable String symbol) {
        try {
            adminService.deleteResearchNote(symbol);
            return Result.ok(null);
        } catch (IllegalArgumentException e) {
            return Result.fail(404, e.getMessage());
        }
    }

    /** 导出历史表白名单全表 INSERT.sql（表名：paper_*）。 */
    @GetMapping("/admin/export/{table}")
    public Result<PaperSqlExportVO> exportSql(@PathVariable String table) {
        try {
            return Result.ok(sqlExportService.exportTable(table));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        } catch (IllegalStateException e) {
            return Result.fail(500, e.getMessage());
        }
    }
}
