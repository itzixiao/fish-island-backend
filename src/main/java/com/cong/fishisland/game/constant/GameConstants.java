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
     * 房间超时解散：不管人够不够，房间创建后10分钟未开始游戏则自动解散
     * 统一超时，不区分"人不够"和"满员没开始"
     * 默认 10 分钟
     */
    public static final long ROOM_TIMEOUT_MS = 10 * 60 * 1000L;

}
