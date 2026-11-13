package com.mychat.apps.papertrade.ledger.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mychat.apps.papertrade.ledger.entity.po.PaperAccount;
import org.apache.ibatis.annotations.Mapper;

/** paper_account 表访问。 */
@Mapper
public interface PaperAccountMapper extends BaseMapper<PaperAccount> {
}
