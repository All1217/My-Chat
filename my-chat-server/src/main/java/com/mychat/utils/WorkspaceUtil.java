package com.mychat.utils;

import com.mychat.entity.FileInfo;
import com.mychat.entity.FileTreeNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 工作区文件管理工具类
 * <p>
 * 所有操作限定在 workspaceRoot 目录内，防止路径穿越。
 */
@Slf4j
@Component
public class WorkspaceUtil {

    @Value("${app.workspace.root:./src/main/resources/workspace}")
    private String workspaceRootPath;

    private Path workspaceRoot;

    @PostConstruct
    public void init() {
        this.workspaceRoot = Paths.get(workspaceRootPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(workspaceRoot);
            log.info("工作区初始化完成: {}", workspaceRoot);
        } catch (IOException e) {
            log.error("创建工作区目录失败: {}", workspaceRoot, e);
            throw new RuntimeException("创建工作区目录失败", e);
        }
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
            return workspaceRoot.relativize(target).toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("文件保存失败: {}", target, e);
            throw new RuntimeException("文件保存失败", e);
        }
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

    /** 读取文本文件内容 */
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
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取文件失败: {}", file, e);
            throw new RuntimeException("读取文件失败", e);
        }
    }

    /** 以 Base64 读取文件 */
    public String readFileAsBase64(String relativePath) {
        Path file = resolveSafe(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("目标不是文件: " + relativePath);
        }
        try {
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
            return workspaceRoot.relativize(target).toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("重命名失败: {}", source, e);
            throw new RuntimeException("重命名失败", e);
        }
    }

    private Path resolveSafe(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return workspaceRoot;
        }
        String cleaned = relativePath.replace("\\", "/").replaceAll("^/+", "");
        Path resolved = workspaceRoot.resolve(cleaned).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
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
                node.setPath(workspaceRoot.relativize(p).toString().replace("\\", "/"));
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
        info.setPath(workspaceRoot.relativize(path).toString().replace("\\", "/"));
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
}