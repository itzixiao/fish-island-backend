package com.cong.fishisland.model.ranking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 排行榜单条 VO
 *
 * @author cong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameRankingItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer rank;

    private Long userId;

    private String userName;

    private String userAvatar;

    private Integer totalGames;

    private Integer winGames;

    private Integer loseGames;

    private Long totalScore;

    private BigDecimal winRate;

    private String extraStats;
}
