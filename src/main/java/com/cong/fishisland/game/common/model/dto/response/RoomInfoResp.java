package com.cong.fishisland.game.common.model.dto.response;

import com.cong.fishisland.game.common.enums.GameTypeEnum;
import com.cong.fishisland.game.common.enums.PlayerRoleEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 房间信息响应
 *
 * @author cong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomInfoResp {

    /**
     * 房间ID
     */
    private String roomId;

    /**
     * 游戏类型
     */
    private GameTypeEnum gameType;

    /**
     * 房间状态
     */
    private Integer state;

    /**
     * 房主ID
     */
    private Long ownerId;

    /**
     * 当前玩家数
     */
    private Integer playerCount;

    /**
     * 最大玩家数
     */
    private Integer maxPlayers;

    /**
     * 是否需要密码
     */
    private Boolean needPassword;

    /**
     * 玩家列表
     */
    private List<PlayerInfoResp> players;

    /**
     * 准备阶段开始时间
     */
    private Long readyPhaseStartTime;

    /**
     * 房间信息
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RoomInfo {
        private String roomId;
        private GameTypeEnum gameType;
        private Integer state;
        private Long ownerId;
        private int maxPlayers;
        private int currentPlayers;
        private boolean hasPassword;
        private List<PlayerInfoResp> players;
    }
}
