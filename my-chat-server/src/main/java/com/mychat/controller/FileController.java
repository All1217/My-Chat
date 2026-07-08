package com.mychat.controller;

import com.mychat.common.result.Result;
import com.mychat.entity.FileInfo;
import com.mychat.entity.FileTreeNode;
import com.mychat.entity.po.DocumentMeta;
import com.mychat.mapper.DocumentMetaMapper;
import com.mychat.service.DocumentService;
import com.mychat.service.EmbeddingService;
import com.mychat.utils.WorkspaceUtil;
import com.mychat.vo.DocumentResponseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/ai/file")
public class FileController {
    @Autowired
    private WorkspaceUtil workspaceUtil;
    @Autowired
    private DocumentMetaMapper documentMetaMapper;
    @Autowired
    private DocumentService documentService;
    @Autowired
    private EmbeddingService embeddingService;

    /**
     * 上传文件并将其向量化
     *
     * @param file 文本文件
     * @param kbId 可选：归入的知识库ID
     * @return processing result
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "kbId", required = false) String kbId) {
        log.info("Received document upload: {}, kbId={}", file.getOriginalFilename(), kbId);
        try {
            if (file.isEmpty()) return Result.fail(400, "空文件非法！");
            String filename = file.getOriginalFilename();
            if (filename == null || filename.isEmpty()) return Result.fail(400, "文件名不能为空！");

            try (InputStream inputStream = file.getInputStream()) {
                DocumentService.ProcessedDocument processed = documentService.processDocument(inputStream, filename, kbId);
                int embeddingCount = embeddingService.storeSegments(processed.segments());

                if (kbId != null && !kbId.isEmpty()) {
                    DocumentMeta meta = new DocumentMeta();
                    meta.setId(processed.documentId());
                    meta.setKbId(kbId);
                    meta.setFilename(filename);
                    meta.setFileSize(file.getSize());
                    meta.setFileType(filename.contains(".") ? filename.substring(filename.lastIndexOf('.') + 1) : "");
                    meta.setChunkCount(embeddingCount);
                    meta.setStatus("READY");
                    documentMetaMapper.insert(meta);
                }

                return Result.ok(new DocumentResponseVO(
                        processed.documentId(),
                        filename,
                        "文件上传并向量化成功！",
                        embeddingCount));
            }
        } catch (Exception e) {
            log.error("Failed to process document", e);
            return Result.fail(500, "文件处理失败！");
        }
    }

    /**
     * 删除文档（同步清理向量 + 元数据）
     */
    @PostMapping("/delete")
    public Result<Void> deleteDocument(@RequestBody Map<String, String> body) {
        String id = body.get("id");
        if (id == null || id.isEmpty()) return Result.fail("参数 id 不能为空");
        try {
            DocumentMeta meta = documentMetaMapper.selectById(id);
            if (meta != null) {
                embeddingService.deleteByDocumentId(meta.getId(), meta.getChunkCount());
                documentMetaMapper.deleteById(id);
            }
            return Result.ok();
        } catch (Exception e) {
            log.error("Failed to delete document: {}", id, e);
            return Result.fail(500, "文档删除失败！");
        }
    }

    /**
     * 获取工作区目录树结构
     *
     * @param path 相对路径（空字符串表示根目录）
     * @return 树形节点列表
     */
    @GetMapping("/workspace/tree")
    public Result<List<FileTreeNode>> getDirectoryTree(
            @RequestParam(value = "path", defaultValue = "") String path) {
        try {
            List<FileTreeNode> tree = workspaceUtil.getDirectoryTree(path);
            return Result.ok(tree);
        } catch (Exception e) {
            log.error("获取目录树失败: {}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }

    /* 列出文件目录 */
    @GetMapping("/workspace/list")
    public Result<List<FileInfo>> listDirectory(
            @RequestParam(value = "path", defaultValue = "") String path) {
        try {
            List<FileInfo> files = workspaceUtil.listDirectory(path);
            return Result.ok(files);
        } catch (Exception e) {
            log.error("列出目录失败: {}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }

    /* 预览文件 */
    @GetMapping("/workspace/read")
    public Result<String> readFile(@RequestParam(value = "path") String path) {
        try {
            String content = workspaceUtil.readFileContent(path);
            return Result.ok(content);
        } catch (Exception e) {
            log.error("读取文件失败: {}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 以 Base64 形式读取文件（支持 docx / xlsx / pdf 等二进制格式）
     */
    @GetMapping("/workspace/read/binary")
    public Result<Map<String, String>> readFileAsBase64(@RequestParam("path") String path) {
        try {
            String base64 = workspaceUtil.readFileAsBase64(path);
            String mimeType = workspaceUtil.getMimeType(path);
            Map<String, String> data = new HashMap<>();
            data.put("base64", base64);
            data.put("mimeType", mimeType);
            data.put("name", path.substring(path.lastIndexOf('/') + 1));
            return Result.ok(data);
        } catch (Exception e) {
            log.error("读取文件失败: {}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 新建文件夹
     */
    @PostMapping("/workspace/folder")
    public Result<String> createFolder(
            @RequestParam(value = "path", defaultValue = "") String path,
            @RequestParam("name") String name) {
        try {
            String createdPath = workspaceUtil.createDirectory(path, name);
            return Result.ok(createdPath);
        } catch (Exception e) {
            log.error("创建文件夹失败: {}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 删除文件或文件夹（文件夹会递归删除其内所有内容）
     */
    @PostMapping("/workspace/delete")
    public Result<Void> deleteFileOrFolder(@RequestBody Map<String, String> body) {
        String path = body.get("path");
        if (path == null || path.isEmpty()) {
            return Result.fail("参数 path 不能为空");
        }
        try {
            workspaceUtil.deleteFileOrDirectory(path);
            return Result.ok();
        } catch (Exception e) {
            log.error("删除失败: {}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 重命名文件或文件夹
     */
    @PostMapping("/workspace/rename")
    public Result<String> renameFileOrFolder(@RequestBody Map<String, String> body) {
        String path = body.get("path");
        String newName = body.get("newName");
        if (path == null || path.isEmpty()) {
            return Result.fail("参数 path 不能为空");
        }
        if (newName == null || newName.isEmpty()) {
            return Result.fail("参数 newName 不能为空");
        }
        try {
            String newPath = workspaceUtil.renameFileOrDirectory(path, newName);
            return Result.ok(newPath);
        } catch (Exception e) {
            log.error("重命名失败: {}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 切换工作目录（只在当前会话生效）
     */
    @PostMapping("/workspace/switch")
    public Result<String> switchWorkspace(@RequestBody Map<String, String> body) {
        String path = body.get("path");
        if (path == null || path.isEmpty()) {
            return Result.fail("参数 path 不能为空");
        }
        try {
            return Result.ok(workspaceUtil.switchRoot(path));
        } catch (Exception e) {
            log.error("切换工作目录失败: {}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 导入文件（上传到当前目录）
     */
    @PostMapping("/workspace/import")
    public Result<List<String>> importFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "path", defaultValue = "") String path) {
        if (files == null || files.isEmpty()) {
            return Result.fail("请选择文件");
        }
        List<String> savedPaths = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                String originalFilename = file.getOriginalFilename();
                if (originalFilename == null || originalFilename.isBlank()) {
                    continue;
                }
                String savedPath = workspaceUtil.saveFile(path, originalFilename, file.getInputStream());
                savedPaths.add(savedPath);
            }
            return Result.ok(savedPaths);
        } catch (Exception e) {
            log.error("导入文件失败: {}", e.getMessage());
            return Result.fail(e.getMessage());
        }
    }
}
