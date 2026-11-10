package com.mychat.apps.papertrade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mychat.apps.papertrade.entity.po.PaperPosition;
import org.apache.ibatis.annotations.Mapper;

/** paper_position 表访问。 */
@Mapper
public interface PaperPositionMapper extends BaseMapper<PaperPosition> {
}
