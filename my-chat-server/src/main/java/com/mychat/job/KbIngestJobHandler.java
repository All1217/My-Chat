package com.mychat.job;

import com.mychat.entity.po.AsyncJob;
import com.mychat.service.knowledge.DocumentIngestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 知识库单文档向量化（首次入库与重新向量化共用）。HTTP 已落盘或校验；本 Handler 只读盘切段。
 */
@Slf4j
@Component
public class KbIngestJobHandler implements JobHandler {

    private final DocumentIngestService documentIngestService;

    public KbIngestJobHandler(@Lazy DocumentIngestService documentIngestService) {
        this.documentIngestService = documentIngestService;
    }

    // 自报家门：我专门处理哪种类型的 job
    @Override
    public String type() {
        return DocumentIngestService.JOB_TYPE;
    }

    @Override
    public void execute(AsyncJob job) throws Exception {
        String documentId = job.getRefId();
        if (!StringUtils.hasText(documentId)) {
            throw new IllegalArgumentException("kb_ingest 缺少 refId（document_meta.id）");
        }
        log.info("开始入库 documentId={} jobId={}", documentId, job.getId());
        documentIngestService.ingest(documentId);
    }
}
