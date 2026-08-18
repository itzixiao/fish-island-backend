package com.cong.fishisland.game.framework.landlords.dto.response;

import com.cong.fishisland.game.framework.landlords.enums.GamePhaseEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 回合通知响应
 * 统一格式：告诉所有人轮到谁了，以及该玩家可以做什么操作
 *
 * @author cong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurnNotifyResp {

    private String event;
    private GamePhaseEnum phase;
    private com.cong.fishisland.game.common.model.room.GameRoom.RoomState roomState;
    private String phaseDesc;

    private Long currentPlayerId;
    private String currentPlayerName;
    private Boolean isCurrentPlayerMe;

    private String action;
    private List<ActionOption> actionOptions;
    private Boolean canPass;
    private Boolean canPlay;

    private Integer timeout;
    private Long startTime;
    private String message;

    private Integer highestScore;
    private Long landlordId;
    private String landlordName;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionOption {
        private Integer value;
        private String name;
        private Boolean enabled;
        private String hint;
    }
}
