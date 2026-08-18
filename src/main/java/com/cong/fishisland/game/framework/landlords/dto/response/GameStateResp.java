package com.cong.fishisland.game.framework.landlords.dto.response;

import com.cong.fishisland.game.common.enums.GameTypeEnum;
import com.cong.fishisland.game.framework.landlords.enums.GamePhaseEnum;
import com.cong.fishisland.game.framework.landlords.model.poker.Poker;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 游戏状态响应
 * 完整描述游戏当前状态，用于同步给客户端
 *
 * @author cong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStateResp {

    // ==================== 房间信息 ====================

    private String roomId;
    private GameTypeEnum gameType;
    private com.cong.fishisland.game.common.model.room.GameRoom.RoomState roomState;
    private GamePhaseEnum phase;
    private Long ownerId;

    // ==================== 地主相关 ====================

    private Long landlordId;
    private List<PokerCardVO> bottomCards;

    // ==================== 当前操作信息 ====================

    private Long currentPlayerId;
    private Long currentRobPlayerId;
    private Integer highestRobScore;
    private Long timeLeft;

    // ==================== 最近出牌信息 ====================

    private List<PokerCardVO> lastPlayedCards;
    private Long lastPlayerId;
    private String lastPlayerName;
    private String lastPatternDesc;

    // ==================== 准备阶段信息 ====================

    private Long readyPhaseStartTime;

    // ==================== 玩家信息 ====================

    private List<PlayerStateVO> players;

    // ==================== 手牌 ====================

    private List<PokerCardVO> handCards;

    // ==================== 癞子相关 ====================

    private Integer universalValue;
    private List<PokerCardVO> universalCards;

    // ==================== 游戏结果 ====================

    private GameResultVO gameResult;

    // ==================== 扑克牌VO ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PokerCardVO {
        private String id;
        private Integer suit;
        private Integer rank;
        private String display;
        private boolean selected;
        private boolean isUniversal;

        public static PokerCardVO from(Poker poker) {
            if (poker == null) return null;
            return PokerCardVO.builder()
                    .id(poker.getId())
                    .suit(poker.getType() != null ? poker.getType().getCode() : null)
                    .rank(poker.getValue() != null ? poker.getValue().getCode() : null)
                    .display(poker.getDisplayName())
                    .selected(poker.isSelected())
                    .isUniversal(poker.isUniversal())
                    .build();
        }

        public static List<PokerCardVO> fromList(List<Poker> pokers) {
            if (pokers == null || pokers.isEmpty()) {
                return new ArrayList<>();
            }
            return pokers.stream()
                    .map(PokerCardVO::from)
                    .collect(Collectors.toList());
        }
    }

    // ==================== 玩家状态VO ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerStateVO {
        private Long userId;
        private String userName;
        private String avatar;
        private Integer cardCount;
        private boolean isLandlord;
        private boolean isCurrentPlayer;
        private boolean isCurrentRobPlayer;
        private boolean isReady;
        private boolean isOnline;
        private boolean isRobotControlled;
        private Integer robScore;
        private String role;
        private List<PokerCardVO> cards;
        private List<PokerCardVO> currentPlayedCards;
    }

    // ==================== 游戏结果VO ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GameResultVO {
        private Long winnerId;
        private String winnerName;
        private boolean isLandlordWin;
        private List<PlayerResultVO> players;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlayerResultVO {
        private Long userId;
        private String userName;
        private Boolean isWinner;
        private Boolean isLandlord;
        private Integer scoreChange;
    }
}
