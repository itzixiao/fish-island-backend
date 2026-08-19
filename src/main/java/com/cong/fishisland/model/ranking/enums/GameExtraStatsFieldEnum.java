package com.cong.fishisland.model.ranking.enums;

import com.cong.fishisland.game.common.enums.GameTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 游戏战绩扩展字段枚举
 *
 * @author cong
 */
@Getter
@AllArgsConstructor
public enum GameExtraStatsFieldEnum {

    MAX_CONSECUTIVE_WINS("max_consecutive_wins", GameTypeEnum.UNKNOWN, Integer.class, "最大连胜"),
    CURRENT_CONSECUTIVE_WINS("current_consecutive_wins", GameTypeEnum.UNKNOWN, Integer.class, "当前连胜"),

    LANDLORD_WIN_COUNT("landlord_win_count", GameTypeEnum.LANDLORDS_CLASSIC, Integer.class, "当地主胜场"),
    LANDLORD_LOSE_COUNT("landlord_lose_count", GameTypeEnum.LANDLORDS_CLASSIC, Integer.class, "当地主负场"),
    LANDLORD_WIN_RATE("landlord_win_rate", GameTypeEnum.LANDLORDS_CLASSIC, Double.class, "当地主胜率"),
    FARMER_WIN_COUNT("farmer_win_count", GameTypeEnum.LANDLORDS_CLASSIC, Integer.class, "当农民胜场"),
    FARMER_LOSE_COUNT("farmer_lose_count", GameTypeEnum.LANDLORDS_CLASSIC, Integer.class, "当农民负场"),
    FARMER_WIN_RATE("farmer_win_rate", GameTypeEnum.LANDLORDS_CLASSIC, Double.class, "当农民胜率"),
    ;

    private final String jsonKey;
    private final GameTypeEnum gameType;
    private final Class<?> type;
    private final String label;
}
