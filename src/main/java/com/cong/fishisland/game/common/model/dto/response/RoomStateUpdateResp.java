package com.cong.fishisland.game.common.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 房间状态更新响应
 *
 * @author cong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomStateUpdateResp {

    /**
     * 事件类型
     */
    private String event;

    /**
     * 玩家名称
     */
    private String playerName;

    /**
     * 玩家数量
     */
    private Integer playerCount;

    /**
     * 房间信息
     */
    private RoomInfoResp roomInfo;
}
