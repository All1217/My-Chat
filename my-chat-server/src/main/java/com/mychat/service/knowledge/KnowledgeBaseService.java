package com.mychat.service.knowledge;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mychat.entity.dto.KnowledgeBaseUpdateRequest;
import com.mychat.entity.po.KnowledgeBase;

/**
 * 知识库 CRUD；删除时级联清文档、向量和落盘文件。
 */
public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    /** 删除知识库及其文档、向量、磁盘文件。 */
    void deleteKb(String id);

    /** 更新名称/描述与切分、检索参数并返回最新记录。 */
    KnowledgeBase updateKb(KnowledgeBaseUpdateRequest request);
}
