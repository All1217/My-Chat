package com.mychat.utils;

import com.mychat.config.WorkspaceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 工作区 system prompt 构建器：路径规则 + 浅层目录摘要 + 最近修改文件。
 * <p>
 * 低成本骨架感知（depth≤2），不是 Cursor 级全库索引；扫盘失败时仅省略摘要段。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspacePromptBuilder {

    static final int MAX_DEPTH = 2;
    static final int MAX_TREE_LINES = 80;
    static final int MAX_RECENT_FILES = 8;

    private static final Set<String> SKIP_DIR_NAMES = Set.of(
            ".git", "node_modules", "target", "dist", ".idea", ".cursor", "build", "__pycache__");

    private static final DateTimeFormatter ISO_UTC =
            DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneOffset.UTC);

    private final WorkspaceUtil workspaceUtil;

    /**
     * 使用当前 {@link WorkspaceContext}（空则回退默认工作区根）。
     */
    public String build() {
        String workDir = WorkspaceContext.get();
        if (!StringUtils.hasText(workDir)) {
            workDir = workspaceUtil.getWorkspaceRoot().toString();
        }
        return build(workDir);
    }

    /**
     * 按指定工作目录绝对路径构建 system 文案。
     */
    public String build(String workDir) {
        String root = StringUtils.hasText(workDir)
                ? workDir.trim()
                : workspaceUtil.getWorkspaceRoot().toString();
        Path rootPath = Paths.get(root).toAbsolutePath().normalize();
        String name = rootPath.getFileName() != null
                ? rootPath.getFileName().toString()
                : rootPath.toString();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("""
                所有涉及文件的查看、创建、写入、修改、删除、重命名、复制操作，务必积极调用可用工具实际执行。
                不能在回复中假装执行了文件操作。
                当前工作目录: %1$s
                路径规则：所有路径都是相对于当前工作目录的**相对路径**。
                不要把工作目录名 "%2$s" 作为路径前缀。
                ✅ 正确: path="src/components/App.vue"
                ✅ 正确: path="README.md"
                ❌ 错误: path="%2$s/src/components/App.vue"
                ❌ 错误: path="%2$s/README.md"
                上下文策略：若下方已提供「工作区浅层摘要」，询问顶层目录、项目骨架、有哪些文件夹时，
                **优先直接根据摘要回答**，不必先调用 ls/tree；仅当需要完整树、文件内容或写改时再调工具。
                """, rootPath, name));

        try {
            ScanResult scan = shallowScan(rootPath);
            if (!scan.treeLines().isEmpty()) {
                sb.append("\n## 工作区浅层摘要（自动注入，depth≤2；完整树请用工具 tree/ls）\n");
                for (String line : scan.treeLines()) {
                    sb.append(line).append('\n');
                }
                if (scan.treeTruncated()) {
                    sb.append("…(已截断)\n");
                }
            }
            if (!scan.recentFiles().isEmpty()) {
                sb.append("\n## 最近修改（最多 ").append(MAX_RECENT_FILES).append(" 个）\n");
                for (RecentFile rf : scan.recentFiles()) {
                    sb.append("- ").append(rf.relativePath())
                            .append(" (").append(rf.modifiedIso()).append(")\n");
                }
            }
        } catch (Exception e) {
            log.warn("工作区浅层摘要扫描失败，已省略摘要段: {}", e.getMessage());
        }

        return sb.toString().trim();
    }

    private ScanResult shallowScan(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return new ScanResult(List.of(), false, List.of());
        }
        List<String> treeLines = new ArrayList<>();
        List<RecentFile> recent = new ArrayList<>();
        boolean[] truncated = {false};

        walk(root, root, 1, "", treeLines, recent, truncated);

        recent.sort(Comparator.comparing(RecentFile::modifiedEpochMs).reversed());
        if (recent.size() > MAX_RECENT_FILES) {
            recent = new ArrayList<>(recent.subList(0, MAX_RECENT_FILES));
        }
        return new ScanResult(treeLines, truncated[0], recent);
    }

    private void walk(
            Path root,
            Path dir,
            int depth,
            String prefix,
            List<String> treeLines,
            List<RecentFile> recent,
            boolean[] truncated) throws IOException {
        if (depth > MAX_DEPTH) {
            return;
        }
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                children.add(p);
            }
        }
        children.sort(Comparator
                .comparing((Path p) -> !Files.isDirectory(p))
                .thenComparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));

        for (int i = 0; i < children.size(); i++) {
            if (treeLines.size() >= MAX_TREE_LINES) {
                truncated[0] = true;
                return;
            }
            Path child = children.get(i);
            String fileName = child.getFileName().toString();
            boolean isDir = Files.isDirectory(child);
            if (isDir && SKIP_DIR_NAMES.contains(fileName)) {
                continue;
            }

            boolean last = i == children.size() - 1;
            String branch = last ? "└─ " : "├─ ";
            String line = prefix + branch + fileName + (isDir ? "/" : "");
            treeLines.add(line);

            if (!isDir) {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(child, BasicFileAttributes.class);
                    String rel = root.relativize(child).toString().replace("\\", "/");
                    recent.add(new RecentFile(
                            rel,
                            attrs.lastModifiedTime().toMillis(),
                            ISO_UTC.format(Instant.ofEpochMilli(attrs.lastModifiedTime().toMillis())) + "Z"));
                } catch (IOException ignored) {
                    // 单文件属性失败不影响整树
                }
            } else if (depth < MAX_DEPTH) {
                String childPrefix = prefix + (last ? "   " : "│  ");
                walk(root, child, depth + 1, childPrefix, treeLines, recent, truncated);
                if (truncated[0]) {
                    return;
                }
            }
        }
    }

    private record RecentFile(String relativePath, long modifiedEpochMs, String modifiedIso) {
    }

    private record ScanResult(List<String> treeLines, boolean treeTruncated, List<RecentFile> recentFiles) {
    }
}
