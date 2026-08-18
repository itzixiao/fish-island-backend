package com.cong.fishisland.service.ranking.impl;

import com.cong.fishisland.mapper.ranking.GameStatsMapper;
import com.cong.fishisland.model.ranking.GameStats;
import com.cong.fishisland.model.ranking.GameUserStatsVO;
import com.cong.fishisland.model.ranking.dto.GameStatsUpdateContext;
import com.cong.fishisland.model.ranking.enums.GameExtraStatsFieldEnum;
import com.cong.fishisland.service.ranking.GameStatsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 游戏战绩写库服务实现
 *
 * @author cong
 */
@Slf4j
@Service
public class GameStatsServiceImpl implements GameStatsService {

    @Resource
    private GameStatsMapper gameStatsMapper;

    @Resource
    private ObjectMapper objectMapper;

    private static final int WIN_RATE_SCALE = 4;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordGameFinish(GameStatsUpdateContext ctx) {
        Long userId = ctx.getUserId();
        int gameType = ctx.getGameType().getCode();
        Boolean isWin = ctx.getIsWin();
        String role = ctx.getRole();

        GameStats existingStats = getExistingStats(userId, gameType);

        int totalGamesDelta = 1;
        int winGamesDelta = (isWin != null && isWin) ? 1 : 0;
        int loseGamesDelta = (isWin != null && !isWin) ? 1 : 0;
        int drawGamesDelta = (isWin == null) ? 1 : 0;
        int scoreDelta = ctx.getScoreDelta() != null ? ctx.getScoreDelta() : 0;

        BigDecimal newWinRate = calculateWinRate(existingStats, winGamesDelta, totalGamesDelta);

        Map<String, Object> extraDeltas = buildExtraDeltas(ctx, existingStats);

        GameStats statsToUpsert = GameStats.builder()
                .userId(userId)
                .gameType(gameType)
                .totalGames(totalGamesDelta)
                .winGames(winGamesDelta)
                .loseGames(loseGamesDelta)
                .drawGames(drawGamesDelta)
                .totalScore((long) scoreDelta)
                .winRate(newWinRate)
                .extraStats(serializeExtraStats(extraDeltas))
                .build();

        gameStatsMapper.upsertWithExtra(statsToUpsert);
        gameStatsMapper.insertRecord(ctx);
    }

    @Override
    public GameUserStatsVO getUserStats(Long userId, com.cong.fishisland.game.common.enums.GameTypeEnum gameType) {
        GameUserStatsVO vo = gameStatsMapper.selectUserStats(userId, gameType.getCode());
        if (vo == null) {
            return null;
        }

        Map<String, Object> extraMap = parseExtraStats(vo.getExtraStats());
        vo.setExtraMap(extraMap);

        return vo;
    }

    private GameStats getExistingStats(Long userId, int gameType) {
        return gameStatsMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GameStats>()
                        .eq("userId", userId)
                        .eq("gameType", gameType)
                        .eq("isDelete", 0)
        ).stream().findFirst().orElse(null);
    }

    private BigDecimal calculateWinRate(GameStats existing, int winGamesDelta, int totalGamesDelta) {
        int existingTotal = existing != null ? existing.getTotalGames() : 0;
        int existingWins = existing != null ? existing.getWinGames() : 0;

        int newTotal = existingTotal + totalGamesDelta;
        int newWins = existingWins + winGamesDelta;

        if (newTotal == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(newWins)
                .divide(BigDecimal.valueOf(newTotal), WIN_RATE_SCALE, RoundingMode.HALF_UP);
    }

    private Map<String, Object> buildExtraDeltas(GameStatsUpdateContext ctx, GameStats existing) {
        Map<String, Object> deltas = new HashMap<>();

        if (ctx.getExtraDeltas() != null) {
            for (Map.Entry<GameExtraStatsFieldEnum, Object> entry : ctx.getExtraDeltas().entrySet()) {
                deltas.put(entry.getKey().getJsonKey(), entry.getValue());
            }
        }

        String role = ctx.getRole();
        Boolean isWin = ctx.getIsWin();

        if ("landlord".equals(role)) {
            if (isWin != null) {
                deltas.put(GameExtraStatsFieldEnum.LANDLORD_WIN_COUNT.getJsonKey(), isWin ? 1 : 0);
                deltas.put(GameExtraStatsFieldEnum.LANDLORD_LOSE_COUNT.getJsonKey(), isWin ? 0 : 1);
            }
            recalculateRoleWinRate(deltas, existing, GameExtraStatsFieldEnum.LANDLORD_WIN_COUNT,
                    GameExtraStatsFieldEnum.LANDLORD_LOSE_COUNT, GameExtraStatsFieldEnum.LANDLORD_WIN_RATE);
        } else if ("farmer".equals(role)) {
            if (isWin != null) {
                deltas.put(GameExtraStatsFieldEnum.FARMER_WIN_COUNT.getJsonKey(), isWin ? 1 : 0);
                deltas.put(GameExtraStatsFieldEnum.FARMER_LOSE_COUNT.getJsonKey(), isWin ? 0 : 1);
            }
            recalculateRoleWinRate(deltas, existing, GameExtraStatsFieldEnum.FARMER_WIN_COUNT,
                    GameExtraStatsFieldEnum.FARMER_LOSE_COUNT, GameExtraStatsFieldEnum.FARMER_WIN_RATE);
        }

        return deltas;
    }

    private void recalculateRoleWinRate(Map<String, Object> deltas, GameStats existing,
                                        GameExtraStatsFieldEnum winCount, GameExtraStatsFieldEnum loseCount,
                                        GameExtraStatsFieldEnum winRate) {
        Map<String, Object> existingExtra = existing != null ? parseExtraStats(existing.getExtraStats()) : new HashMap<>();

        int existingWins = getIntValue(existingExtra, winCount.getJsonKey(), 0);
        int existingLoses = getIntValue(existingExtra, loseCount.getJsonKey(), 0);

        int deltaWins = getIntValue(deltas, winCount.getJsonKey(), 0);
        int deltaLoses = getIntValue(deltas, loseCount.getJsonKey(), 0);

        int newWins = existingWins + deltaWins;
        int newLoses = existingLoses + deltaLoses;
        int newTotal = newWins + newLoses;

        double newRate = newTotal > 0 ? (double) newWins / newTotal : 0.0;
        deltas.put(winRate.getJsonKey(), Math.round(newRate * 10000.0) / 10000.0);
    }

    private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private String serializeExtraStats(Map<String, Object> extraStats) {
        if (extraStats == null || extraStats.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(extraStats);
        } catch (JsonProcessingException e) {
            log.error("序列化扩展字段失败", e);
            return "{}";
        }
    }

    private Map<String, Object> parseExtraStats(String extraStats) {
        if (extraStats == null || extraStats.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(extraStats, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("反序列化扩展字段失败", e);
            return new HashMap<>();
        }
    }
}
