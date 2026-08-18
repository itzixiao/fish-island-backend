package com.cong.fishisland.game.common.model.dto.response;

import com.cong.fishisland.game.common.enums.GameTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 房间列表响应
 *
 * @author cong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomListResp {

    /**
     * 房间列表
     */
    private List<RoomItem> rooms;

    /**
     * 房间限制信息
     */
    private RoomRestrictionInfo restriction;

    /**
     * 总数
     */
    private Integer total;

    /**
     * 房间项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomItem {
        private String roomId;
        private GameTypeEnum gameType;
        private Integer playerCount;
        private Integer maxPlayers;
        private Boolean needPassword;
        private Long ownerId;
        private List<PlayerInfoResp> players;
    }

    /**
     * 房间限制信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomRestrictionInfo {
        private String roomId;
        private GameTypeEnum gameType;
        private Integer state;
        private String reason;
    }
}
