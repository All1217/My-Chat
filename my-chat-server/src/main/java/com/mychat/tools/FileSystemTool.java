package com.mychat.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 让AI能够浏览本地项目目录结构和读取文件内容的工具类
 */
public class FileSystemTool {

    // 安全限制：只允许访问项目根目录下的内容
    private static final Path PROJECT_ROOT = Paths.get(System.getProperty("user.dir"))
            .toAbsolutePath().normalize();

    /**
     * 列出指定目录下的直接子目录和文件（仅一级）
     */
    @Tool(description = "列出给定目录下的直接子目录和文件（仅一级）。输入相对路径，如 '.' 表示项目根目录")
    public String listDirectory(
            @ToolParam(description = "要列出的目录相对路径，例如 '.' 或 'src/main/java'") String path) {
        Path resolved = resolveSafe(path);
        if (resolved == null || !Files.isDirectory(resolved)) {
            return "错误：'" + path + "' 不是有效的目录，或无权访问。";
        }

        try (Stream<Path> entries = Files.list(resolved)) {
            List<String> lines = new ArrayList<>();
            lines.add("目录: " + resolved.toAbsolutePath());
            lines.add("----------------------------------------");

            List<Path> sorted = entries.sorted().toList();
            for (Path entry : sorted) {
                String type = Files.isDirectory(entry) ? "[DIR] " : "[FILE]";
                String name = entry.getFileName().toString();
                try {
                    String sizeStr = Files.isDirectory(entry) ? ""
                            : " (" + formatSize(Files.size(entry)) + ")";
                    lines.add(type + "  " + name + sizeStr);
                } catch (IOException e) {
                    lines.add(type + "  " + name);
                }
            }

            if (sorted.isEmpty()) lines.add("(空目录)");
            return String.join("\n", lines);
        } catch (IOException e) {
            return "读取目录失败: " + e.getMessage();
        }
    }

    /**
     * 递归列出目录树结构（类似 tree 命令）
     */
    @Tool(description = "递归列出目录树结构（类似tree命令）。可指定最大深度（建议不超过3，避免输出过长）")
    public String treeDirectory(
            @ToolParam(description = "目录相对路径") String path,
            @ToolParam(description = "最大递归深度，默认2") int maxDepth) {

        Path resolved = resolveSafe(path);
        if (resolved == null || !Files.isDirectory(resolved)) {
            return "错误：'" + path + "' 不是有效的目录，或无权访问。";
        }
        if (maxDepth <= 0) maxDepth = 2;
        if (maxDepth > 16) maxDepth = 16;
        StringBuilder sb = new StringBuilder();
        sb.append(resolved.toAbsolutePath().toString()).append("\n");
        try {
            buildTree(resolved, "", maxDepth, 0, sb);
        } catch (IOException e) {
            return "生成目录树失败: " + e.getMessage();
        }
        return sb.toString();
    }

    /**
     * 读取文本文件内容
     */
    @Tool(description = "读取指定文本文件的内容。输入文件相对于项目的路径，如 'pom.xml' 或 'src/main/java/com/mychat/tools/ToolDemo.java'")
    public String readFile(
            @ToolParam(description = "文件相对路径") String filePath) {
        Path resolved = resolveSafe(filePath);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            return "错误：'" + filePath + "' 不是有效的文件，或无权访问。";
        }
        try {
            String content = Files.readString(resolved);
            long fileSize = Files.size(resolved);
            // 防止 token 爆炸，过大的文件截断处理
            if (content.length() > 30_000) {
                content = content.substring(0, 30_000)
                        + "\n\n... [文件过长，已截断。完整大小: "
                        + formatSize(fileSize) + "]";
            }
            return "文件: " + resolved.toAbsolutePath() + "\n"
                    + "大小: " + formatSize(fileSize) + "\n"
                    + "----------------------------------------\n"
                    + content;
        } catch (IOException e) {
            return "读取文件失败: " + e.getMessage();
        }
    }

    // ==================== 内部辅助方法 ====================

    private Path resolveSafe(String path) {
        Path resolved = PROJECT_ROOT.resolve(path).toAbsolutePath().normalize();
        // 安全校验：必须在项目根目录内
        if (resolved.startsWith(PROJECT_ROOT) && Files.exists(resolved)) {
            return resolved;
        }
        return null;
    }

    private void buildTree(Path dir, String prefix, int maxDepth, int currentDepth,
                           StringBuilder sb) throws IOException {
        if (currentDepth >= maxDepth) return;

        try (Stream<Path> entries = Files.list(dir)) {
            List<Path> sorted = entries.sorted().toList();
            for (int i = 0; i < sorted.size(); i++) {
                Path entry = sorted.get(i);
                boolean isLast = (i == sorted.size() - 1);
                String connector = isLast ? "└── " : "├── ";
                String childPrefix = isLast ? "    " : "│   ";

                sb.append(prefix).append(connector).append(entry.getFileName());
                if (Files.isDirectory(entry)) {
                    sb.append("/\n");
                    buildTree(entry, prefix + childPrefix, maxDepth, currentDepth + 1, sb);
                } else {
                    sb.append("\n");
                }
            }
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
