package com.mychat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mychat.entity.po.DocumentChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档切段表访问。
 */
@Mapper
public interface DocumentChunkMapper extends BaseMapper<DocumentChunk> {
}
