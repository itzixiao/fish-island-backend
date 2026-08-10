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
    
    // ==================== 超时时间(毫秒) ====================
    
    /** 叫地主超时时间 */
    public static final long ROB_TIMEOUT = 20000L;
    
    /** 出牌超时时间 */
    public static final long PLAY_TIMEOUT = 40000L;
    
    /** 麻将出牌超时时间 */
    public static final long MAHJONG_TIMEOUT = 30000L;
    
    /** 德州扑克押注超时时间 */
    public static final long BET_TIMEOUT = 60000L;
    
    /** 准备超时时间 */
    public static final long READY_TIMEOUT = 60000L;
}
