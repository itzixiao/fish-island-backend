package com.cong.fishisland.model.ranking.dto;

import com.cong.fishisland.game.common.enums.GameTypeEnum;
import com.cong.fishisland.model.ranking.enums.GameExtraStatsFieldEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 游戏战绩写库上下文
 *
 * @author cong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameStatsUpdateContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;

    private GameTypeEnum gameType;

    private String roomId;

    private Boolean isWin;

    private Integer scoreDelta;

    private String role;

    private Map<GameExtraStatsFieldEnum, Object> extraDeltas;
}
