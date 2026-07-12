package com.mychat.utils;

import com.mychat.config.WorkspaceContext;
import com.mychat.entity.FileInfo;
import com.mychat.entity.FileTreeNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作区文件管理工具类
 * <p>
 * 所有操作限定在 workspaceRoot 目录内，防止路径穿越。
 * 支持运行时切换工作目录（不持久化）。
 */
@Slf4j
@Component
public class WorkspaceUtil {

    @Value("${app.workspace.root:./src/main/resources/workspace}")
    private String workspaceRootPath;

    private Path workspaceRoot;

    /** 禁止切换到的系统关键目录（不区分大小写） */
    private static final Set<String> BLOCKED_PATHS = Set.of(
            "C:\\Windows", "C:\\Program Files", "C:\\Program Files (x86)",
            "C:\\System32", "C:\\Users\\Default",
            "/etc", "/usr", "/bin", "/boot", "/dev", "/proc", "/sys",
            "/System", "/Library", "/Applications"
    );

    /** 文本读取大小上限（10MB），超出则截断 */
    private static final long MAX_READ_SIZE = 10 * 1024 * 1024;

    /** Base64 预览大小上限（50MB），超出拒绝 */
    private static final long MAX_BASE64_SIZE = 50 * 1024 * 1024;

    @PostConstruct
    public void init() {
        Path configured = Paths.get(workspaceRootPath).toAbsolutePath().normalize();
        // 校验配置的根目录是否可用（存在、是目录、非系统禁止目录）
        if (Files.exists(configured) && Files.isDirectory(configured) && !isBlockedPath(configured)) {
            this.workspaceRoot = configured;
        } else {
            Path fallback = Paths.get("src/main/resources/workspace").toAbsolutePath().normalize();
            log.warn("配置的工作区目录不可用 ({}), 使用默认目录: {}", configured, fallback);
            this.workspaceRoot = fallback;
        }
        try {
            Files.createDirectories(this.workspaceRoot);
            log.info("工作区初始化完成: {}", this.workspaceRoot);
        } catch (IOException e) {
            log.error("创建工作区目录失败: {}", this.workspaceRoot, e);
            throw new RuntimeException("创建工作区目录失败", e);
        }
    }

    /** 获取当前有效的工作区根目录（优先 ThreadLocal，否则默认 workspaceRoot） */
    private Path getEffectiveRoot() {
        String contextWorkDir = WorkspaceContext.get();
        return (contextWorkDir != null)
                ? Paths.get(contextWorkDir).toAbsolutePath().normalize()
                : workspaceRoot;
    }

    /** 获取当前工作区根目录 */
    public Path getWorkspaceRoot() {
        return getEffectiveRoot();
    }

