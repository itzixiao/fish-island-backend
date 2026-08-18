package com.cong.fishisland.game.framework.landlords.dto.request;

import lombok.Data;

/**
 * 叫地主请求
 *
 * @author cong
 */
@Data
public class RobLandlordReq {

    /**
     * 叫分动作
     * 0 - 不叫
     * 1 - 叫1分
     * 2 - 叫2分
     * 3 - 叫3分
     */
    private Integer action;
}
