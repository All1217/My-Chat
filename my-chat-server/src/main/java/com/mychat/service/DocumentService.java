package com.mychat.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.jsoup.Jsoup;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DocumentService {
    private static final TokenTextSplitter splitter = TokenTextSplitter.builder().build();

    public ProcessedDocument processDocument(InputStream inputStream, String filename, String kbId) {
        log.info("Processing document: {}, kbId={}", filename, kbId);
        String documentId = UUID.randomUUID().toString();
        try {
            String ext = filename.contains(".")
                    ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
                    : "";
            String content = parseByExtension(inputStream, ext);
            if (content == null || content.isBlank()) {
                throw new RuntimeException("空文件非法！");
            }
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("filename", filename);
            metadata.put("documentId", documentId);
            if (kbId != null && !kbId.isEmpty()) {
                metadata.put("kbId", kbId);
            }
            Document document = new Document(documentId, content, metadata);
            List<Document> rawSegments = splitter.split(document);
            List<Document> segments = new ArrayList<>(rawSegments.size());
            for (int i = 0; i < rawSegments.size(); i++) {
                Document seg = rawSegments.get(i);
                String segmentId = UUID.nameUUIDFromBytes(
                        (documentId + "_" + i).getBytes(StandardCharsets.UTF_8)).toString();
                segments.add(new Document(segmentId, seg.getText(), seg.getMetadata()));
            }
            log.info("Document '{}' processed into {} segments", filename, segments.size());
            return new ProcessedDocument(documentId, filename, segments);
        } catch (Exception e) {
            log.error("Failed to process document: {}", filename, e);
            throw new RuntimeException("Document processing failed: " + e.getMessage(), e);
        }
    }

    private String parseByExtension(InputStream inputStream, String ext) throws Exception {
        return switch (ext) {
            case "pdf" -> parsePdf(inputStream);
            case "docx" -> parseDocx(inputStream);
            case "xlsx" -> parseXlsx(inputStream);
            case "html", "htm" -> parseHtml(inputStream);
            default -> parseText(inputStream);
        };
    }

    private String parsePdf(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String parseDocx(InputStream inputStream) throws Exception {
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            return document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .filter(line -> !line.isBlank())
                    .collect(Collectors.joining("\n"));
        }
    }

    private String parseXlsx(InputStream inputStream) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(inputStream)) {
            StringBuilder sb = new StringBuilder();
            for (Sheet sheet : workbook) {
                sb.append("## ").append(sheet.getSheetName()).append("\n");
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        sb.append(getCellValue(cell)).append("\t");
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    private String parseHtml(InputStream inputStream) throws Exception {
        org.jsoup.nodes.Document html = Jsoup.parse(inputStream, "UTF-8", "");
        return html.body().text();
    }

    private String parseText(InputStream inputStream) {
        return new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));
    }

    /**
     * 聊天附件抽纯文本（不切段、不入向量库）。复用知识库解析路径。
     *
     * @param inputStream 文件流（由调用方关闭 MultipartFile 生命周期）
     * @param filename    原文件名（用于按扩展名选择解析器）
     * @return 纯文本；空文件返回空串
     */
    public String extractPlainText(InputStream inputStream, String filename) throws Exception {
        if (inputStream == null) {
            return "";
        }
        String name = filename != null ? filename : "";
        String ext = name.contains(".")
                ? name.substring(name.lastIndexOf('.') + 1).toLowerCase()
                : "";
        String content = parseByExtension(inputStream, ext);
        return content != null ? content : "";
    }

    private String getCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    yield cell.getStringCellValue();
                }
            }
            default -> "";
        };
    }

    public record ProcessedDocument(String documentId, String filename, List<Document> segments) {}
}
