package com.mychat.controller;

import com.mychat.config.WorkspaceContext;
import com.mychat.service.agent.ChatOrchestrateStreamService;
import com.mychat.service.agent.ChatUploadEnrichment;
import com.mychat.service.chat.ChatSessionsService;
import com.mychat.utils.WorkspaceUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 主聊天 HTTP 入口：校验 ndjson、绑工作区、解析 kbId、处理附件后交给管道。
 * <p>
 * 怎么读：本类 → {@link ChatUploadEnrichment}（附件）→ {@link ChatOrchestrateStreamService}（NDJSON 管道）
 * → {@link com.mychat.service.agent.AgentOrchestratorService}（编排循环与 Worker）。
 * 主路固定 Orchestrator，不走 Routing；Routing 仅 {@code POST /ai/agent/route}（Demo）。
 * 附件仅 txt/md/pdf，图片双拒。
 */
@Slf4j
@RestController
@RequestMapping("/ai/normalChat")
public class ChatController {

    private final ChatOrchestrateStreamService chatOrchestrateStreamService;
    private final ChatUploadEnrichment chatUploadEnrichment;
    private final ChatSessionsService chatSessionsService;
    private final WorkspaceUtil workspaceUtil;

    public ChatController(
            ChatOrchestrateStreamService chatOrchestrateStreamService,
            ChatUploadEnrichment chatUploadEnrichment,
            ChatSessionsService chatSessionsService,
            WorkspaceUtil workspaceUtil) {
        this.chatOrchestrateStreamService = chatOrchestrateStreamService;
        this.chatUploadEnrichment = chatUploadEnrichment;
        this.chatSessionsService = chatSessionsService;
        this.workspaceUtil = workspaceUtil;
    }

    /**
     * 主聊天 NDJSON 流式入口。
     *
     * @param prompt       用户本轮原问
     * @param chatId       会话 ID（Memory / 工作区 / 回合落库）
     * @param files        可选附件，仅 txt/md/pdf；正文只进本轮 Agent
     * @param format       必须为 {@code ndjson}
     * @param kbId         可选知识库；缺省用会话绑定
     * @param qualityLoop  写盘后是否跑质量环；缺省 true，显式 false 关闭
     * @param criteria     质量环评价标准；缺省用服务端默认文案
     */
    @RequestMapping(value = "/chat")
    public Flux<String> chat(
            @RequestParam("prompt") String prompt,
            @RequestParam("chatId") String chatId,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "format", required = false) String format,
            @RequestParam(value = "kbId", required = false) String kbId,
            @RequestParam(value = "qualityLoop", required = false) Boolean qualityLoop,
            @RequestParam(value = "criteria", required = false) String criteria,
            HttpServletResponse response) {

        if (!"ndjson".equalsIgnoreCase(format)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "主聊天仅支持 format=ndjson（Orchestrator）；单次 Routing 请用 POST /ai/agent/route");
        }
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/x-ndjson;charset=UTF-8");

        bindWorkspace(chatId);
        String effectiveKbId = resolveKbId(chatId, kbId);
        // 默认开启质量环；仅显式 qualityLoop=false 关闭
        boolean ql = !Boolean.FALSE.equals(qualityLoop);

        // 图片暂不支持：前后端双拒，避免静默丢图
        if (chatUploadEnrichment.containsImageFile(files)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "暂不支持图片上传，请使用 txt/md/pdf 文本附件");
        }
        // 注入文件全文内容。同时让刷新后的文件上传历史只显示文件名列表而非全文
        String agentInput = chatUploadEnrichment.enrichPromptWithUploadedDocuments(prompt, files);
        // 将上传文件内容录入数据库（仅文件名列表加原问题）
        String memoryUserText = chatUploadEnrichment.buildMemoryUserText(prompt, files);

        return chatOrchestrateStreamService
                .streamOrchestrate(agentInput, memoryUserText, prompt, chatId, effectiveKbId, ql, criteria)
                .doFinally(signalType -> WorkspaceContext.clear());
    }

    private String resolveKbId(String chatId, String requestKbId) {
        if (StringUtils.hasText(requestKbId)) {
            return requestKbId.trim();
        }
        String sessionKb = chatSessionsService.getKbId(chatId);
        return StringUtils.hasText(sessionKb) ? sessionKb.trim() : null;
    }

    private void bindWorkspace(String chatId) {
        String workDir = chatSessionsService.getWorkDir(chatId);
        if (workDir != null) {
            WorkspaceContext.set(workDir);
            log.info("会话 {} 工作目录已设置为: {}", chatId, workDir);
        } else {
            String defaultRoot = workspaceUtil.getWorkspaceRoot().toString();
            WorkspaceContext.set(defaultRoot);
            log.info("会话 {} 使用默认工作目录: {}", chatId, defaultRoot);
        }
    }
}
