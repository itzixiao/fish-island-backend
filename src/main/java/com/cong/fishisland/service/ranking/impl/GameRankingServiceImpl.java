package com.cong.fishisland.service.ranking.impl;

import com.cong.fishisland.game.common.enums.GameTypeEnum;
import com.cong.fishisland.mapper.ranking.GameStatsMapper;
import com.cong.fishisland.model.ranking.GameRankingItemVO;
import com.cong.fishisland.model.ranking.GameUserStatsVO;
import com.cong.fishisland.model.ranking.SortOptionVO;
import com.cong.fishisland.model.ranking.enums.GameRankingSortByEnum;
import com.cong.fishisland.service.ranking.GameRankingService;
import com.cong.fishisland.service.ranking.GameStatsService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 排行榜查询服务实现
 *
 * @author cong
 */
@Service
public class GameRankingServiceImpl implements GameRankingService {

    @Resource
    private GameStatsMapper gameStatsMapper;

    @Resource
    private GameStatsService gameStatsService;

    @Override
    public List<GameRankingItemVO> getRanking(GameTypeEnum gameType, GameRankingSortByEnum sortBy, int topN, int minGames) {
        if (sortBy == null) {
            sortBy = GameRankingSortByEnum.WIN_RATE;
        }

        List<GameRankingItemVO> ranking;

        if (sortBy.isExtraSort()) {
            String extKey = sortBy.getExtraKey();
            ranking = gameStatsMapper.selectRankingByExtra(gameType.getCode(), extKey, minGames, topN);
        } else {
            String sortField = sortBy.getCode();
            ranking = gameStatsMapper.selectRankingByMain(gameType.getCode(), sortField, minGames, topN);
        }

        for (int i = 0; i < ranking.size(); i++) {
            ranking.get(i).setRank(i + 1);
        }

        return ranking;
    }

    @Override
    public GameUserStatsVO getUserStats(Long userId, GameTypeEnum gameType) {
        return gameStatsService.getUserStats(userId, gameType);
    }

    @Override
    public List<SortOptionVO> listSortOptions(GameTypeEnum gameType) {
        List<SortOptionVO> options = new ArrayList<>();

        for (GameRankingSortByEnum sortBy : GameRankingSortByEnum.values()) {
            if (sortBy.getGameType() == GameTypeEnum.UNKNOWN || sortBy.getGameType() == gameType) {
                SortOptionVO option = SortOptionVO.builder()
                        .code(sortBy.getCode())
                        .label(sortBy.getLabel())
                        .extKey(sortBy.isExtraSort() ? sortBy.getExtraKey() : null)
                        .build();
                options.add(option);
            }
        }

        return options;
    }
}
