package com.mychat.service.knowledge;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mychat.entity.po.KnowledgeBase;

public interface KnowledgeBaseService extends IService<KnowledgeBase> {
    void deleteKb(String id);
}
