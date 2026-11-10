package com.mychat.apps.papertrade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mychat.apps.papertrade.entity.po.PaperOrder;
import org.apache.ibatis.annotations.Mapper;

/** paper_order 表访问。 */
@Mapper
public interface PaperOrderMapper extends BaseMapper<PaperOrder> {
}
