package com.cong.fishisland.game.common.model.dto.response;

import com.cong.fishisland.game.common.enums.GameTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一房间状态消息
 * 用于创建房间、加入房间、玩家加入/离开等所有房间状态同步
 *
 * @author cong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomStateResp {

    /**
     * 操作类型: CREATE, JOIN, PLAYER_JOIN, PLAYER_LEAVE, STATE_UPDATE
     */
    private String action;

    /**
     * 房间ID
     */
    private String roomId;

    /**
     * 游戏类型
     */
    private GameTypeEnum gameType;

    /**
     * 当前玩家ID（用于前端判断是否是本人）
     */
    private Long playerId;

    /**
     * 房间玩家列表
     */
    private List<PlayerInfoResp> players;

    /**
     * 房间状态码
     */
    private Integer state;

    /**
     * 地主ID
     */
    private Long landlordId;

    /**
     * 手牌（仅在 JOIN/CREATE 时返回）
     */
    private List<String> handCards;

    /**
     * 底牌
     */
    private List<String> bottomCards;

    /**
     * 通用值（如癞子值等）
     */
    private Integer universalValue;

    /**
     * 准备阶段开始时间
     */
    private Long readyPhaseStartTime;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 错误消息
     */
    private String message;

    // ========== 工厂方法 ==========

    public static RoomStateResp success(String action, String roomId, Long playerId, List<PlayerInfoResp> players) {
        return RoomStateResp.builder()
                .action(action)
                .roomId(roomId)
                .playerId(playerId)
                .players(players)
                .success(true)
                .build();
    }

    public static RoomStateResp error(String message) {
        return RoomStateResp.builder()
                .success(false)
                .message(message)
                .build();
    }
}
