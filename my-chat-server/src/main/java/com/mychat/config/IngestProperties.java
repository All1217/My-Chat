package com.mychat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库入库配置（与 workspace 分离）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ingest")
public class IngestProperties {

    /** 原文落盘根目录 */
    private String root = "./src/main/resources/ingest";

    /** 单文件上限（MB），工作区导入仍走 servlet 200MB */
    private int maxFileSizeMb = 50;

    private int maxFilesPerRequest = 20;

    /** embedding 每批段数，对齐供应商批量上限 */
    private int embedBatchSize = 32;

    private String allowedExtensions = "pdf,docx,xlsx,html,htm,txt,md";

    public Set<String> allowedExtSet() {
        return Arrays.stream(allowedExtensions.split(","))
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }

    public long maxFileSizeBytes() {
        return maxFileSizeMb * 1024L * 1024L;
    }
}
