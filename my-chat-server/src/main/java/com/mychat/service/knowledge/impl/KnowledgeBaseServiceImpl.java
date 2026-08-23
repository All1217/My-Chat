package com.mychat.service.knowledge.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mychat.entity.po.DocumentMeta;
import com.mychat.entity.po.KnowledgeBase;
import com.mychat.mapper.DocumentMetaMapper;
import com.mychat.mapper.KnowledgeBaseMapper;
import com.mychat.service.knowledge.EmbeddingService;
import com.mychat.service.knowledge.KnowledgeBaseService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseService {
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMetaMapper documentMetaMapper;
    private final EmbeddingService embeddingService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKb(String id) {
        List<DocumentMeta> docs = documentMetaMapper.selectList(
                new LambdaQueryWrapper<DocumentMeta>().eq(DocumentMeta::getKbId, id));
        if (!docs.isEmpty()) {
            embeddingService.deleteByDocumentMetas(docs);
            documentMetaMapper.delete(new LambdaQueryWrapper<DocumentMeta>().eq(DocumentMeta::getKbId, id));
        }
        knowledgeBaseMapper.deleteById(id);
        log.info("Deleted knowledge base {} with {} documents", id, docs.size());
    }
}
