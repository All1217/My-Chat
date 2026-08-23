package com.mychat.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mychat.common.result.Result;
import com.mychat.entity.po.DocumentMeta;
import com.mychat.entity.po.KnowledgeBase;
import com.mychat.mapper.DocumentMetaMapper;
import com.mychat.service.knowledge.DocumentIngestService;
import com.mychat.service.knowledge.KnowledgeBaseService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/ai/knowledge-base")
@AllArgsConstructor
public class KnowledgeBaseController {
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentMetaMapper documentMetaMapper;
    private final DocumentIngestService documentIngestService;

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
        knowledgeBaseService.save(kb);
        log.info("Created knowledge base: {} ({})", kb.getId(), kb.getName());
        return Result.ok(kb);
    }

    @PostMapping("/delete")
    public Result<Void> delete(@RequestParam String id) {
        knowledgeBaseService.deleteKb(id);
        return Result.ok();
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