    /** 判断路径是否被列入系统禁止目录黑名单 */
    private static boolean isBlockedPath(Path path) {
        String upper = path.toAbsolutePath().normalize().toString().toUpperCase();
        for (String blocked : BLOCKED_PATHS) {
            if (upper.startsWith(blocked.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 校验路径是否可用作工作区目录（无副作用，不切换）。
     * 用于前端确认前的预校验。
     */
    public void validateWorkspacePath(String path) {
        Path resolved = Paths.get(path).toAbsolutePath().normalize();
        if (!Files.exists(resolved)) {
            throw new IllegalArgumentException("目录不存在: " + path);
        }
        if (!Files.isDirectory(resolved)) {
            throw new IllegalArgumentException("路径不是目录: " + path);
        }
        if (isBlockedPath(resolved)) {
            throw new SecurityException("该目录不可作为工作区，请更换其他目录！");
        }
    }

    /**
     * 切换当前请求线程的工作目录（只设 ThreadLocal，不修改全局单例）。
     * 用于 FileController 的 ad-hoc 目录切换，会话级工作目录由 WorkspaceContext 在 Controller 中设置。
     */
    public String switchRoot(String newPath) {
        Path resolved = Paths.get(newPath).toAbsolutePath().normalize();
        if (!Files.exists(resolved)) {
            throw new IllegalArgumentException("目录不存在: " + newPath);
        }
        if (!Files.isDirectory(resolved)) {
            throw new IllegalArgumentException("路径不是目录: " + newPath);
        }
        if (isBlockedPath(resolved)) {
            throw new SecurityException("禁止切换到系统目录: " + resolved);
        }
        WorkspaceContext.set(resolved.toString());
        log.info("工作目录(ThreadLocal)已切换至: {}", resolved);
        return resolved.toString();
    }

    // ==================== 公共方法 ====================

    /** 在指定父目录下创建子目录 */
    public String createDirectory(String parentPath, String dirName) {
        validateName(dirName);
        Path parent = resolveSafe(parentPath);
        Path newDir = parent.resolve(dirName);
        if (Files.exists(newDir)) {
            throw new IllegalArgumentException("目录已存在: " + newDir.getFileName());
        }
        try {
            Files.createDirectory(newDir);
            return newDir.toAbsolutePath().toString();
        } catch (IOException e) {
            log.error("目录创建失败: {}", newDir, e);
            throw new RuntimeException("目录创建失败", e);
        }
    }

    /** 删除文件或目录（目录递归删除） */
    public void deleteFileOrDirectory(String relativePath) {
        Path path = resolveSafe(relativePath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("路径不存在: " + relativePath);
        }
        if (Files.isDirectory(path)) {
            deleteDirectoryForce(relativePath);
        } else {
            deleteFile(relativePath);
        }
    }

    /** 重命名文件或目录 */
    public String renameFileOrDirectory(String relativePath, String newName) {
        validateName(newName);
        Path path = resolveSafe(relativePath);
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("路径不存在: " + relativePath);
        }
        if (Files.isDirectory(path)) {
            return moveAndGetRelative(path, newName);
        } else {
            return moveAndGetRelative(path, newName);
        }
    }

    /** 保存上传的文件到指定目录 */
    public String saveFile(String parentPath, String originalFilename, InputStream inputStream) {
        validateName(originalFilename);
        Path parent = resolveSafe(parentPath);
        Path target = parent.resolve(originalFilename);
        if (Files.exists(target)) {
            String baseName = getBaseName(originalFilename);
            String extension = getExtension(originalFilename);
            String newName = baseName + "_" + System.currentTimeMillis() +
                    (extension.isEmpty() ? "" : "." + extension);
            target = parent.resolve(newName);
        }
        try {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            log.info("文件保存成功: {}", target);
            return getEffectiveRoot().relativize(target).toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("文件保存失败: {}", target, e);
            throw new RuntimeException("文件保存失败", e);
        }
    }

    /** 创建或覆盖写入文本文件（自动创建父目录） */
    public String writeFile(String relativePath, String content) {
        Path file = resolveSafe(relativePath);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
            log.info("文件写入成功: {} ({} bytes)", file, content.length());
            return getEffectiveRoot().relativize(file).toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("文件写入失败: {}", file, e);
            throw new RuntimeException("文件写入失败", e);
        }
    }

    /** 复制文件或目录 */
    public String copyFileOrDirectory(String sourcePath, String targetPath) {
        Path source = resolveSafe(sourcePath);
        Path target = resolveSafe(targetPath);
        if (!Files.exists(source)) {
            throw new IllegalArgumentException("源路径不存在: " + sourcePath);
        }
        try {
            Files.createDirectories(target.getParent());
            if (Files.isDirectory(source)) {
                copyDirectoryRecursively(source, target);
            } else {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("复制成功: {} -> {}", source, target);
            return getEffectiveRoot().relativize(target).toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("复制失败: {} -> {}", source, target, e);
            throw new RuntimeException("复制失败", e);
        }
    }

    private void copyDirectoryRecursively(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /** 获取目录树 */
    public List<FileTreeNode> getDirectoryTree(String relativePath) {
        Path root = resolveSafe(relativePath);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("路径不是目录: " + relativePath);
        }
        try {
            return buildTree(root);
        } catch (IOException e) {
            log.error("读取目录树失败: {}", root, e);
            throw new RuntimeException("读取目录树失败", e);
        }
    }

    /** 列出目录下的直接子项 */
    public List<FileInfo> listDirectory(String relativePath) {
        Path dir = resolveSafe(relativePath);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("路径不是目录: " + relativePath);
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            List<FileInfo> result = new ArrayList<>();
            for (Path p : stream) {
                result.add(buildFileInfo(p));
            }
            result.sort(Comparator
                    .comparingInt((FileInfo f) -> f.isDirectory() ? 0 : 1)
                    .thenComparing(FileInfo::getName, String.CASE_INSENSITIVE_ORDER));
            return result;
        } catch (IOException e) {
            log.error("列出目录失败: {}", dir, e);
            throw new RuntimeException("列出目录失败", e);
        }
    }

    /** 懒加载：获取指定路径的直接子节点（仅一层，用于 el-tree lazy） */
    public List<FileTreeNode> listDirectoryAsTree(String relativePath) {
        Path dir = resolveSafe(relativePath);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("路径不是目录: " + relativePath);
        }
        List<FileTreeNode> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                FileTreeNode node = new FileTreeNode();
                node.setName(p.getFileName().toString());
                node.setPath(getEffectiveRoot().relativize(p).toString().replace("\\", "/"));
                node.setDirectory(Files.isDirectory(p));
                result.add(node);
            }
        } catch (IOException e) {
            log.error("懒加载目录失败: {}", dir, e);
            throw new RuntimeException("懒加载目录失败", e);
        }
        result.sort(Comparator
                .comparingInt((FileTreeNode n) -> n.isDirectory() ? 0 : 1)
                .thenComparing(FileTreeNode::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /** 读取文本文件内容（超 10MB 截断并提示） */
    public String readFileContent(String relativePath) {
        Path file = resolveSafe(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("目标不是文件: " + relativePath);
        }
        String extension = getExtension(file.getFileName().toString()).toLowerCase();
        Set<String> allowedExtensions = Set.of(
                "txt", "md", "json", "xml", "yaml", "yml",
                "properties", "csv", "log", "sql", "java",
                "py", "js", "ts", "html", "css", "less", "scss",
                "vue", "sh", "bat"
        );
        if (!allowedExtensions.contains(extension) && !extension.isEmpty()) {
            throw new IllegalArgumentException("不支持预览该文件类型: ." + extension + "，仅支持文本格式");
        }
        try {
            long size = Files.size(file);
            if (size > MAX_READ_SIZE) {
                StringBuilder sb = new StringBuilder((int) MAX_READ_SIZE);
                try (InputStream is = Files.newInputStream(file);
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    char[] buf = new char[8192];
                    int total = 0;
                    int n;
                    while ((n = reader.read(buf, 0,
                            (int) Math.min(buf.length, MAX_READ_SIZE - total))) != -1
                            && total < MAX_READ_SIZE) {
                        sb.append(buf, 0, n);
                        total += n;
                    }
                }
                sb.append("\n\n... [文件过大，仅显示前 ")
                        .append(formatSize(MAX_READ_SIZE))
                        .append("]");
                return sb.toString();
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取文件失败: {}", file, e);
            throw new RuntimeException("读取文件失败", e);
        }
    }

    /** 以 Base64 读取文件（超 50MB 拒绝） */
    public String readFileAsBase64(String relativePath) {
        Path file = resolveSafe(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("目标不是文件: " + relativePath);
        }
        try {
            if (Files.size(file) > MAX_BASE64_SIZE) {
                throw new IllegalArgumentException("文件过大，不支持 Base64 读取（最大 50MB）");
            }
            byte[] bytes = Files.readAllBytes(file);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (IOException e) {
            log.error("读取文件失败: {}", file, e);
            throw new RuntimeException("读取文件失败", e);
        }
    }

    /** 获取文件的 MIME 类型 */
    public String getMimeType(String relativePath) {
        String ext = getExtension(relativePath.substring(relativePath.lastIndexOf('/') + 1)).toLowerCase();
        Map<String, String> mimeMap = Map.ofEntries(
                Map.entry("pdf", "application/pdf"),
                Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
                Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                Map.entry("doc", "application/msword"),
                Map.entry("xls", "application/vnd.ms-excel"),
                Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
                Map.entry("png", "image/png"),
                Map.entry("jpg", "image/jpeg"),
                Map.entry("jpeg", "image/jpeg"),
                Map.entry("gif", "image/gif"),
                Map.entry("svg", "image/svg+xml"),
                Map.entry("webp", "image/webp")
        );
        return mimeMap.getOrDefault(ext, "application/octet-stream");
    }

    /**
     * 列出文件系统根目录（如 C:\、D:\ 或 /）
     */
    public List<String> listRoots() {
        File[] roots = File.listRoots();
        return Arrays.stream(roots)
                .map(File::getPath)
                .collect(Collectors.toList());
    }

    /**
     * 浏览绝对路径下的直接子目录（用于目录选择器）
     * 不依赖 getEffectiveRoot()，直接操作绝对路径
     */
    public List<FileInfo> listAbsoluteDirectory(String absolutePath) {
        Path dir = Paths.get(absolutePath).toAbsolutePath().normalize();
        if (!Files.exists(dir)) {
            throw new IllegalArgumentException("目录不存在: " + absolutePath);
        }
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("路径不是目录: " + absolutePath);
        }
        // 安全检查：禁止浏览系统关键目录
        if (isBlockedPath(dir)) {
            throw new SecurityException("禁止浏览系统目录: " + dir);
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            List<FileInfo> result = new ArrayList<>();
            for (Path p : stream) {
                if (!Files.isDirectory(p)) continue;
                FileInfo info = new FileInfo();
                info.setName(p.getFileName().toString());
                info.setPath(p.toAbsolutePath().toString().replace("\\", "/"));
                info.setDirectory(true);
                result.add(info);
            }
            result.sort(Comparator.comparing(FileInfo::getName, String.CASE_INSENSITIVE_ORDER));
            return result;
        } catch (IOException e) {
            log.error("浏览目录失败: {}", dir, e);
            throw new RuntimeException("浏览目录失败", e);
        }
    }

    /**
     * 为绝对路径输入提供目录联想建议（用于 el-autocomplete 防抖搜索）。
     * 纯绝对路径模式，不涉及 workspaceRoot。
     * <p>
     * 逻辑：
     * 1. query 末尾无分隔符 → 取父目录 + 最后一段做前缀过滤
     * 2. query 末尾有分隔符 → 直接列出该目录的子目录（无前缀过滤）
     * 3. 父目录不存在 → 返回空
     */
    public List<String> suggestDirectories(String query) {
        if (query == null || query.isBlank()) return Collections.emptyList();
        String normalized = query.replace("/", "\\");
        boolean endsWithSep = normalized.endsWith("\\");

        // 去掉末尾分隔符以便推断父目录
        String parentStr = endsWithSep ? normalized.substring(0, normalized.length() - 1) : normalized;
        Path parent = Paths.get(parentStr).toAbsolutePath().normalize();

        // 末尾有分隔符 → parent 是已存在的目录，列出其全部子目录
        if (endsWithSep) {
            if (!Files.isDirectory(parent)) return Collections.emptyList();
            return listSubDirectories(parent, "");
        }

        // 末尾无分隔符 → 尝试直接作为目录
        if (Files.isDirectory(parent)) {
            return listSubDirectories(parent, "");
        }

        // 路径不存在 → 取父目录 + 最后一段作为前缀
        Path dir = parent.getParent();
        if (dir == null || !Files.isDirectory(dir)) return Collections.emptyList();
        String prefix = parent.getFileName().toString();
        return listSubDirectories(dir, prefix);
    }

    /** 列出 dir 下的子目录，按 prefix 做大小写不敏感前缀过滤 */
    private List<String> listSubDirectories(Path dir, String prefix) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            List<String> result = new ArrayList<>();
            for (Path p : stream) {
                if (!Files.isDirectory(p)) continue;
                // 跳过系统禁止目录（如 C:\Windows）
                if (isBlockedPath(p)) continue;
                String name = p.getFileName().toString();
                if (prefix.isEmpty() || name.regionMatches(true, 0, prefix, 0, prefix.length())) {
                    result.add(p.toAbsolutePath().normalize().toString() + "\\");
                }
            }
            result.sort(String.CASE_INSENSITIVE_ORDER);
            return result;
        } catch (IOException e) {
            log.warn("目录联想扫描失败: {}", dir, e);
            return Collections.emptyList();
        }
    }

    // ==================== 私有方法 ====================

    private void deleteFile(String relativePath) {
        Path file = resolveSafe(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("目标不是文件: " + relativePath);
        }
        try {
            Files.delete(file);
            log.info("文件删除成功: {}", file);
        } catch (IOException e) {
            log.error("文件删除失败: {}", file, e);
            throw new RuntimeException("文件删除失败", e);
        }
    }

    private void deleteDirectoryForce(String relativePath) {
        Path dir = resolveSafe(relativePath);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("目标不是目录: " + relativePath);
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
            log.info("目录(递归)删除成功: {}", dir);
        } catch (IOException e) {
            log.error("目录递归删除失败: {}", dir, e);
            throw new RuntimeException("目录递归删除失败", e);
        }
    }

