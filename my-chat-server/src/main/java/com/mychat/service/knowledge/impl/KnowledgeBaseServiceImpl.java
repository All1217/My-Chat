package com.mychat.service.knowledge.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mychat.entity.dto.KnowledgeBaseUpdateRequest;
import com.mychat.entity.po.DocumentMeta;
import com.mychat.entity.po.KnowledgeBase;
import com.mychat.entity.po.KnowledgeBaseSettings;
import com.mychat.mapper.DocumentMetaMapper;
import com.mychat.mapper.KnowledgeBaseMapper;
import com.mychat.service.knowledge.DocumentIngestService;
import com.mychat.service.knowledge.EmbeddingService;
import com.mychat.service.knowledge.KnowledgeBaseService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 知识库持久化：删除级联清理；更新时校验切分/检索参数。
 */
@Slf4j
@Service
@AllArgsConstructor
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseService {
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMetaMapper documentMetaMapper;
    private final EmbeddingService embeddingService;
    private final DocumentIngestService documentIngestService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKb(String id) {
        List<DocumentMeta> docs = documentMetaMapper.selectList(
                new LambdaQueryWrapper<DocumentMeta>().eq(DocumentMeta::getKbId, id));
        if (!docs.isEmpty()) {
            embeddingService.deleteByDocumentMetas(docs);
            documentIngestService.deleteStoredFiles(docs);
            documentMetaMapper.delete(new LambdaQueryWrapper<DocumentMeta>().eq(DocumentMeta::getKbId, id));
        }
        knowledgeBaseMapper.deleteById(id);
        log.info("Deleted knowledge base {} with {} documents", id, docs.size());
    }

    @Override
    public KnowledgeBase updateKb(KnowledgeBaseUpdateRequest request) {
        if (request == null || !StringUtils.hasText(request.getId())) {
            throw new IllegalArgumentException("知识库 ID 不能为空");
        }
        KnowledgeBase kb = knowledgeBaseMapper.selectById(request.getId().trim());
        if (kb == null) {
            throw new IllegalArgumentException("知识库不存在");
        }
        if (request.getName() != null) {
            if (!StringUtils.hasText(request.getName())) {
                throw new IllegalArgumentException("知识库名称不能为空");
            }
            kb.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            kb.setDescription(request.getDescription());
        }
        if (request.getChunkSize() != null) {
            kb.setChunkSize(request.getChunkSize());
        }
        if (request.getChunkOverlap() != null) {
            kb.setChunkOverlap(request.getChunkOverlap());
        }
        if (request.getTopK() != null) {
            kb.setTopK(request.getTopK());
        }
        if (request.getSimilarityThreshold() != null) {
            kb.setSimilarityThreshold(request.getSimilarityThreshold());
        }
        KnowledgeBaseSettings.validate(kb);
        knowledgeBaseMapper.updateById(kb);
        log.info("Updated knowledge base {} chunkSize={} overlap={} topK={} threshold={}",
                kb.getId(), kb.getChunkSize(), kb.getChunkOverlap(), kb.getTopK(), kb.getSimilarityThreshold());
        return knowledgeBaseMapper.selectById(kb.getId());
    }
}
