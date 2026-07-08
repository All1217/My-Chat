package com.mychat.tools;

import com.mychat.utils.WorkspaceUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 安全的只读 Shell 命令执行工具（纯 Java NIO 实现）
 * 不 fork 任何子进程，所有操作基于 Java NIO API，路径严格限定在 workspace root 内。
 */
@Component
public class ShellTool {

    private final WorkspaceUtil workspaceUtil;

    public ShellTool(WorkspaceUtil workspaceUtil) {
        this.workspaceUtil = workspaceUtil;
    }

    @Tool(description = "在项目目录中执行只读文件操作。" +
            "可用操作: ls (列出目录), tree (目录树), cat (查看文件内容), " +
            "grep (搜索文件内容), stat (文件/目录信息)。" +
            "示例: 'ls src/main/java', 'tree src', 'cat pom.xml', " +
            "'grep \"@Tool\" src\\main\\java', 'stat src'")
    public String executeCommand(
            @ToolParam(description = "操作命令，格式: <操作> [路径] [参数]") String command) {

        if (command == null || command.isBlank()) {
            return "错误：命令不能为空。可用: ls, tree, cat, grep, stat";
        }

        String[] parts = command.trim().split("\\s+", 2);
        String action = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : "";

        try {
            return switch (action) {
                case "ls", "dir" -> executeLs(arg);
                case "tree" -> executeTree(arg);
                case "cat", "type" -> executeCat(arg);
                case "grep", "select-string" -> executeGrep(arg);
                case "stat" -> executeStat(arg);
                default -> "未知操作: '" + action + "'。可用: ls, tree, cat, grep, stat";
            };
        } catch (SecurityException e) {
            return "安全拦截: " + e.getMessage();
        } catch (Exception e) {
            return "操作失败: " + e.getMessage();
        }
    }

    private String executeLs(String arg) throws IOException {
        Path dir = resolvePath(arg.isEmpty() ? "." : arg);
        if (!Files.isDirectory(dir)) {
            return "错误: 路径不是目录: " + dir.getFileName();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("目录: ").append(workspaceUtil.getWorkspaceRoot().relativize(dir).toString().replace("\\", "/"))
                .append("\n----------------------------------------\n");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            List<Path> items = new ArrayList<>();
            stream.forEach(items::add);
            items.sort(Comparator.comparing(p -> {
                boolean isDir = Files.isDirectory(p);
                return (isDir ? "0" : "1") + p.getFileName().toString().toLowerCase();
            }));
            for (Path p : items) {
                BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                String type = Files.isDirectory(p) ? "DIR" : "FIL";
                sb.append(String.format("[%s] %s (%s)\n", type, p.getFileName(),
                        formatSize(attrs.size())));
            }
        }
        return sb.toString();
    }

    private String executeTree(String arg) throws IOException {
        Path dir = resolvePath(arg.isEmpty() ? "." : arg);
        if (!Files.isDirectory(dir)) {
            return "错误: 路径不是目录";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("目录树: ").append(workspaceUtil.getWorkspaceRoot().relativize(dir).toString().replace("\\", "/"))
                .append("\n");
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            private int depth = 0;

            @Override
            public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                if (depth > 0) {
                    sb.append("  ".repeat(depth - 1)).append("├─ ")
                            .append(d.getFileName()).append("/\n");
                }
                depth++;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) {
                sb.append("  ".repeat(depth)).append("├─ ")
                        .append(f.getFileName()).append("\n");
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) {
                depth--;
                return FileVisitResult.CONTINUE;
            }
        });
        return sb.toString();
    }

    private String executeCat(String arg) throws IOException {
        if (arg.isEmpty()) {
            return "错误: 缺少文件路径";
        }
        Path file = resolvePath(arg);
        if (!Files.isRegularFile(file)) {
            return "错误: 目标不是文件: " + arg;
        }
        long size = Files.size(file);
        if (size > 1024 * 1024) {
            return "文件过大 (" + formatSize(size) + ")，跳过读取，请使用其他工具查看";
        }
        String content = Files.readString(file, StandardCharsets.UTF_8);
        if (content.length() > 20_000) {
            content = content.substring(0, 20_000) + "\n\n... [输出过长，已截断]";
        }
        return content;
    }

    private String executeGrep(String arg) throws IOException {
        if (arg.isEmpty()) {
            return "错误: 缺少搜索模式。格式: grep <模式> [路径]";
        }
        String[] parts = arg.split("\\s+", 2);
        String pattern = parts[0];
        String pathArg = parts.length > 1 ? parts[1] : ".";

        Path start = resolvePath(pathArg);
        if (!Files.isDirectory(start)) {
            start = start.getParent();
            if (start == null || !Files.isDirectory(start)) {
                return "错误: 无法确定搜索目录";
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("搜索 \"").append(pattern).append("\" 在 ")
                .append(workspaceUtil.getWorkspaceRoot().relativize(start).toString().replace("\\", "/"))
                .append("\n----------------------------------------\n");

        int[] matchCount = {0};
        try (Stream<Path> paths = Files.walk(start)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".java")
                                || name.endsWith(".ts") || name.endsWith(".js") || name.endsWith(".vue")
                                || name.endsWith(".py") || name.endsWith(".html") || name.endsWith(".css")
                                || name.endsWith(".xml") || name.endsWith(".json") || name.endsWith(".yaml")
                                || name.endsWith(".yml") || name.endsWith(".properties") || name.endsWith(".sql")
                                || name.endsWith(".sh") || name.endsWith(".bat") || name.endsWith(".csv")
                                || name.endsWith(".log");
                    })
                    .limit(200)
                    .forEach(p -> {
                        try (var lines = Files.lines(p, StandardCharsets.UTF_8)) {
                            List<String> matches = lines.filter(l -> l.toLowerCase().contains(pattern.toLowerCase()))
                                    .limit(10).collect(Collectors.toList());
                            if (!matches.isEmpty()) {
                                String rel = workspaceUtil.getWorkspaceRoot().relativize(p).toString().replace("\\", "/");
                                sb.append(rel).append(":\n");
                                matches.forEach(l -> sb.append("  ").append(l.trim()).append("\n"));
                                matchCount[0] += matches.size();
                            }
                        } catch (IOException ignored) {
                        }
                    });
        }
        sb.append("----------------------------------------\n共找到 ").append(matchCount[0]).append(" 处匹配");
        return sb.toString();
    }

    private String executeStat(String arg) throws IOException {
        Path target = resolvePath(arg.isEmpty() ? "." : arg);
        BasicFileAttributes attrs = Files.readAttributes(target, BasicFileAttributes.class);
        String rel = workspaceUtil.getWorkspaceRoot().relativize(target).toString().replace("\\", "/");
        return "路径: " + rel + "\n"
                + "类型: " + (Files.isDirectory(target) ? "目录" : "文件") + "\n"
                + "大小: " + formatSize(attrs.size()) + "\n"
                + "创建时间: " + attrs.creationTime() + "\n"
                + "修改时间: " + attrs.lastModifiedTime() + "\n"
                + "是否可读: " + Files.isReadable(target) + "\n"
                + "是否可写: " + Files.isWritable(target);
    }

    private Path resolvePath(String path) {
        return workspaceUtil.resolveSafe(path);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
