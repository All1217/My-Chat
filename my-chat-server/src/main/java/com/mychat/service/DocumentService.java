package com.mychat.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DocumentService {
    private static final TokenTextSplitter splitter = TokenTextSplitter.builder().build();

    public ProcessedDocument processDocument(InputStream inputStream, String filename) {
        log.info("Processing document: {}", filename);
        String documentId = UUID.randomUUID().toString();
        try {
            String content;
            if (filename.toLowerCase().endsWith(".pdf")) {
                content = parsePdf(inputStream);
            } else {
                content = parseText(inputStream);
            }
            if (content == null || content.isBlank()) {
                throw new RuntimeException("空文件非法！");
            }
            Document document = new Document(documentId, content, Map.of(
                    "filename", filename,
                    "documentId", documentId
            ));
            List<Document> segments = splitter.split(document);
            log.info("Document '{}' processed into {} segments", filename, segments.size());
            return new ProcessedDocument(documentId, filename, segments);
        } catch (Exception e) {
            log.error("Failed to process document: {}", filename, e);
            throw new RuntimeException("Document processing failed: " + e.getMessage(), e);
        }
    }

    private String parsePdf(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String parseText(InputStream inputStream) {
        return new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));
    }

    // record： Java 14 新语法，本质是个类，而且自动拥有set、get
    public record ProcessedDocument(String documentId, String filename, List<Document> segments) {}
}
