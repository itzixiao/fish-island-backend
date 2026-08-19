package com.cong.fishisland.game.common.model.dto.request;

import lombok.Data;

/**
 * 加入房间请求
 *
 * @author cong
 */
@Data
public class JoinRoomReq {

    /**
     * 房间ID
     */
    private String roomId;

    /**
     * 房间密码（可选）
     */
    private String password;
}
