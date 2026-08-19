package com.cong.fishisland.game.framework.landlords.dto.request;

import lombok.Data;

/**
 * 出牌请求
 *
 * @author cong
 */
@Data
public class PlayCardsReq {

    /**
     * 出的牌 (牌ID列表)
     */
    private java.util.List<String> pokers;
}
