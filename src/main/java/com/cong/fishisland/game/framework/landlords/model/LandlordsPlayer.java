package com.cong.fishisland.game.framework.landlords.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.cong.fishisland.game.common.enums.PlayerRoleEnum;
import com.cong.fishisland.game.common.model.dto.response.PlayerInfoResp;
import com.cong.fishisland.game.common.model.player.GamePlayer;
import com.cong.fishisland.game.framework.landlords.dto.response.LandlordsPlayerInfoResp;
import com.cong.fishisland.game.framework.landlords.model.poker.Poker;
import com.cong.fishisland.game.framework.landlords.model.poker.PokerHand;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 斗地主玩家
 * 继承自 GamePlayer，添加斗地主专属的字段和方法
 *
 * @author cong
 */
@Data
public class LandlordsPlayer extends GamePlayer {

    /**
     * 手牌（斗地主专用）
     */
    private PokerHand hand;

    /**
     * 当前出牌 (显示在桌面上)
     */
    private List<Poker> currentPlayedCards;

    /**
     * 是否是地主
     */
    private boolean isLandlord;

    /**
     * 叫分 (0表示不叫)
     */
    private int robScore;

    /**
     * 底牌是否已添加（防止重复添加）
     */
    private boolean bottomCardsAdded;

    /**
     * 本局积分变化
     */
    private int scoreDelta;

    public LandlordsPlayer() {
        this.hand = new PokerHand();
        this.currentPlayedCards = new ArrayList<>();
        this.isLandlord = false;
        this.robScore = 0;
        this.bottomCardsAdded = false;
        this.scoreDelta = 0;
        setOnline(true);
        setReady(false);
    }

    public LandlordsPlayer(Long userId, String userName, String avatar) {
        this();
        setUserId(userId);
        setUserName(userName);
        setAvatar(avatar);
        setRole(userId != null ? PlayerRoleEnum.PLAYER : null);
    }

    /**
     * 获取手牌数量
     */
    @JSONField(serialize = false)
    public int getCardCount() {
        return hand != null ? hand.size() : 0;
    }

    /**
     * 获取手牌ID列表
     */
    @JSONField(serialize = false)
    public List<String> getCardIds() {
        return hand != null ? hand.toIdList() : new ArrayList<>();
    }

    /**
     * 获取当前出牌ID列表
     */
    @JSONField(serialize = false)
    public List<String> getCurrentPlayedCardIds() {
        if (currentPlayedCards == null) return new ArrayList<>();
        return currentPlayedCards.stream().map(Poker::getId).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 设置当前出牌
     */
    public void setCurrentPlayedCards(List<Poker> cards) {
        this.currentPlayedCards = cards != null ? new ArrayList<>(cards) : new ArrayList<>();
    }

    /**
     * 清除当前出牌
     */
    public void clearCurrentPlayedCards() {
        if (this.currentPlayedCards != null) {
            this.currentPlayedCards.clear();
        }
    }

    /**
     * 设置为地主
     */
    public void setAsLandlord() {
        this.isLandlord = true;
        this.bottomCardsAdded = false;
    }

    /**
     * 设置为非地主
     */
    public void removeLandlord() {
        this.isLandlord = false;
        this.bottomCardsAdded = false;
    }

    /**
     * 标记底牌已添加
     */
    public void markBottomCardsAdded() {
        this.bottomCardsAdded = true;
    }

    /**
     * 检查底牌是否已添加
     */
    @JSONField(serialize = false)
    public boolean isBottomCardsAdded() {
        return this.bottomCardsAdded;
    }

    /**
     * 重置为新游戏准备（不清空手牌内容，只重置状态）
     */
    @Override
    public void resetForNewGame() {
        this.isLandlord = false;
        this.bottomCardsAdded = false;
        this.robScore = 0;
        this.scoreDelta = 0;
        setFinished(false);
    }

    /**
     * 转换为 PlayerInfoResp（通用字段）
     */
    @Override
    public PlayerInfoResp toPlayerInfoResp() {
        return PlayerInfoResp.builder()
                .userId(getUserId())
                .userName(getUserName())
                .avatar(getAvatar())
                .ready(isReady())
                .online(isOnline())
                .role(getRole() != null ? getRole().name() : "PLAYER")
                .robotControlled(isRobotControlled())
                .build();
    }

    /**
     * 转换为斗地主专属 PlayerInfoResp
     */
    public LandlordsPlayerInfoResp toLandlordsPlayerInfoResp() {
        return LandlordsPlayerInfoResp.builder()
                .isLandlord(isLandlord)
                .robScore(robScore)
                .cardCount(getCardCount())
                .build();
    }

    /**
     * 转换为通用 GamePlayer
     * 注意：此方法会丢失斗地主专属的字段
     */
    @JSONField(serialize = false)
    public GamePlayer toGamePlayer() {
        GamePlayer player = new GamePlayer();
        player.setUserId(getUserId());
        player.setUserName(getUserName());
        player.setAvatar(getAvatar());
        player.setRole(getRole());
        player.setReady(isReady());
        player.setOnline(isOnline());
        player.setFinished(isFinished());
        return player;
    }

    /**
     * 获取斗地主玩家简要信息
     */
    @JSONField(serialize = false)
    public LandlordPlayerInfo getLandlordInfo() {
        return new LandlordPlayerInfo(
                getUserId(),
                getUserName(),
                getAvatar(),
                getRole(),
                getCardCount(),
                isReady(),
                isOnline(),
                isLandlord(),
                getRobScore(),
                isRobotControlled()
        );
    }

    /**
     * 斗地主玩家信息 (用于传输)
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LandlordPlayerInfo {
        private Long userId;
        private String userName;
        private String avatar;
        private PlayerRoleEnum role;
        private int cardCount;
        private boolean ready;
        private boolean online;
        private boolean isLandlord;
        private int robScore;
        private boolean robotControlled;
    }
}
