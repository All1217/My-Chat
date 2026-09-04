package com.mychat.service.agent;

import com.mychat.service.knowledge.DocumentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 主聊天附件 enrichment：正文只进本轮编排，Memory / 刷新气泡只留文件名+原问。
 */
@Slf4j
@Service
public class ChatUploadEnrichment {

    /** 单文件抽正文上限，超出则截断并标注。 */
    private static final int DOC_CONTENT_MAX_CHARS = 50_000;

    private final DocumentService documentService;

    public ChatUploadEnrichment(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 将上传的 txt/md/pdf 抽成文本，拼进本轮用户目标（不落 Memory）。
     */
    public String enrichPromptWithUploadedDocuments(String prompt, List<MultipartFile> files) {
        List<MultipartFile> documents = nonEmptyDocuments(files);
        if (documents.isEmpty()) {
            return prompt;
        }
        String fileList = buildFileList(documents);
        String docContent = extractDocContent(documents);
        StringBuilder sb = new StringBuilder();
        if (!fileList.isEmpty()) {
            sb.append(fileList).append("\n\n");
        }
        if (!docContent.isEmpty()) {
            sb.append("以下为上传文档正文（供回答参考）：\n").append(docContent).append("\n");
        }
        sb.append("用户的问题：\n").append(prompt != null ? prompt : "");
        return sb.toString();
    }

    /**
     * 写入 spring_ai_chat_memory / 刷新后气泡：仅文件名列表 + 原问，不含正文。
     */
    public String buildMemoryUserText(String prompt, List<MultipartFile> files) {
        List<MultipartFile> documents = nonEmptyDocuments(files);
        if (documents.isEmpty()) {
            return prompt != null ? prompt : "";
        }
        String fileList = buildFileList(documents);
        return fileList + "\n\n用户的问题：\n" + (prompt != null ? prompt : "");
    }

    /**
     * 是否含图片附件（contentType 或常见后缀）。主聊天图片双拒。
     */
    public boolean containsImageFile(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return false;
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("image/")) {
                return true;
            }
            String name = file.getOriginalFilename();
            if (name != null) {
                String lower = name.toLowerCase();
                if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                        || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<MultipartFile> nonEmptyDocuments(List<MultipartFile> files) {
        List<MultipartFile> documents = new ArrayList<>();
        if (files == null) {
            return documents;
        }
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                documents.add(file);
            }
        }
        return documents;
    }

    /** 提取文件名列表。 */
    private String buildFileList(List<MultipartFile> documents) {
        if (documents.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("上传了以下文件：\n");
        for (MultipartFile file : documents) {
            String name = file.getOriginalFilename();
            if (name == null) {
                continue;
            }
            sb.append("- ").append(name).append("（").append(formatFileSize(file.getSize())).append("）\n");
        }
        return sb.toString().trim();
    }

    /** 提取文件正文，单文件超过上限则截断。 */
    private String extractDocContent(List<MultipartFile> documents) {
        if (documents.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (MultipartFile file : documents) {
            String filename = file.getOriginalFilename();
            if (filename == null) {
                continue;
            }
            try {
                String text = documentService.extractPlainText(file.getInputStream(), filename);
                if (!text.isBlank()) {
                    sb.append("\n--- ").append(filename).append(" ---\n");
                    if (text.length() > DOC_CONTENT_MAX_CHARS) {
                        text = text.substring(0, DOC_CONTENT_MAX_CHARS) + "\n\n... [内容过长已截断]";
                    }
                    sb.append(text).append("\n");
                }
            } catch (Exception e) {
                log.warn("提取文档内容失败: {}", filename, e);
            }
        }
        return sb.toString();
    }

    /** 格式化文件大小。 */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
