package com.mychat.controller;

import com.mychat.common.result.Result;
import com.mychat.entity.FileInfo;
import com.mychat.entity.FileTreeNode;
import com.mychat.service.impl.VideoService;
import com.mychat.utils.WorkspaceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/file")
public class FileController {
    private final VideoService videoService;
    private final WorkspaceUtil workspaceUtil;

    // 视频总结接口
    @PostMapping("/video/summarize")
    public Flux<String> summarizeVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "prompt", defaultValue = "请详细总结这个视频的内容") String prompt,
            @RequestParam(value = "interval", defaultValue = "2") int intervalSec) {
        return videoService.summarizeVideo(file, prompt, intervalSec);
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
}