    /** 重命名文件或目录（调用方已校验名称并解析 path） */
    private String moveAndGetRelative(Path source, String newName) {
        if (!Files.isRegularFile(source) && !Files.isDirectory(source)) {
            throw new IllegalArgumentException("目标不存在: " + source.getFileName());
        }
        Path target = source.getParent().resolve(newName);
        if (Files.exists(target)) {
            throw new IllegalArgumentException("目标名称已存在: " + newName);
        }
        try {
            Files.move(source, target);
            log.info("重命名成功: {} -> {}", source, target);
            return getEffectiveRoot().relativize(target).toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("重命名失败: {}", source, e);
            throw new RuntimeException("重命名失败", e);
        }
    }

    /**
     * 解析相对路径为绝对路径，限制在当前有效 workspaceRoot 内。
     */
    public Path resolveSafe(String relativePath) {
        Path root = getEffectiveRoot();
        if (relativePath == null || relativePath.isEmpty()) {
            return root;
        }
        String cleaned = relativePath.replace("\\", "/").replaceAll("^/+", "");
        Path resolved = root.resolve(cleaned).normalize();
        if (!resolved.startsWith(root)) {
            throw new SecurityException("非法的路径访问: " + relativePath);
        }
        return resolved;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("名称不能为空");
        }
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new IllegalArgumentException("名称包含非法字符: " + name);
        }
        if (name.startsWith(".") && name.length() == 1) {
            throw new IllegalArgumentException("名称不能为 '.'");
        }
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1);
    }

    private String getBaseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) return fileName;
        return fileName.substring(0, dot);
    }

    private List<FileTreeNode> buildTree(Path dir) throws IOException {
        List<FileTreeNode> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                FileTreeNode node = new FileTreeNode();
                node.setName(p.getFileName().toString());
                node.setPath(getEffectiveRoot().relativize(p).toString().replace("\\", "/"));
                node.setDirectory(Files.isDirectory(p));
                if (Files.isDirectory(p)) {
                    node.setChildren(buildTree(p));
                }
                result.add(node);
            }
        }
        result.sort(Comparator
                .comparingInt((FileTreeNode n) -> n.isDirectory() ? 0 : 1)
                .thenComparing(FileTreeNode::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private FileInfo buildFileInfo(Path path) {
        FileInfo info = new FileInfo();
        info.setName(path.getFileName().toString());
        info.setPath(getEffectiveRoot().relativize(path).toString().replace("\\", "/"));
        info.setDirectory(Files.isDirectory(path));
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            info.setSize(attrs.size());
            info.setCreatedAt(formatTime(attrs.creationTime().toInstant()));
            info.setModifiedAt(formatTime(attrs.lastModifiedTime().toInstant()));
        } catch (IOException e) {
            // ignore
        }
        return info;
    }

    private String formatTime(Instant instant) {
        LocalDateTime dt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}