package com.cong.fishisland.model.ranking.enums;

import com.cong.fishisland.game.common.enums.GameTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 排行榜排序维度枚举
 *
 * @author cong
 */
@Getter
@AllArgsConstructor
public enum GameRankingSortByEnum {

    WIN_RATE("win_rate", "胜率", GameTypeEnum.UNKNOWN, false),
    WIN_GAMES("win_games", "胜场", GameTypeEnum.UNKNOWN, false),
    TOTAL_GAMES("total_games", "总场数", GameTypeEnum.UNKNOWN, false),
    TOTAL_SCORE("total_score", "累计积分", GameTypeEnum.UNKNOWN, false),

    LANDLORD_WIN_RATE("extra:landlord_win_rate", "当地主胜率", GameTypeEnum.LANDLORDS_CLASSIC, true),
    FARMER_WIN_RATE("extra:farmer_win_rate", "当农民胜率", GameTypeEnum.LANDLORDS_CLASSIC, true),
    ROCKET_COUNT("extra:rocket_count", "王炸次数", GameTypeEnum.LANDLORDS_CLASSIC, true),
    SPRING_COUNT("extra:spring_count", "春天次数", GameTypeEnum.LANDLORDS_CLASSIC, true),
    ;

    private final String code;
    private final String label;
    private final GameTypeEnum gameType;
    private final boolean isExtra;

    public boolean isExtraSort() {
        return code.startsWith("extra:");
    }

    public String getExtraKey() {
        if (isExtraSort()) {
            return code.substring("extra:".length());
        }
        return null;
    }

    public static GameRankingSortByEnum getByCode(String code) {
        for (GameRankingSortByEnum sortBy : values()) {
            if (sortBy.getCode().equals(code)) {
                return sortBy;
            }
        }
        return null;
    }
}
