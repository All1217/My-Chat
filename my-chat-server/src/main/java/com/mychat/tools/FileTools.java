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
 * AI 文件操作工具集。每个 @Tool 方法对应一个独立的文件操作，
 * 由 Spring AI 框架自动生成 JSON Schema，AI 模型以类型安全的参数调用。
 * <p>
 * 替代原来的 ShellTool.executeCommand(String) 单一入口方案，
 * 彻底解决空格文件名、换行符、通配符等字符串解析问题。
 * <p>
 * 每个方法的参数由 Spring AI 框架通过 JSON 反序列化传入，
 * AI 模型不再需要自己构造 shell 命令字符串，只需填充 JSON 字段。
 */
@Component
public class FileTools {

    private final WorkspaceUtil workspaceUtil;

    public FileTools(WorkspaceUtil workspaceUtil) {
        this.workspaceUtil = workspaceUtil;
    }

    private Path resolvePath(String path) {
        return workspaceUtil.resolveSafe(path != null ? path : ".");
    }

    @Tool(description = "列出指定目录的内容，包含文件名和大小。不传路径则列出当前目录")
    public String ls(
            @ToolParam(description = "目录路径，如 \"src/main/java\"。不传则列出当前目录", required = false) String path) {
        Path dir = resolvePath(path);
        if (!Files.isDirectory(dir)) {
            return "错误: 路径不是目录";
        }
        Path root = workspaceUtil.getWorkspaceRoot();
        boolean isRoot = root.equals(dir);
        StringBuilder sb = new StringBuilder();
        if (isRoot) {
            sb.append("目录: .  ← 当前工作区根目录 (").append(root.toString()).append(")");
        } else {
            sb.append("目录: ").append(root.relativize(dir).toString().replace("\\", "/"));
        }
        sb.append("\n----------------------------------------\n");
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
                sb.append(String.format("[%s] %s (%s)\n", type, p.getFileName(), formatSize(attrs.size())));
            }
        } catch (IOException e) {
            return "错误: " + e.getMessage();
        }
        return sb.toString();
    }

    @Tool(description = "读取文件内容并返回文本。适用于查看配置、代码、日志等文本文件")
    public String cat(
            @ToolParam(description = "文件路径") String path) {
        Path file = resolvePath(path);
        if (!Files.isRegularFile(file)) {
            return "错误: 目标不是文件";
        }
        try {
            long size = Files.size(file);
            if (size > 1024 * 1024) {
                return "文件过大 (" + formatSize(size) + ")，跳过读取";
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.length() > 20_000) {
                content = content.substring(0, 20_000) + "\n\n... [输出过长，已截断]";
            }
            return content;
        } catch (IOException e) {
            return "错误: " + e.getMessage();
        }
    }

    @Tool(description = "创建新文件或覆盖写入已有文件。内容支持多行文本，会覆盖原文件内容")
    public String write(
            @ToolParam(description = "文件路径") String path,
            @ToolParam(description = "写入的文本内容") String content) {
        try {
            String rel = workspaceUtil.writeFile(path, content);
            Path fullPath = workspaceUtil.resolveSafe(path);
            return "文件写入成功: " + rel + " (绝对路径: " + fullPath.toAbsolutePath() + ") [" + content.length() + " 字符]";
        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }

    @Tool(description = "删除文件或目录（目录会递归删除所有内容）")
    public String rm(
            @ToolParam(description = "要删除的文件或目录路径") String path) {
        try {
            Path target = resolvePath(path);
            if (!Files.exists(target)) {
                return "错误: 路径不存在";
            }
            String type = Files.isDirectory(target) ? "目录" : "文件";
            workspaceUtil.deleteFileOrDirectory(path);
            return type + "已删除";
        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }

    @Tool(description = "移动或重命名文件/目录")
    public String mv(
            @ToolParam(description = "源路径") String source,
            @ToolParam(description = "目标路径") String target) {
        try {
            workspaceUtil.renameFileOrDirectory(source, target);
            return "移动/重命名成功";
        } catch (Exception e) {
            // rename 失败时尝试跨目录 Files.move
            try {
                Path srcPath = resolvePath(source);
                Path dstPath = resolvePath(target);
                Files.createDirectories(dstPath.getParent());
                Files.move(srcPath, dstPath, StandardCopyOption.REPLACE_EXISTING);
                return "移动成功";
            } catch (Exception e2) {
                return "错误: " + e2.getMessage();
            }
        }
    }

    @Tool(description = "复制文件或目录到目标位置")
    public String cp(
            @ToolParam(description = "源路径") String source,
            @ToolParam(description = "目标路径") String target) {
        try {
            workspaceUtil.copyFileOrDirectory(source, target);
            return "复制成功";
        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }

    @Tool(description = "创建新目录（会自动创建所有父目录）")
    public String mkdir(
            @ToolParam(description = "要创建的目录路径") String path) {
        try {
            Path dir = resolvePath(path);
            Files.createDirectories(dir);
            return "目录创建成功";
        } catch (Exception e) {
            return "错误: " + e.getMessage();
        }
    }

    @Tool(description = "查看文件或目录的详细信息：类型、大小、创建时间、修改时间、读写权限")
    public String stat(
            @ToolParam(description = "文件或目录路径，不传则查看当前目录", required = false) String path) {
        try {
            Path target = resolvePath(path);
            BasicFileAttributes attrs = Files.readAttributes(target, BasicFileAttributes.class);
            Path root = workspaceUtil.getWorkspaceRoot();
            String rel;
            if (target.equals(root)) {
                rel = "（当前工作区根目录）";
            } else {
                rel = root.relativize(target).toString().replace("\\", "/");
            }
            return "路径: " + rel + "\n"
                    + "类型: " + (Files.isDirectory(target) ? "目录" : "文件") + "\n"
                    + "大小: " + formatSize(attrs.size()) + "\n"
                    + "创建时间: " + attrs.creationTime() + "\n"
                    + "修改时间: " + attrs.lastModifiedTime() + "\n"
                    + "是否可读: " + Files.isReadable(target) + "\n"
                    + "是否可写: " + Files.isWritable(target);
        } catch (IOException e) {
            return "错误: " + e.getMessage();
        }
    }

    @Tool(description = "在文件中搜索文本内容，返回匹配的文件名和行内容。支持搜索 .txt/.java/.ts/.vue 等文本文件")
    public String grep(
            @ToolParam(description = "搜索的关键词") String pattern,
            @ToolParam(description = "搜索的目录路径，不传则搜索当前目录", required = false) String path) {
        try {
            Path start = resolvePath(path);
            if (!Files.isDirectory(start)) {
                start = start.getParent();
                if (start == null || !Files.isDirectory(start)) {
                    return "错误: 无法确定搜索目录";
                }
            }
            StringBuilder sb = new StringBuilder();
            sb.append("搜索 \"").append(pattern).append("\"\n----------------------------------------\n");
            int[] matchCount = {0};
            try (Stream<Path> paths = Files.walk(start)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> {
                            String name = p.getFileName().toString().toLowerCase();
                            return name.endsWith(".txt") || name.endsWith(".java") || name.endsWith(".ts")
                                    || name.endsWith(".js") || name.endsWith(".vue") || name.endsWith(".py")
                                    || name.endsWith(".html") || name.endsWith(".css") || name.endsWith(".xml")
                                    || name.endsWith(".json") || name.endsWith(".yaml") || name.endsWith(".yml")
                                    || name.endsWith(".properties") || name.endsWith(".sql") || name.endsWith(".md");
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
        } catch (IOException e) {
            return "错误: " + e.getMessage();
        }
    }

    @Tool(description = "以树形结构查看目录内容，展示完整的目录层级关系")
    public String tree(
            @ToolParam(description = "目录路径，不传则列出当前目录树", required = false) String path) {
        try {
            Path dir = resolvePath(path);
            if (!Files.isDirectory(dir)) {
                return "错误: 路径不是目录";
            }
            Path root = workspaceUtil.getWorkspaceRoot();
            boolean isRoot = root.equals(dir);
            String rootLabel = isRoot ? "（当前工作区根目录）" : root.relativize(dir).toString().replace("\\", "/");
            StringBuilder sb = new StringBuilder();
            sb.append("目录树: ").append(rootLabel).append("\n");
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                private int depth = 0;
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    if (depth > 0) {
                        sb.append("  ".repeat(depth - 1)).append("├─ ").append(d.getFileName()).append("/\n");
                    }
                    depth++;
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path f, BasicFileAttributes attrs) {
                    sb.append("  ".repeat(depth)).append("├─ ").append(f.getFileName())
                            .append(" (").append(formatSize(attrs.size())).append(")\n");
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) {
                    depth--;
                    return FileVisitResult.CONTINUE;
                }
            });
            return sb.toString();
        } catch (IOException e) {
            return "错误: " + e.getMessage();
        }
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
