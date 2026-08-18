package com.cong.fishisland.game.common.model.dto.request;

import lombok.Data;

/**
 * 创建房间请求
 *
 * @author cong
 */
@Data
public class CreateRoomReq {

    /**
     * 游戏类型（可以是枚举名称字符串或数字）
     */
    private String gameType;

    /**
     * 房间密码（可选）
     */
    private String password;
}
