package com.cong.fishisland.game.common.constant;

/**
 * 游戏常量
 *
 * @author cong
 */
public class GameConstants {

    private GameConstants() {
    }

    // ==================== 房间清理超时(毫秒) ====================

    /**
     * 房间超时解散：不管人够不够，房间创建后10分钟未开始游戏则自动解散
     * 统一超时，不区分"人不够"和"满员没开始"
     * 默认 10 分钟
     */
    public static final long ROOM_TIMEOUT_MS = 10 * 60 * 1000L;

    // ==================== 出牌/叫分超时(秒) ====================

    /**
     * 出牌/叫分阶段单回合超时时间
     * 默认 30 秒
     */
    public static final int TURN_TIMEOUT_SECONDS = 30;
}
