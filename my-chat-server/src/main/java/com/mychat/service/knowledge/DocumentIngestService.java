package com.mychat.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mychat.config.IngestProperties;
import com.mychat.entity.po.AsyncJob;
import com.mychat.entity.po.DocumentMeta;
import com.mychat.entity.po.KnowledgeBase;
import com.mychat.entity.po.KnowledgeBaseSettings;
import com.mychat.job.AsyncJobService;
import com.mychat.mapper.AsyncJobMapper;
import com.mychat.mapper.DocumentMetaMapper;
import com.mychat.mapper.KnowledgeBaseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 知识库入库：HTTP 只落盘 + 插 PROCESSING + 提交 Job；向量化在 {@link #ingest}。
 */
@Slf4j
@Service
public class DocumentIngestService {

    public static final String JOB_TYPE = "kb_ingest";

    private static final int ERROR_MAX_CHARS = 1000;

    private final IngestProperties ingestProperties;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final DocumentMetaMapper documentMetaMapper;
    private final AsyncJobMapper asyncJobMapper;
    private final AsyncJobService asyncJobService;
    private final DocumentService documentService;
    private final EmbeddingService embeddingService;
    /** Spring Boot 4 只注册 Jackson 3 Bean，Jackson 2 Mapper 需自建（与 ChatStreamEventWriter 一致） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DocumentIngestService(
            IngestProperties ingestProperties,
            KnowledgeBaseMapper knowledgeBaseMapper,
            DocumentMetaMapper documentMetaMapper,
            AsyncJobMapper asyncJobMapper,
            @Lazy AsyncJobService asyncJobService,
            DocumentService documentService,
            EmbeddingService embeddingService) {
        this.ingestProperties = ingestProperties;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.documentMetaMapper = documentMetaMapper;
        this.asyncJobMapper = asyncJobMapper;
        this.asyncJobService = asyncJobService;
        this.documentService = documentService;
        this.embeddingService = embeddingService;
    }

    /**
     * 同步：校验、落盘、插元数据、提交 {@code kb_ingest}。不解析、不 embedding。
     */
    public List<DocumentMeta> accept(String kbId, List<MultipartFile> files) {
        if (!StringUtils.hasText(kbId)) {
            throw new IllegalArgumentException("必须绑定知识库");
        }
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId.trim());
        if (kb == null) {
            throw new IllegalArgumentException("知识库不存在");
        }
        List<MultipartFile> valid = nonEmptyFiles(files);
        if (valid.isEmpty()) {
            throw new IllegalArgumentException("请选择文件");
        }
        if (valid.size() > ingestProperties.getMaxFilesPerRequest()) {
            throw new IllegalArgumentException("单次最多上传 " + ingestProperties.getMaxFilesPerRequest() + " 个文件");
        }
        for (MultipartFile file : valid) {
            validateOne(file);
        }

        List<DocumentMeta> accepted = new ArrayList<>(valid.size());
        for (MultipartFile file : valid) {
            accepted.add(acceptOne(kbId.trim(), file));
        }
        return accepted;
    }

    /**
     * Job 线程：读盘 → 按已有 id 切段 → 分批 embedding → READY / FAILED。
     */
    public void ingest(String documentId) throws Exception {
        if (!StringUtils.hasText(documentId)) {
            throw new IllegalArgumentException("documentId 不能为空");
        }
        DocumentMeta meta = documentMetaMapper.selectById(documentId);
        if (meta == null) {
            throw new IllegalStateException("文档不存在: " + documentId);
        }
        if (!StringUtils.hasText(meta.getStoragePath())) {
            markFailed(documentId, "未找到落盘文件");
            throw new IllegalStateException("未找到落盘文件: " + documentId);
        }
        Path path = Path.of(meta.getStoragePath());
        if (!Files.isRegularFile(path)) {
            markFailed(documentId, "落盘文件丢失");
            throw new IllegalStateException("落盘文件丢失: " + path);
        }

        KnowledgeBase kb = knowledgeBaseMapper.selectById(meta.getKbId());
        int chunkSize = KnowledgeBaseSettings.chunkSizeOrDefault(kb != null ? kb.getChunkSize() : null);
        int chunkOverlap = KnowledgeBaseSettings.chunkOverlapOrDefault(kb != null ? kb.getChunkOverlap() : null);

        int written = 0;
        try (InputStream in = Files.newInputStream(path)) {
            DocumentService.ProcessedDocument processed = documentService.processDocument(
                    in, meta.getFilename(), meta.getKbId(), documentId, chunkSize, chunkOverlap);
            written = embeddingService.storeSegmentsBatched(
                    processed.segments(), ingestProperties.getEmbedBatchSize());
            markReady(documentId, written);
            log.info("文档入库成功 documentId={} chunks={}", documentId, written);
        } catch (EmbeddingService.PartialEmbedException e) {
            written = e.written();
            embeddingService.deleteByDocumentId(documentId, written);
            String msg = truncateError(e.getMessage());
            markFailed(documentId, msg);
            throw e;
        } catch (Exception e) {
            if (written > 0) {
                embeddingService.deleteByDocumentId(documentId, written);
            }
            String msg = truncateError(e.getMessage());
            markFailed(documentId, msg);
            throw e;
        }
    }

    public void deleteDocument(String id) {
        DocumentMeta meta = documentMetaMapper.selectById(id);
        if (meta == null) {
            return;
        }
        int chunks = meta.getChunkCount() != null ? meta.getChunkCount() : 0;
        embeddingService.deleteByDocumentId(meta.getId(), chunks);
        deleteStoredFile(meta);
        documentMetaMapper.deleteById(id);
    }

    public void deleteStoredFiles(List<DocumentMeta> docs) {
        if (docs == null) {
            return;
        }
        for (DocumentMeta doc : docs) {
            deleteStoredFile(doc);
        }
    }

    /**
     * 启动回收：kb_ingest 已 FAILED 且文档仍 PROCESSING 的标失败（不重跑）。
     */
    public int failDocumentsForFailedIngestJobs() {
        LambdaQueryWrapper<AsyncJob> q = new LambdaQueryWrapper<>();
        q.eq(AsyncJob::getJobType, JOB_TYPE)
                .eq(AsyncJob::getStatus, AsyncJob.STATUS_FAILED)
                .isNotNull(AsyncJob::getRefId);
        List<AsyncJob> jobs = asyncJobMapper.selectList(q);
        Set<String> refIds = new LinkedHashSet<>();
        for (AsyncJob job : jobs) {
            if (StringUtils.hasText(job.getRefId())) {
                refIds.add(job.getRefId().trim());
            }
        }
        int n = 0;
        for (String refId : refIds) {
            DocumentMeta meta = documentMetaMapper.selectById(refId);
            if (meta == null || !DocumentMeta.STATUS_PROCESSING.equals(meta.getStatus())) {
                continue;
            }
            markFailed(refId, "任务超时或进程中断");
            n++;
        }
        if (n > 0) {
            log.warn("已将 {} 条卡住的入库文档标为 FAILED", n);
        }
        return n;
    }

    public void deleteStoredFile(DocumentMeta meta) {
        if (meta == null || !StringUtils.hasText(meta.getStoragePath())) {
            return;
        }
        try {
            Path file = Path.of(meta.getStoragePath());
            Files.deleteIfExists(file);
            Path parent = file.getParent();
            if (parent != null) {
                try (var stream = Files.list(parent)) {
                    if (stream.findAny().isEmpty()) {
                        Files.deleteIfExists(parent);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("删除落盘文件失败 path={}: {}", meta.getStoragePath(), e.getMessage());
        }
    }

    private DocumentMeta acceptOne(String kbId, MultipartFile file) {
        String filename = file.getOriginalFilename();
        String docId = UUID.randomUUID().toString();
        String safeName = safeFilename(filename);
        Path dest = Path.of(ingestProperties.getRoot(), kbId, docId, safeName).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dest.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IllegalStateException("文件落盘失败: " + filename, e);
        }

        String ext = extensionOf(filename);
        DocumentMeta meta = new DocumentMeta();
        meta.setId(docId);
        meta.setKbId(kbId);
        meta.setFilename(filename);
        meta.setFileSize(file.getSize());
        meta.setFileType(ext);
        meta.setChunkCount(0);
        meta.setStatus(DocumentMeta.STATUS_PROCESSING);
        meta.setStoragePath(dest.toString());
        documentMetaMapper.insert(meta);

        String payload = toPayload(kbId, docId, dest.toString(), filename);
        asyncJobService.submit(JOB_TYPE, "向量化：" + filename, docId, payload);
        return meta;
    }

    private void validateOne(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("空文件非法！");
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("文件名不能为空！");
        }
        String ext = extensionOf(filename);
        if (!ingestProperties.allowedExtSet().contains(ext)) {
            throw new IllegalArgumentException("不支持的文件类型: " + ext);
        }
        if (file.getSize() > ingestProperties.maxFileSizeBytes()) {
            throw new IllegalArgumentException("单文件不能超过 " + ingestProperties.getMaxFileSizeMb() + "MB");
        }
    }

    private static List<MultipartFile> nonEmptyFiles(List<MultipartFile> files) {
        List<MultipartFile> out = new ArrayList<>();
        if (files == null) {
            return out;
        }
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                out.add(file);
            }
        }
        return out;
    }

    private void markReady(String documentId, int chunkCount) {
        LambdaUpdateWrapper<DocumentMeta> u = new LambdaUpdateWrapper<>();
        u.eq(DocumentMeta::getId, documentId)
                .set(DocumentMeta::getStatus, DocumentMeta.STATUS_READY)
                .set(DocumentMeta::getChunkCount, chunkCount)
                .set(DocumentMeta::getErrorMessage, null);
        documentMetaMapper.update(null, u);
    }

    private void markFailed(String documentId, String errorMessage) {
        LambdaUpdateWrapper<DocumentMeta> u = new LambdaUpdateWrapper<>();
        u.eq(DocumentMeta::getId, documentId)
                .set(DocumentMeta::getStatus, DocumentMeta.STATUS_FAILED)
                .set(DocumentMeta::getErrorMessage, truncateError(errorMessage));
        documentMetaMapper.update(null, u);
    }

    private String toPayload(String kbId, String documentId, String storagePath, String filename) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "kbId", kbId,
                    "documentId", documentId,
                    "storagePath", storagePath,
                    "filename", filename));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化入库 payload 失败", e);
        }
    }

    static String safeFilename(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return name.isEmpty() ? "unnamed" : name;
    }

    static String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private static String truncateError(String msg) {
        if (!StringUtils.hasText(msg)) {
            return "入库失败";
        }
        String t = msg.trim();
        return t.length() <= ERROR_MAX_CHARS ? t : t.substring(0, ERROR_MAX_CHARS);
    }
}
