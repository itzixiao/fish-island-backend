package com.cong.fishisland.game.framework.landlords.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.cong.fishisland.game.common.model.player.GamePlayer;
import com.cong.fishisland.game.common.model.room.GameRoom;
import com.cong.fishisland.game.framework.landlords.model.poker.PokerHand;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.*;

/**
 * 斗地主房间
 * 继承自 GameRoom，添加斗地主专属的字段和方法
 *
 * @author cong
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class LandlordsRoom extends GameRoom {

    /**
     * 底牌
     */
    private PokerHand bottomCards;

    /**
     * 地主ID
     */
    private Long landlordId;

    /**
     * 当前出牌玩家ID
     */
    private Long currentPlayerId;

    /**
     * 上一个出牌玩家ID
     */
    private Long lastPlayerId;

    /**
     * 上一个出牌 (用于验证)
     */
    private PokerHand lastPlayedCards;

    /**
     * 当前叫分玩家ID
     */
    private Long currentRobPlayerId;

    /**
     * 当前最高叫分
     */
    private int highestRobScore;

    /**
     * 已叫分的玩家
     */
    private Set<Long> robbedPlayers;

    /**
     * 不叫的玩家
     */
    private Set<Long> passedRobPlayers;

    /**
     * 叫地主轮次开始的玩家ID
     */
    private Long robRoundStartPlayerId;

    /**
     * 最后一个叫分的玩家ID
     */
    private Long lastRobPlayerId;

    /**
     * 是否是癞子模式
     */
    private boolean laiZiMode;

    /**
     * 癞子牌面值
     */
    private int universalValue;

    public LandlordsRoom() {
        super();
        this.bottomCards = new PokerHand();
        this.robbedPlayers = new HashSet<>();
        this.passedRobPlayers = new HashSet<>();
    }

    public LandlordsRoom(String roomId, com.cong.fishisland.game.common.enums.GameTypeEnum gameType, Long ownerId) {
        super(roomId, gameType, ownerId);
        this.bottomCards = new PokerHand();
        this.robbedPlayers = new HashSet<>();
        this.passedRobPlayers = new HashSet<>();
    }

    /**
     * 获取底牌
     */
    public PokerHand getBottomCards() {
        return bottomCards;
    }

    /**
     * 设置地主
     */
    public void setLandlord(Long userId) {
        if (getPlayers() == null) {
            return;
        }
        if (landlordId != null) {
            GamePlayer oldLandlord = getPlayers().get(landlordId);
            if (oldLandlord instanceof LandlordsPlayer) {
                ((LandlordsPlayer) oldLandlord).removeLandlord();
            }
        }

        landlordId = userId;

        GamePlayer newLandlord = getPlayers().get(userId);
        if (newLandlord instanceof LandlordsPlayer) {
            LandlordsPlayer landlordPlayer = (LandlordsPlayer) newLandlord;
            landlordPlayer.setAsLandlord();
            if (!landlordPlayer.isBottomCardsAdded() && bottomCards != null && !bottomCards.isEmpty()) {
                landlordPlayer.getHand().addAll(bottomCards.getAll());
                landlordPlayer.markBottomCardsAdded();
            }
        }
    }

    /**
     * 是否是地主
     */
    public boolean isLandlord(Long userId) {
        return landlordId != null && landlordId.equals(userId);
    }

    /**
     * 重置房间准备下一局
     */
    public void resetForNewRound() {
        setState(RoomState.WAITING);

        for (GamePlayer player : getOrderedPlayers()) {
            player.setReady(false);
            player.setRobotControlled(false);
            player.setRobotReason(null);
            player.resetForNewGame();

            // 斗地主专属重置
            if (player instanceof LandlordsPlayer) {
                LandlordsPlayer lp = (LandlordsPlayer) player;
                lp.setRobScore(0);
                lp.setCurrentPlayedCards(null);
            }
        }

        setLandlordId(null);
        setCurrentPlayerId(null);
        setLastPlayerId(null);
        setLastPlayedCards(null);
        setCurrentRobPlayerId(null);
        setHighestRobScore(0);
        setLastRobPlayerId(null);
        setRobRoundStartPlayerId(null);
        setBottomCards(new PokerHand());
        if (getRobbedPlayers() != null) {
            getRobbedPlayers().clear();
        }
        if (getPassedRobPlayers() != null) {
            getPassedRobPlayers().clear();
        }

        enterReadyPhase(System.currentTimeMillis());
    }

    /**
     * 获取下一个玩家ID
     */
    public Long getNextPlayerId(Long currentId) {
        List<Long> order = getPlayerOrder();
        int index = order.indexOf(currentId);
        if (index < 0 || order.size() <= 1) {
            return null;
        }
        return order.get((index + 1) % order.size());
    }

    /**
     * 获取上一个玩家ID
     */
    public Long getPrevPlayerId(Long currentId) {
        List<Long> order = getPlayerOrder();
        int index = order.indexOf(currentId);
        if (index < 0 || order.size() <= 1) {
            return null;
        }
        return order.get((index - 1 + order.size()) % order.size());
    }

    /**
     * 获取按顺序排列的斗地主玩家
     */
    @JSONField(serialize = false)
    public List<LandlordsPlayer> getOrderedLandlordsPlayers() {
        return getOrderedPlayers().stream()
                .filter(p -> p instanceof LandlordsPlayer)
                .map(p -> (LandlordsPlayer) p)
                .collect(java.util.stream.Collectors.toList());
    }
}
