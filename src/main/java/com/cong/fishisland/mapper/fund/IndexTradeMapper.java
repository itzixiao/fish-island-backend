package com.cong.fishisland.mapper.fund;

import com.cong.fishisland.model.entity.fund.IndexTradeRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

/**
* @author Shing
* @description 针对表【index_trade_record(指数交易记录表（买入/卖出及T+1结算）)】的数据库操作Mapper
* @createDate 2026-04-07 09:36:38
* @Entity com.cong.fishisland.model.entity.fund.IndexTradeRecord
*/
public interface IndexTradeMapper extends BaseMapper<IndexTradeRecord> {

    /**
     * 查询指定时间段内已完成的买入金额。
     */
    Long sumCompletedBuyAmount(@Param("userId") Long userId,
                               @Param("startTime") Date startTime,
                               @Param("endTime") Date endTime);
}
