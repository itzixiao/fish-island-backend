package com.cong.fishisland.mapper.ranking;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.cong.fishisland.model.ranking.GameStats;
import com.cong.fishisland.model.ranking.GameRankingItemVO;
import com.cong.fishisland.model.ranking.GameUserStatsVO;
import com.cong.fishisland.model.ranking.dto.GameStatsUpdateContext;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 游戏战绩 Mapper 接口
 *
 * @author cong
 */
public interface GameStatsMapper extends BaseMapper<GameStats> {

    int upsertWithExtra(@Param("stats") GameStats stats);

    int insertRecord(@Param("context") GameStatsUpdateContext context);

    List<GameRankingItemVO> selectRankingByMain(@Param("gameType") int gameType,
                                                 @Param("sortBy") String sortBy,
                                                 @Param("minGames") int minGames,
                                                 @Param("limit") int limit);

    List<GameRankingItemVO> selectRankingByExtra(@Param("gameType") int gameType,
                                                  @Param("extKey") String extKey,
                                                  @Param("minGames") int minGames,
                                                  @Param("limit") int limit);

    GameUserStatsVO selectUserStats(@Param("userId") Long userId, @Param("gameType") int gameType);
}
