package com.cong.fishisland.service.ranking;

import com.cong.fishisland.game.common.enums.GameTypeEnum;
import com.cong.fishisland.model.ranking.GameRankingItemVO;
import com.cong.fishisland.model.ranking.GameUserStatsVO;
import com.cong.fishisland.model.ranking.SortOptionVO;
import com.cong.fishisland.model.ranking.enums.GameRankingSortByEnum;

import java.util.List;

/**
 * 排行榜查询服务接口
 *
 * @author cong
 */
public interface GameRankingService {

    /**
     * 通用排行榜查询
     */
    List<GameRankingItemVO> getRanking(GameTypeEnum gameType, GameRankingSortByEnum sortBy, int topN, int minGames);

    /**
     * 获取某用户的战绩
     */
    GameUserStatsVO getUserStats(Long userId, GameTypeEnum gameType);

    /**
     * 获取某游戏支持的所有排序维度
     */
    List<SortOptionVO> listSortOptions(GameTypeEnum gameType);
}
