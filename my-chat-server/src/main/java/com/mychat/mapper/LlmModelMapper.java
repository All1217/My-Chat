package com.mychat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mychat.entity.po.LlmModel;
import org.apache.ibatis.annotations.Mapper;

/** llm_model 表访问。 */
@Mapper
public interface LlmModelMapper extends BaseMapper<LlmModel> {
}
