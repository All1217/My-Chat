package com.mychat.entity;

import lombok.Data;

/**
 * 文件/目录基本信息
 */
@Data
public class FileInfo {
    private String name;
    private String path;          // 相对于工作区的路径
    private boolean isDirectory;
    private long size;
    private String createdAt;
    private String modifiedAt;
}
