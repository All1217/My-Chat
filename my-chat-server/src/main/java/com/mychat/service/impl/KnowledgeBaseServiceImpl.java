package com.mychat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mychat.entity.po.DocumentMeta;
import com.mychat.entity.po.KnowledgeBase;
import com.mychat.mapper.DocumentMetaMapper;
import com.mychat.mapper.KnowledgeBaseMapper;
import com.mychat.service.KnowledgeBaseService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseService {
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMetaMapper documentMetaMapper;
    private final VectorStore vectorStore;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKb(String id) {
        List<DocumentMeta> docs = documentMetaMapper.selectList(
                new LambdaQueryWrapper<DocumentMeta>().eq(DocumentMeta::getKbId, id));
        if (!docs.isEmpty()) {
            List<String> docIds = docs.stream().map(DocumentMeta::getId).collect(Collectors.toList());
            vectorStore.delete(docIds);
            documentMetaMapper.delete(new LambdaQueryWrapper<DocumentMeta>().eq(DocumentMeta::getKbId, id));
        }
        knowledgeBaseMapper.deleteById(id);
        log.info("Deleted knowledge base {} with {} documents", id, docs.size());
    }
}
