package com.mychat.utils;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
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

    /**
     * 工作区根目录（可通过 application.yaml 配置）
     */
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

    // =============================
    //  目录操作
    // =============================

    /**
     * 在指定父目录下创建子目录
     *
     * @param parentPath 父目录相对路径（相对于工作区根目录），空串表示工作区根目录
     * @param dirName    新目录名（不允许包含 /、\ 等非法字符）
     * @return 创建后的目录绝对路径
     */
    public String createDirectory(String parentPath, String dirName) {
        validateName(dirName);
        Path parent = resolveSafe(parentPath);
        Path newDir = parent.resolve(dirName);

        if (Files.exists(newDir)) {
            throw new IllegalArgumentException("目录已存在: " + newDir.getFileName());
        }
        try {
            Files.createDirectory(newDir);
//            log.info("目录创建成功: {}", newDir);
            return newDir.toAbsolutePath().toString();
        } catch (IOException e) {
            log.error("目录创建失败: {}", newDir, e);
            throw new RuntimeException("目录创建失败", e);
        }
    }

    /**
     * 删除目录（仅当目录为空时可删除）
     *
     * @param relativePath 目录相对路径
     */
    public void deleteDirectory(String relativePath) {
        Path dir = resolveSafe(relativePath);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("目标不是目录: " + relativePath);
        }
        try {
            Files.delete(dir);
            log.info("目录删除成功: {}", dir);
        } catch (DirectoryNotEmptyException e) {
            throw new IllegalArgumentException("目录非空，请先删除子文件/子目录: " + relativePath);
        } catch (IOException e) {
            log.error("目录删除失败: {}", dir, e);
            throw new RuntimeException("目录删除失败", e);
        }
    }

    /**
     * 强制删除目录（递归删除所有子文件和子目录）
     *
     * @param relativePath 目录相对路径
     */
    public void deleteDirectoryForce(String relativePath) {
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

    /**
     * 重命名目录
     *
     * @param relativePath 目录相对路径
     * @param newName      新名称（不允许包含 /、\）
     */
    public String renameDirectory(String relativePath, String newName) {
        validateName(newName);
        Path dir = resolveSafe(relativePath);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("目标不是目录: " + relativePath);
        }
        Path target = dir.getParent().resolve(newName);
        if (Files.exists(target)) {
            throw new IllegalArgumentException("目标名称已存在: " + newName);
        }
        try {
            Files.move(dir, target);
            log.info("目录重命名成功: {} -> {}", dir, target);
            return workspaceRoot.relativize(target).toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("目录重命名失败: {}", dir, e);
            throw new RuntimeException("目录重命名失败", e);
        }
    }

    // =============================
    //  文件操作
    // =============================

    /**
     * 删除文件
     *
     * @param relativePath 文件相对路径
     */
    public void deleteFile(String relativePath) {
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

    /**
     * 重命名文件
     *
     * @param relativePath 文件相对路径
     * @param newName      新名称（不允许包含 /、\）
     */
    public String renameFile(String relativePath, String newName) {
        validateName(newName);
        Path file = resolveSafe(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("目标不是文件: " + relativePath);
        }
        Path target = file.getParent().resolve(newName);
        if (Files.exists(target)) {
            throw new IllegalArgumentException("目标名称已存在: " + newName);
        }
        try {
            Files.move(file, target);
            log.info("文件重命名成功: {} -> {}", file, target);
            return workspaceRoot.relativize(target).toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("文件重命名失败: {}", file, e);
            throw new RuntimeException("文件重命名失败", e);
        }
    }

    /**
     * 导入文件（从磁盘其他位置复制到工作区）
     *
     * @param sourcePath      源文件绝对路径
     * @param targetParentDir 目标父目录相对路径（空串=工作区根目录）
     * @return 导入后的文件相对路径
     */
    public String importFile(String sourcePath, String targetParentDir) {
        Path source = Paths.get(sourcePath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("源文件不存在或不是文件: " + sourcePath);
        }
        Path parent = resolveSafe(targetParentDir);
        Path target = parent.resolve(source.getFileName().toString());

        // 若同名文件已存在，自动添加后缀
        if (Files.exists(target)) {
            String baseName = getBaseName(source.getFileName().toString());
            String extension = getExtension(source.getFileName().toString());
            String newName = baseName + "_" + System.currentTimeMillis() +
                    (extension.isEmpty() ? "" : "." + extension);
            target = parent.resolve(newName);
        }
        try {
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
            log.info("文件导入成功: {} -> {}", source, target);
            return workspaceRoot.relativize(target).toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("文件导入失败: {}", source, e);
            throw new RuntimeException("文件导入失败", e);
        }
    }

    /**
     * 导入目录（递归复制整个目录到工作区）
     *
     * @param sourcePath      源目录绝对路径
     * @param targetParentDir 目标父目录相对路径（空串=工作区根目录）
     * @return 导入后的目录相对路径
     */
    public String importDirectory(String sourcePath, String targetParentDir) {
        Path source = Paths.get(sourcePath).toAbsolutePath().normalize();
        if (!Files.isDirectory(source)) {
            throw new IllegalArgumentException("源目录不存在或不是目录: " + sourcePath);
        }
        Path parent = resolveSafe(targetParentDir);
        Path target = parent.resolve(source.getFileName().toString());

        if (Files.exists(target)) {
            String suffix = "_" + System.currentTimeMillis();
            target = parent.resolve(source.getFileName().toString() + suffix);
        }

        // 将 target 捕获为 effectively final 变量，供内部类使用
        final Path finalTarget = target;

        try {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Path relative = source.relativize(dir);
                    Path targetDir = finalTarget.resolve(relative);
                    Files.createDirectories(targetDir);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path relative = source.relativize(file);
                    Path targetFile = finalTarget.resolve(relative);
                    Files.copy(file, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
                    return FileVisitResult.CONTINUE;
                }
            });
            log.info("目录导入成功: {} -> {}", source, finalTarget);
            return workspaceRoot.relativize(finalTarget).toString().replace("\\", "/");
        } catch (IOException e) {
            log.error("目录导入失败: {}", source, e);
            throw new RuntimeException("目录导入失败", e);
        }
    }

    // =============================
    //  查询/读取操作
    // =============================

    /**
     * 获取目录树结构
     *
     * @param relativePath 起始目录相对路径（空串=工作区根目录）
     * @return 树形节点列表
     */
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

    /**
     * 列出指定目录下的直接子项（不递归）
     *
     * @param relativePath 目录相对路径
     * @return 文件/目录信息列表
     */
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
                    .comparingInt((FileInfo f) -> f.isDirectory ? 0 : 1)
                    .thenComparing(f -> f.name, String.CASE_INSENSITIVE_ORDER));
            return result;
        } catch (IOException e) {
            log.error("列出目录失败: {}", dir, e);
            throw new RuntimeException("列出目录失败", e);
        }
    }

    /**
     * 读取文件内容（仅支持文本类型文件）
     *
     * @param relativePath 文件相对路径
     * @return 文件文本内容
     */
    public String readFileContent(String relativePath) {
        Path file = resolveSafe(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("目标不是文件: " + relativePath);
        }
        String extension = getExtension(file.getFileName().toString()).toLowerCase();
        // 仅允许文本格式
        Set<String> allowedExtensions = Set.of(
                "txt", "md", "json", "xml", "yaml", "yml",
                "properties", "csv", "log", "sql", "java",
                "py", "js", "ts", "html", "css", "less", "scss",
                "vue", "sh", "bat"
        );
        if (!allowedExtensions.contains(extension) && !"".equals(extension)) {
            throw new IllegalArgumentException("不支持预览该文件类型: ." + extension + "，仅支持文本格式");
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取文件失败: {}", file, e);
            throw new RuntimeException("读取文件失败", e);
        }
    }

    /**
     * 判断文件是否为文本类型（可预览）
     */
    public boolean isPreviewable(String relativePath) {
        Path file = resolveSafe(relativePath);
        String extension = getExtension(file.getFileName().toString()).toLowerCase();
        Set<String> allowed = Set.of(
                "txt", "md", "json", "xml", "yaml", "yml",
                "properties", "csv", "log", "sql", "java",
                "py", "js", "ts", "html", "css", "less", "scss",
                "vue", "sh", "bat", "pdf"
        );
        return allowed.contains(extension) || "".equals(extension);
    }

    /**
     * 获取文件信息
     */
    public FileInfo getFileInfo(String relativePath) {
        Path path = resolveSafe(relativePath);
        return buildFileInfo(path);
    }

    // =============================
    //  内部辅助方法
    // =============================

    /**
     * 安全解析相对路径，防止路径穿越
     */
    private Path resolveSafe(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return workspaceRoot;
        }
        // 清理路径，移除可能的前导 /
        String cleaned = relativePath.replace("\\", "/").replaceAll("^/+", "");
        Path resolved = workspaceRoot.resolve(cleaned).normalize();
        if (!resolved.startsWith(workspaceRoot)) {
            throw new SecurityException("非法的路径访问: " + relativePath);
        }
        return resolved;
    }

    /**
     * 校验名称（文件/目录名不允许包含路径分隔符）
     */
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

    /**
     * 获取文件扩展名（不含 .）
     */
    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1);
    }

    /**
     * 获取文件名（不含扩展名）
     */
    private String getBaseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) return fileName;
        return fileName.substring(0, dot);
    }

    /**
     * 递归构建目录树
     */
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
                .comparingInt((FileTreeNode n) -> n.isDirectory ? 0 : 1)
                .thenComparing(n -> n.name, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /**
     * 构建文件/目录信息对象
     */
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
            // 忽略属性读取失败
        }
        return info;
    }

    private String formatTime(Instant instant) {
        LocalDateTime dt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    // =============================
    //  内部数据类
    // =============================

    /**
     * 文件/目录基本信息
     */
    public static class FileInfo {
        private String name;
        private String path;          // 相对于工作区的路径
        private boolean isDirectory;
        private long size;
        private String createdAt;
        private String modifiedAt;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public boolean isDirectory() { return isDirectory; }
        public void setDirectory(boolean directory) { isDirectory = directory; }

        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }

        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

        public String getModifiedAt() { return modifiedAt; }
        public void setModifiedAt(String modifiedAt) { this.modifiedAt = modifiedAt; }
    }

    /**
     * 目录树节点（用于前端 Tree 组件）
     */
    public static class FileTreeNode {
        private String name;
        private String path;
        private boolean isDirectory;
        private List<FileTreeNode> children;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public boolean isDirectory() { return isDirectory; }
        public void setDirectory(boolean directory) { isDirectory = directory; }

        public List<FileTreeNode> getChildren() { return children; }
        public void setChildren(List<FileTreeNode> children) { this.children = children; }
    }
}