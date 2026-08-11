package com.cong.fishisland.game.constant;

/**
 * 游戏常量
 *
 * @author cong
 */
public class GameConstants {

    private GameConstants() {
    }

    // ==================== 游戏相关 ====================

    /** 斗地主玩家数 */
    public static final int LANDLORDS_PLAYERS = 3;

    /** 跑得快玩家数 */
    public static final int RUNFAST_PLAYERS = 3;

    /** 底牌数量 */
    public static final int BOTTOM_CARD_COUNT = 3;

    /** 每人初始手牌数 */
    public static final int INITIAL_HAND_CARDS = 17;

    // ==================== 回合内超时(毫秒) ====================

    /** 叫地主超时时间 */
    public static final long ROB_TIMEOUT = 20000L;

    /** 出牌超时时间 */
    public static final long PLAY_TIMEOUT = 40000L;

    /** 麻将出牌超时时间 */
    public static final long MAHJONG_TIMEOUT = 30000L;

    /** 德州扑克押注超时时间 */
    public static final long BET_TIMEOUT = 60000L;

    // ==================== 房间清理超时(毫秒) ====================

    /**
     * 等待中房间超时：房间未满、长时间没人开始 → 自动解散
     * 默认 3 分钟；超过即视为无人参与
     */
    public static final long WAITING_ROOM_TIMEOUT_MS = 3 * 60 * 1000L;

    /**
     * 满员房间超时：3 人齐了但一直不开（没人准备 / 房主不点开始）→ 自动解散
     * 默认 3 分钟
     */
    public static final long ROOM_FULL_NO_START_TIMEOUT_MS = 3 * 60 * 1000L;

    // ==================== 准备超时(毫秒) ====================

    /**
     * 一局结束后进入下一局时，玩家必须重新点击准备的超时时间
     * 超过即视为放弃本局，自动踢出房间
     * 默认 30 秒
     */
    public static final long READY_TIMEOUT_MS = 30 * 1000L;

    /**
     * 准备超时最后告警阈值：剩余多少毫秒时再通知一次前端
     * 默认 5 秒
     */
    public static final long READY_TIMEOUT_WARN_MS = 5 * 1000L;
}
