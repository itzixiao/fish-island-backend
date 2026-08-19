package com.cong.fishisland.game.common.model.player;

import com.alibaba.fastjson2.annotation.JSONField;
import com.cong.fishisland.game.common.enums.PlayerRoleEnum;
import com.cong.fishisland.game.common.enums.RobotReasonEnum;
import com.cong.fishisland.game.common.model.dto.response.PlayerInfoResp;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 游戏玩家（通用基础类）
 * <p>
 * 包含所有游戏类型通用的字段。
 * 各游戏类型的专属字段应放在各自的子类中（如 LandlordsPlayer）。
 *
 * @author cong
 */
@Data
public class GamePlayer {

    /**
     * 玩家ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 角色 (房主/玩家)
     */
    private PlayerRoleEnum role;

    /**
     * 是否准备
     */
    private boolean ready;

    /**
     * 是否在线
     */
    private boolean online;

    /**
     * 是否已出完牌
     */
    private boolean finished;

    /**
     * 最后出牌时间
     */
    private long lastPlayTime;

    /**
     * 是否被AI托管
     */
    private boolean robotControlled;

    /**
     * AI托管原因
     */
    private RobotReasonEnum robotReason;

    public GamePlayer() {
        this.ready = false;
        this.online = true;
        this.finished = false;
        this.robotControlled = false;
    }

    public GamePlayer(Long userId, String userName, String avatar) {
        this();
        this.userId = userId;
        this.userName = userName;
        this.avatar = avatar;
    }

    /**
     * 是否可以开始游戏
     */
    @JSONField(serialize = false)
    public boolean canStartGame() {
        return role == PlayerRoleEnum.OWNER && ready;
    }

    /**
     * 重置为新游戏准备
     */
    @JSONField(serialize = false)
    public void resetForNewGame() {
        this.finished = false;
    }

    /**
     * 获取玩家简要信息
     */
    @JSONField(serialize = false)
    public PlayerInfo getInfo() {
        return new PlayerInfo(userId, userName, avatar, role, ready, online, robotControlled);
    }

    /**
     * 玩家信息 (用于传输)
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PlayerInfo {
        private Long userId;
        private String userName;
        private String avatar;
        private PlayerRoleEnum role;
        private boolean ready;
        private boolean online;
        private boolean robotControlled;
    }

    /**
     * 转换为 PlayerInfoResp
     */
    public PlayerInfoResp toPlayerInfoResp() {
        return PlayerInfoResp.builder()
                .userId(userId)
                .userName(userName)
                .avatar(avatar)
                .ready(ready)
                .online(online)
                .role(role != null ? role.name() : "PLAYER")
                .robotControlled(robotControlled)
                .build();
    }
}
