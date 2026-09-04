package com.cong.fishisland.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 指数交易风控配置。
 */
@Configuration
@ConfigurationProperties(prefix = "fishisland.index-trade")
@Data
public class IndexTradeProperties {

    /** 单笔买入积分上限。 */
    private long maxSingleBuyAmount = 100_000L;

    /** 单个用户每日累计买入积分上限。 */
    private long maxDailyBuyAmount = 500_000L;
}
