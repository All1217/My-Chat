package com.mychat.apps.market.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mychat.apps.market.entity.po.MarketForecast;
import org.apache.ibatis.annotations.Mapper;

/** market_forecast 表访问。 */
@Mapper
public interface MarketForecastMapper extends BaseMapper<MarketForecast> {
}
