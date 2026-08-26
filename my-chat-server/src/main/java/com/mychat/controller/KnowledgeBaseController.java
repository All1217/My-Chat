package com.mychat.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mychat.common.result.Result;
import com.mychat.entity.dto.DocumentChunkListResponse;
import com.mychat.entity.dto.KnowledgeBaseUpdateRequest;
import com.mychat.entity.dto.KnowledgeRetrieveTestRequest;
import com.mychat.entity.dto.KnowledgeRetrieveTestResponse;
import com.mychat.entity.po.DocumentMeta;
import com.mychat.entity.po.KnowledgeBase;
import com.mychat.entity.po.KnowledgeBaseSettings;
import com.mychat.mapper.DocumentMetaMapper;
import com.mychat.service.knowledge.DocumentChunkService;
import com.mychat.service.knowledge.DocumentIngestService;
import com.mychat.service.knowledge.KnowledgeBaseService;
import com.mychat.service.knowledge.KnowledgeRetrievalService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * 知识库与文档元数据 API：列表、创建、更新参数、删除、批量入库、重新向量化、召回测试、只读分段。
 */
@Slf4j
@RestController
@RequestMapping("/ai/knowledge-base")
@AllArgsConstructor
public class KnowledgeBaseController {
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentMetaMapper documentMetaMapper;
    private final DocumentIngestService documentIngestService;
    private final KnowledgeRetrievalService knowledgeRetrievalService;
    private final DocumentChunkService documentChunkService;

    @GetMapping("/list")
    public Result<List<KnowledgeBase>> list() {
        return Result.ok(knowledgeBaseService.list());
    }

    @PostMapping("/create")
    public Result<KnowledgeBase> create(@RequestParam String name, @RequestParam(required = false) String description) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(UUID.randomUUID().toString());
        kb.setName(name);
        kb.setDescription(description);
        KnowledgeBaseSettings.applyDefaults(kb);
        KnowledgeBaseSettings.validate(kb);
        knowledgeBaseService.save(kb);
        log.info("Created knowledge base: {} ({})", kb.getId(), kb.getName());
        return Result.ok(kb);
    }

    /**
     * 更新名称/描述与切分、检索参数。切分变更只影响之后新上传的文档，已入库文档需重新向量化。
     */
    @PostMapping("/update")
    public Result<KnowledgeBase> update(@RequestBody KnowledgeBaseUpdateRequest request) {
        try {
            return Result.ok(knowledgeBaseService.updateKb(request));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam String id) {
        knowledgeBaseService.deleteKb(id);
        return Result.ok();
    }

    /**
     * 召回测试：只检索命中片段，不调用生成模型。topK/阈值可临时覆盖且不写库。
     */
    @PostMapping("/documents/retrieve-test")
    public Result<KnowledgeRetrieveTestResponse> retrieveTest(@RequestBody KnowledgeRetrieveTestRequest request) {
        try {
            return Result.ok(knowledgeRetrievalService.retrieveTest(request));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("召回测试失败", e);
            return Result.fail(500, e.getMessage() != null ? e.getMessage() : "召回测试失败");
        }
    }

    /**
     * 只读分段列表：按 position 升序返回原文与摘要；无行返回空数组。
     */
    @GetMapping("/documents/chunks")
    public Result<DocumentChunkListResponse> listDocumentChunks(@RequestParam("id") String id) {
        try {
            return Result.ok(documentChunkService.listByDocumentId(id));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /**
     * 文档级重新向量化：读落盘原文，按当前切分参数重切。立刻返回。
     */
    @PostMapping("/documents/reindex")
    public Result<DocumentMeta> reindexDocument(@RequestParam("id") String id) {
        try {
            return Result.ok(documentIngestService.submitReindex(id));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("重新向量化提交失败 id={}", id, e);
            return Result.fail(500, e.getMessage() != null ? e.getMessage() : "提交失败");
        }
    }

    /**
     * 批量提交入库：只落盘并登记 PROCESSING，立刻返回。向量化走 kb_ingest Job。
     */
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<List<DocumentMeta>> uploadDocuments(
            @RequestParam("kbId") String kbId,
            @RequestParam("files") List<MultipartFile> files) {
        try {
            return Result.ok(documentIngestService.accept(kbId, files));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        } catch (Exception e) {
            log.error("批量入库提交失败 kbId={}", kbId, e);
            return Result.fail(500, e.getMessage() != null ? e.getMessage() : "上传失败");
        }
    }

    @GetMapping("/documents")
    public Result<List<DocumentMeta>> listDocuments(@RequestParam String kbId) {
        return Result.ok(documentMetaMapper.selectList(
                new LambdaQueryWrapper<DocumentMeta>().eq(DocumentMeta::getKbId, kbId)
                        .orderByDesc(DocumentMeta::getCreatedAt)));
    }
}
