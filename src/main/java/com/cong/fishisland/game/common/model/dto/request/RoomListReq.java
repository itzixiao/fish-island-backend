package com.cong.fishisland.game.common.model.dto.request;

import lombok.Data;

/**
 * 房间列表请求
 *
 * @author cong
 */
@Data
public class RoomListReq {

    /**
     * 游戏类型（可选），可以是枚举名称字符串或数字
     */
    private String gameType;
}
