package com.cong.fishisland.controller.ranking;

import com.cong.fishisland.common.BaseResponse;
import com.cong.fishisland.common.ErrorCode;
import com.cong.fishisland.common.ResultUtils;
import com.cong.fishisland.game.common.enums.GameTypeEnum;
import com.cong.fishisland.model.ranking.GameRankingItemVO;
import com.cong.fishisland.model.ranking.GameUserStatsVO;
import com.cong.fishisland.model.ranking.SortOptionVO;
import com.cong.fishisland.model.ranking.enums.GameRankingSortByEnum;
import com.cong.fishisland.service.ranking.GameRankingService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 游戏排行榜接口
 *
 * @author cong
 */
@Api(tags = "游戏排行榜")
@RestController
@RequestMapping("/ranking")
@Slf4j
public class GameRankingController {

    @Resource
    private GameRankingService gameRankingService;

    /**
     * 获取游戏排行榜
     */
    @ApiOperation(value = "获取游戏排行榜")
    @GetMapping("/list")
    public BaseResponse<List<GameRankingItemVO>> getRanking(
            @RequestParam int gameType,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "20") int topN,
            @RequestParam(defaultValue = "0") int minGames) {

        GameTypeEnum gameTypeEnum = GameTypeEnum.getByCode(gameType);
        if (gameTypeEnum == GameTypeEnum.UNKNOWN) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "不支持的游戏类型");
        }

        GameRankingSortByEnum sortByEnum = convertSortBy(sortBy, gameTypeEnum);
        if (sortByEnum == null) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "不支持的排序字段");
        }

        topN = Math.min(Math.max(topN, 1), 100);
        minGames = Math.max(minGames, 0);

        List<GameRankingItemVO> ranking = gameRankingService.getRanking(gameTypeEnum, sortByEnum, topN, minGames);
        return ResultUtils.success(ranking);
    }

    /**
     * 获取当前用户的游戏战绩
     */
    @ApiOperation(value = "获取当前用户的游戏战绩")
    @GetMapping("/my")
    public BaseResponse<GameUserStatsVO> getMyStats(@RequestParam int gameType) {
        GameTypeEnum gameTypeEnum = GameTypeEnum.getByCode(gameType);
        if (gameTypeEnum == GameTypeEnum.UNKNOWN) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "不支持的游戏类型");
        }

        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR);
        }

        GameUserStatsVO stats = gameRankingService.getUserStats(userId, gameTypeEnum);
        if (stats == null) {
            stats = GameUserStatsVO.builder()
                    .userId(userId)
                    .gameType(gameType)
                    .totalGames(0)
                    .winGames(0)
                    .loseGames(0)
                    .drawGames(0)
                    .totalScore(0L)
                    .winRate(java.math.BigDecimal.ZERO)
                    .build();
        }

        return ResultUtils.success(stats);
    }

    /**
     * 获取游戏支持的排序选项
     */
    @ApiOperation(value = "获取游戏支持的排序选项")
    @GetMapping("/options")
    public BaseResponse<List<SortOptionVO>> getSortOptions(@RequestParam int gameType) {
        GameTypeEnum gameTypeEnum = GameTypeEnum.getByCode(gameType);
        if (gameTypeEnum == GameTypeEnum.UNKNOWN) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "不支持的游戏类型");
        }

        List<SortOptionVO> options = gameRankingService.listSortOptions(gameTypeEnum);
        return ResultUtils.success(options);
    }

    private GameRankingSortByEnum convertSortBy(String sortBy, GameTypeEnum gameType) {
        if (sortBy == null || sortBy.isEmpty()) {
            return GameRankingSortByEnum.WIN_RATE;
        }

        String normalized = normalizeSortBy(sortBy);

        GameRankingSortByEnum result = GameRankingSortByEnum.getByCode(normalized);
        if (result != null) {
            if (result.getGameType() == GameTypeEnum.UNKNOWN || result.getGameType() == gameType) {
                return result;
            }
        }

        String withExtra = "extra:" + normalized;
        result = GameRankingSortByEnum.getByCode(withExtra);
        if (result != null) {
            if (result.getGameType() == GameTypeEnum.UNKNOWN || result.getGameType() == gameType) {
                return result;
            }
        }

        return null;
    }

    private String normalizeSortBy(String sortBy) {
        if (sortBy == null) {
            return null;
        }
        return sortBy.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    private Long getCurrentUserId() {
        try {
            if (cn.dev33.satoken.stp.StpUtil.isLogin()) {
                return cn.dev33.satoken.stp.StpUtil.getLoginIdAsLong();
            }
        } catch (Exception e) {
            log.warn("获取当前用户失败", e);
        }
        return null;
    }
}
