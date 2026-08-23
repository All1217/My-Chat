package com.mychat.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mychat.common.result.Result;
import com.mychat.entity.po.DocumentMeta;
import com.mychat.entity.po.KnowledgeBase;
import com.mychat.mapper.DocumentMetaMapper;
import com.mychat.service.knowledge.KnowledgeBaseService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/ai/knowledge-base")
@AllArgsConstructor
public class KnowledgeBaseController {
    private final KnowledgeBaseService knowledgeBaseService;
    private final DocumentMetaMapper documentMetaMapper;

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

    @GetMapping("/documents")
    public Result<List<DocumentMeta>> listDocuments(@RequestParam String kbId) {
        return Result.ok(documentMetaMapper.selectList(
                new LambdaQueryWrapper<DocumentMeta>().eq(DocumentMeta::getKbId, kbId)
                        .orderByDesc(DocumentMeta::getCreatedAt)));
    }
}
