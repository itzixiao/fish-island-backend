package com.cong.fishisland.model.ranking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

/**
 * 用户个人战绩 VO
 *
 * @author cong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameUserStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private Integer gameType;

    private Integer totalGames;

    private Integer winGames;

    private Integer loseGames;

    private Integer drawGames;

    private Long totalScore;

    private BigDecimal winRate;

    private Date lastPlayTime;

    private String extraStats;

    private Map<String, Object> extraMap;
}
