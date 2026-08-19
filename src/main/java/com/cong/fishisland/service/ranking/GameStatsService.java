package com.cong.fishisland.service.ranking;

import com.cong.fishisland.game.common.enums.GameTypeEnum;
import com.cong.fishisland.model.ranking.GameUserStatsVO;
import com.cong.fishisland.model.ranking.dto.GameStatsUpdateContext;

/**
 * 游戏战绩写库服务接口
 *
 * @author cong
 */
public interface GameStatsService {

    /**
     * 游戏对局结束的统一入口
     */
    void recordGameFinish(GameStatsUpdateContext ctx);

    /**
     * 查询某个用户的某游戏战绩
     */
    GameUserStatsVO getUserStats(Long userId, GameTypeEnum gameType);
}
