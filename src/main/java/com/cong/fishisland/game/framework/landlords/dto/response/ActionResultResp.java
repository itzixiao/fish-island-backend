package com.cong.fishisland.game.framework.landlords.dto.response;

import com.cong.fishisland.game.framework.landlords.enums.GameActionEnum;
import com.cong.fishisland.game.framework.landlords.enums.GamePhaseEnum;
import com.cong.fishisland.game.framework.landlords.model.poker.Poker;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 操作结果响应
 * 统一格式：告诉所有人某个玩家做了什么操作
 *
 * @author cong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionResultResp {

    private String event;
    private GamePhaseEnum phase;
    private com.cong.fishisland.game.common.model.room.GameRoom.RoomState roomState;

    private Long playerId;
    private String playerName;

    private String action;
    private Integer actionValue;
    private String result;
    private String message;

    private List<String> pokerIds;
    private String patternDesc;
    private Boolean isBomb;
    private Boolean isMaxCard;

    private Integer highestScore;
    private String robScoreDesc;

    private Long landlordId;
    private String landlordName;
    private Long winnerId;
    private String winnerName;
    private Boolean isLandlordWin;
    private String winTeam;
    private List<PlayerResultVO> players;

    public static ActionResultResp robResult(Long playerId, String playerName,
                                            Integer actionValue, String robScoreDesc,
                                            Integer highestScore, String message) {
        return ActionResultResp.builder()
                .event(GameActionEnum.ROB_RESULT.getCode())
                .phase(GamePhaseEnum.ROBBING)
                .roomState(com.cong.fishisland.game.common.model.room.GameRoom.RoomState.ROBBING)
                .playerId(playerId)
                .playerName(playerName)
                .action(GameActionEnum.ROB.getCode())
                .actionValue(actionValue)
                .result(robScoreDesc)
                .message(message)
                .highestScore(highestScore)
                .robScoreDesc(robScoreDesc)
                .build();
    }

    public static ActionResultResp playResult(Long playerId, String playerName,
                                            List<Poker> pokers, String patternDesc,
                                            Boolean isBomb, String message) {
        List<String> pokerIds = pokers.stream()
                .map(Poker::getId)
                .collect(Collectors.toList());

        return ActionResultResp.builder()
                .event(GameActionEnum.PLAY_RESULT.getCode())
                .phase(GamePhaseEnum.PLAYING)
                .roomState(com.cong.fishisland.game.common.model.room.GameRoom.RoomState.PLAYING)
                .playerId(playerId)
                .playerName(playerName)
                .action(GameActionEnum.PLAY.getCode())
                .result(patternDesc)
                .message(message)
                .pokerIds(pokerIds)
                .patternDesc(patternDesc)
                .isBomb(isBomb)
                .build();
    }

    public static ActionResultResp passResult(Long playerId, String playerName, String message) {
        return ActionResultResp.builder()
                .event(GameActionEnum.PASS_RESULT.getCode())
                .phase(GamePhaseEnum.PLAYING)
                .roomState(com.cong.fishisland.game.common.model.room.GameRoom.RoomState.PLAYING)
                .playerId(playerId)
                .playerName(playerName)
                .action(GameActionEnum.PASS.getCode())
                .result("不出")
                .message(message)
                .build();
    }

    public static ActionResultResp landlordConfirmed(Long landlordId, String landlordName,
                                                   List<Poker> bottomCards, String message) {
        List<String> bottomPokerIds = bottomCards.stream()
                .map(Poker::getId)
                .collect(Collectors.toList());

        return ActionResultResp.builder()
                .event(GameActionEnum.LANDLORD_CONFIRMED.getCode())
                .phase(GamePhaseEnum.PLAYING)
                .roomState(com.cong.fishisland.game.common.model.room.GameRoom.RoomState.PLAYING)
                .playerId(landlordId)
                .playerName(landlordName)
                .action(GameActionEnum.LANDLORD.getCode())
                .result(landlordName + "成为地主")
                .message(message)
                .landlordId(landlordId)
                .landlordName(landlordName)
                .pokerIds(bottomPokerIds)
                .build();
    }

    public static ActionResultResp gameOver(Long winnerId, String winnerName,
                                          Boolean isLandlordWin, String winTeam,
                                          List<PlayerResultVO> players, String message) {
        return ActionResultResp.builder()
                .event(GameActionEnum.GAME_OVER.getCode())
                .phase(GamePhaseEnum.ENDING)
                .roomState(com.cong.fishisland.game.common.model.room.GameRoom.RoomState.ENDING)
                .winnerId(winnerId)
                .winnerName(winnerName)
                .isLandlordWin(isLandlordWin)
                .winTeam(winTeam)
                .result(message)
                .message(message)
                .players(players)
                .build();
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
