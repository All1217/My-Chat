package com.mychat.entity;

import lombok.Data;
import java.util.List;

/**
 * 目录树节点（用于前端 Tree 组件）
 */
@Data
public class FileTreeNode {
    private String name;
    private String path;
    private boolean isDirectory;
    private List<FileTreeNode> children;
}
