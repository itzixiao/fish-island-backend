package com.cong.fishisland.game.framework.landlords.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 斗地主玩家信息响应
 * 包含斗地主特有的字段
 *
 * @author cong
 */
@Data
@Builder
public class LandlordsPlayerInfoResp {

    /**
     * 是否是地主
     */
    private Boolean isLandlord;

    /**
     * 叫分
     */
    private Integer robScore;

    /**
     * 手牌数量
     */
    private Integer cardCount;
}
