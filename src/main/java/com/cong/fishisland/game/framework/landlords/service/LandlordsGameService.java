package com.cong.fishisland.game.framework.landlords.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.cong.fishisland.game.common.cache.GameRoomRedisCache;
import com.cong.fishisland.game.common.constant.GameConstants;
import com.cong.fishisland.game.common.enums.GameTypeEnum;
import com.cong.fishisland.game.common.enums.PlayerStatusEnum;
import com.cong.fishisland.game.common.enums.RobotReasonEnum;
import com.cong.fishisland.game.common.manager.GameRoomManager;
import com.cong.fishisland.game.common.manager.GameSessionManager;
import com.cong.fishisland.game.common.model.player.GamePlayer;
import com.cong.fishisland.game.common.model.room.GameRoom;
import com.cong.fishisland.game.framework.landlords.dto.response.ActionResultResp;
import com.cong.fishisland.game.framework.landlords.dto.response.GameStateResp;
import com.cong.fishisland.game.framework.landlords.dto.response.TurnNotifyResp;
import com.cong.fishisland.game.framework.landlords.enums.GameActionEnum;
import com.cong.fishisland.game.framework.landlords.enums.GameMessageTypeEnum;
import com.cong.fishisland.game.framework.landlords.enums.GamePhaseEnum;
import com.cong.fishisland.game.framework.landlords.enums.poker.PokerPatternEnum;
import com.cong.fishisland.game.framework.landlords.model.LandlordsPlayer;
import com.cong.fishisland.game.framework.landlords.model.LandlordsRoom;
import com.cong.fishisland.game.framework.landlords.model.poker.PatternResult;
import com.cong.fishisland.game.framework.landlords.model.poker.Poker;
import com.cong.fishisland.game.framework.landlords.model.poker.PokerHand;
import com.cong.fishisland.game.framework.landlords.util.poker.PokerComparator;
import com.cong.fishisland.game.framework.landlords.util.poker.PokerGenerator;
import com.cong.fishisland.game.framework.landlords.util.poker.PokerPatternMatcher;
import com.cong.fishisland.game.framework.landlords.util.poker.PokerSorter;
import com.cong.fishisland.game.service.GameService;
import com.cong.fishisland.model.ranking.dto.GameStatsUpdateContext;
import com.cong.fishisland.model.ws.response.WSBaseResp;
import com.cong.fishisland.service.ranking.GameStatsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 斗地主游戏服务
 * 使用枚举和实体类统一管理游戏流程
 *
 * @author cong
 */
@Slf4j
@Service
public class LandlordsGameService implements GameService {

    @Resource
    private GameSessionManager sessionManager;

    @Resource
    private GameRoomManager roomManager;

    @Resource
    private GameRoomRedisCache roomCache;

    @Resource
    private LandlordsRobotService robotService;

    @Resource
    private GameStatsService gameStatsService;

    private final Map<String, ScheduledFuture<?>> robTimeoutTasks = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> playTimeoutTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    // ==================== 辅助方法 ====================

    /**
     * 将 GameRoom 转换为 LandlordsRoom
     */
    private LandlordsRoom toLandlordsRoom(GameRoom room) {
        if (room instanceof LandlordsRoom) {
            return (LandlordsRoom) room;
        }
        log.warn("房间类型不匹配: expected LandlordsRoom, got {}", room.getClass().getName());
        return null;
    }

    /**
     * 转换玩家列表为 LandlordsPlayer
     */
    private List<LandlordsPlayer> toLandlordsPlayers(List<GamePlayer> players) {
        return players.stream()
                .filter(p -> p instanceof LandlordsPlayer)
                .map(p -> (LandlordsPlayer) p)
                .collect(Collectors.toList());
    }

    // ==================== 服务接口实现 ====================

    @Override
    public GameTypeEnum getGameType() {
        return GameTypeEnum.LANDLORDS_CLASSIC;
    }

    @Override
    public GameStateResp startGame(GameRoom room) {
        LandlordsRoom landlordsRoom = toLandlordsRoom(room);
        if (landlordsRoom == null) {
            throw new GameBusinessException("INVALID_ROOM", "房间类型不匹配");
        }
        return startGameInternal(landlordsRoom);
    }

    /**
     * 开始游戏内部方法
     */
    private GameStateResp startGameInternal(LandlordsRoom room) {
        validatePlayerCount(room);

        room.exitReadyPhase();
        room.setState(GameRoom.RoomState.DISTRIBUTING);

        PokerHand deck = PokerGenerator.shuffle(PokerGenerator.generateFullDeck());
        PokerGenerator.DealResult dealResult = PokerGenerator.dealWithBottom(
                deck, room.getPlayerCount(), 17, 3);

        room.setBottomCards(dealResult.getBottom());

        List<LandlordsPlayer> players = room.getOrderedLandlordsPlayers();
        for (int i = 0; i < players.size(); i++) {
            PokerHand hand = dealResult.getHands().get(i);
            PokerSorter.sortByLandlordsWithUniversal(hand);
            players.get(i).setHand(hand);
        }

        int randomIndex = new Random().nextInt(players.size());
        Long firstRobPlayerId = players.get(randomIndex).getUserId();
        room.setCurrentRobPlayerId(firstRobPlayerId);
        room.setHighestRobScore(0);
        room.setRobbedPlayers(new HashSet<>());
        room.setPassedRobPlayers(new HashSet<>());
        room.setRobRoundStartPlayerId(firstRobPlayerId);
        room.setLastRobPlayerId(null);
        room.setState(GameRoom.RoomState.ROBBING);

        roomManager.saveRoom(room);

        for (LandlordsPlayer player : players) {
            sendPrivateState(room, player, player.getUserId());
        }

        broadcastGameStart(room);
        broadcastRobTurnNotify(room, firstRobPlayerId);
        startRobTimeout(room);

        return buildGameState(room, null);
    }

    @Override
    public GameStateResp robLandlord(GameRoom room, Long userId, Integer action) {
        LandlordsRoom landlordsRoom = toLandlordsRoom(room);
        if (landlordsRoom == null) {
            throw new GameBusinessException("INVALID_ROOM", "房间类型不匹配");
        }
        return robLandlordInternal(landlordsRoom, userId, action);
    }

    /**
     * 叫地主内部方法
     */
    private GameStateResp robLandlordInternal(LandlordsRoom room, Long userId, Integer action) {
        GameValidationResult validation = validateRobAction(room, userId, action);
        if (!validation.isValid()) {
            throw new GameBusinessException(validation.getErrorCode(), validation.getErrorMessage());
        }

        cancelRobTimeout(room.getRoomId());

        LandlordsPlayer player = getLandlordsPlayer(room, userId);
        player.setRobScore(action);

        if (action > room.getHighestRobScore()) {
            room.setHighestRobScore(action);
        }

        String robScoreDesc = action > 0 ? action + "分" : "不叫";
        String message = action > 0
                ? String.format("叫了 %d 分", action)
                : "不叫";

        ActionResultResp actionResult = ActionResultResp.robResult(
                userId,
                player.getUserName(),
                action,
                robScoreDesc,
                room.getHighestRobScore(),
                message
        );
        broadcastActionResult(room, actionResult);

        GameStateResp result = handleRobCompletion(room, userId, action);
        roomManager.saveRoom(room);

        return result;
    }

    @Override
    public GameStateResp playCards(GameRoom room, Long userId, List<String> pokerIds) {
        LandlordsRoom landlordsRoom = toLandlordsRoom(room);
        if (landlordsRoom == null) {
            throw new GameBusinessException("INVALID_ROOM", "房间类型不匹配");
        }
        return playCardsInternal(landlordsRoom, userId, pokerIds);
    }

    /**
     * 出牌内部方法
     */
    private GameStateResp playCardsInternal(LandlordsRoom room, Long userId, List<String> pokerIds) {
        GameValidationResult validation = validatePlayCards(room, userId, pokerIds);
        if (!validation.isValid()) {
            throw new GameBusinessException(validation.getErrorCode(), validation.getErrorMessage());
        }

        cancelPlayTimeout(room.getRoomId());

        LandlordsPlayer player = getLandlordsPlayer(room, userId);
        List<Poker> playedPokers = parsePokers(pokerIds);
        playedPokers.forEach(p -> removeCardByValue(player.getHand(), p));

        player.setCurrentPlayedCards(playedPokers);

        PokerHand playedHand = new PokerHand(playedPokers);
        PatternResult pattern = PokerPatternMatcher.analyze(playedHand);

        boolean isBomb = PokerPatternEnum.JOKER_BOMB.equals(pattern.getPattern())
                || PokerPatternEnum.BOMB.equals(pattern.getPattern());
        String patternDesc = PokerComparator.getPatternDescription(pattern);

        room.setLastPlayedCards(playedHand);
        room.setLastPlayerId(userId);

        ActionResultResp actionResult = ActionResultResp.playResult(
                userId,
                player.getUserName(),
                playedPokers,
                patternDesc,
                isBomb,
                patternDesc
        );
        broadcastActionResult(room, actionResult);

        if (player.getHand().isEmpty()) {
            return handleGameOver(room, userId);
        }

        sendPrivateState(room, player, userId);
        return advanceToNextPlayer(room, userId);
    }

    @Override
    public GameStateResp pass(GameRoom room, Long userId) {
        LandlordsRoom landlordsRoom = toLandlordsRoom(room);
        if (landlordsRoom == null) {
            throw new GameBusinessException("INVALID_ROOM", "房间类型不匹配");
        }
        return passInternal(landlordsRoom, userId);
    }

    /**
     * 不出内部方法
     */
    private GameStateResp passInternal(LandlordsRoom room, Long userId) {
        if (!room.getCurrentPlayerId().equals(userId)) {
            throw new GameBusinessException("NOT_YOUR_TURN", "还没轮到你出牌");
        }

        GameValidationResult validation = validateCanPass(room, userId);
        if (!validation.isValid()) {
            throw new GameBusinessException(validation.getErrorCode(), validation.getErrorMessage());
        }

        cancelPlayTimeout(room.getRoomId());

        LandlordsPlayer player = getLandlordsPlayer(room, userId);
        player.clearCurrentPlayedCards();
        String message = "不出";

        ActionResultResp actionResult = ActionResultResp.passResult(userId, player.getUserName(), message);
        broadcastActionResult(room, actionResult);

        if (room.getNextPlayerId(userId).equals(room.getLastPlayerId())) {
            room.setLastPlayedCards(new PokerHand());
            room.setLastPlayerId(null);
            room.getOrderedLandlordsPlayers().forEach(LandlordsPlayer::clearCurrentPlayedCards);
        }

        return advanceToNextPlayer(room, userId);
    }

    @Override
    public GameStateResp getGameState(GameRoom room) {
        LandlordsRoom landlordsRoom = toLandlordsRoom(room);
        if (landlordsRoom == null) {
            return null;
        }
        return buildGameState(landlordsRoom, null);
    }

    @Override
    public GameStateResp getGameState(GameRoom room, Long viewerId) {
        LandlordsRoom landlordsRoom = toLandlordsRoom(room);
        if (landlordsRoom == null) {
            return null;
        }
        return buildGameState(landlordsRoom, viewerId);
    }

    @Override
    public void reconnect(GameRoom room, Long userId) {
        updatePlayerStatus(room, userId, PlayerStatusEnum.ONLINE);
    }

    @Override
    public void disconnect(GameRoom room, Long userId) {
        updatePlayerStatus(room, userId, PlayerStatusEnum.OFFLINE);
    }

    /**
     * 获取斗地主玩家
     */
    private LandlordsPlayer getLandlordsPlayer(LandlordsRoom room, Long userId) {
        GamePlayer player = room.getPlayer(userId);
        if (player instanceof LandlordsPlayer) {
            return (LandlordsPlayer) player;
        }
        throw new GameBusinessException("INVALID_PLAYER", "玩家类型不匹配");
    }

    // ==================== 验证方法 ====================

    private GameValidationResult validateRobAction(LandlordsRoom room, Long userId, Integer action) {
        if (room.getState() != GameRoom.RoomState.ROBBING) {
            return GameValidationResult.invalid("PHASE_MISMATCH", "当前不在叫地主阶段");
        }
        if (!room.getCurrentRobPlayerId().equals(userId)) {
            return GameValidationResult.invalid("NOT_YOUR_TURN", "还没轮到你叫地主");
        }
        if (action < 0 || action > 3) {
            return GameValidationResult.invalid("INVALID_ACTION", "无效的叫分");
        }
        if (action > 0 && action <= room.getHighestRobScore()) {
            return GameValidationResult.invalid("INVALID_ACTION", "叫分必须高于当前最高分");
        }
        return GameValidationResult.valid();
    }

    private GameValidationResult validatePlayCards(LandlordsRoom room, Long userId, List<String> pokerIds) {
        if (room.getState() != GameRoom.RoomState.PLAYING) {
            return GameValidationResult.invalid("PHASE_MISMATCH", "当前不在出牌阶段");
        }
        if (!room.getCurrentPlayerId().equals(userId)) {
            return GameValidationResult.invalid("NOT_YOUR_TURN", "还没轮到你出牌");
        }
        if (pokerIds == null || pokerIds.isEmpty()) {
            return GameValidationResult.invalid("INVALID_CARDS", "请选择要出的牌");
        }

        LandlordsPlayer player = getLandlordsPlayer(room, userId);
        List<Poker> playedPokers = parsePokers(pokerIds);

        for (Poker playedPoker : playedPokers) {
            if (!containsCardByValue(player.getHand(), playedPoker)) {
                return GameValidationResult.invalid("INVALID_CARDS", "手牌中没有这些牌");
            }
        }

        PokerHand playedHand = new PokerHand(playedPokers);
        PokerHand lastPlayedCards = room.getLastPlayedCards();
        boolean isFirstPlay = room.getLastPlayerId() == null || room.getLastPlayerId().equals(userId);

        if (!PokerPatternMatcher.isValidPlay(playedPokers,
                lastPlayedCards != null && !lastPlayedCards.isEmpty()
                        ? PokerPatternMatcher.analyze(lastPlayedCards) : null,
                isFirstPlay)) {
            return GameValidationResult.invalid("INVALID_CARDS", "出牌不合法");
        }

        return GameValidationResult.valid();
    }

    private GameValidationResult validateCanPass(LandlordsRoom room, Long userId) {
        Long lastPlayerId = room.getLastPlayerId();
        PokerHand lastPlayedCards = room.getLastPlayedCards();

        if (lastPlayerId == null || lastPlayerId.equals(userId)) {
            return GameValidationResult.invalid("CANNOT_PASS", "第一个出牌不能选择不出");
        }
        if (lastPlayedCards == null || lastPlayedCards.isEmpty()) {
            return GameValidationResult.invalid("CANNOT_PASS", "上家还没出牌，你不能跳过");
        }
        return GameValidationResult.valid();
    }

    // ==================== 业务处理方法 ====================

    private GameStateResp handleRobCompletion(LandlordsRoom room, Long currentUserId, int action) {
        if (action == 3) {
            return handleRobWith3Points(room, currentUserId);
        }

        if (action > 0) {
            room.setLastRobPlayerId(currentUserId);
            room.getRobbedPlayers().add(currentUserId);
        } else {
            room.getPassedRobPlayers().add(currentUserId);
        }

        Long nextPlayerId = room.getNextPlayerId(currentUserId);
        boolean roundEnded = nextPlayerId.equals(room.getRobRoundStartPlayerId());

        if (roundEnded) {
            if (room.getRobbedPlayers().isEmpty()) {
                room.setRobRoundStartPlayerId(nextPlayerId);
                room.setLastRobPlayerId(null);
                room.getPassedRobPlayers().clear();
                return startGameInternal(room);
            }
            Long landlordId = room.getLastRobPlayerId();
            return determineLandlord(room, landlordId);
        }

        room.setCurrentRobPlayerId(nextPlayerId);
        broadcastRobTurnNotify(room, nextPlayerId);
        startRobTimeout(room);

        return buildGameState(room, null);
    }

    private GameStateResp handleRobWith3Points(LandlordsRoom room, Long currentUserId) {
        cancelRobTimeout(room.getRoomId());

        LandlordsPlayer player = getLandlordsPlayer(room, currentUserId);
        String message = "叫了 3 分";

        ActionResultResp actionResult = ActionResultResp.robResult(
                currentUserId,
                player.getUserName(),
                3,
                "3分",
                3,
                message
        );
        broadcastActionResult(room, actionResult);

        final Long landlordId = currentUserId;
        scheduler.schedule(() -> determineLandlordInternal(room, landlordId), 3, TimeUnit.SECONDS);

        return buildGameState(room, null);
    }

    private void determineLandlordInternal(LandlordsRoom room, Long landlordId) {
        room.setLandlord(landlordId);
        room.setState(GameRoom.RoomState.PLAYING);
        room.setCurrentPlayerId(landlordId);
        room.setLastPlayerId(landlordId);
        room.setLastPlayedCards(new PokerHand());

        cancelRobTimeout(room.getRoomId());

        for (LandlordsPlayer player : room.getOrderedLandlordsPlayers()) {
            sendPrivateState(room, player, player.getUserId());
        }

        ActionResultResp actionResult = ActionResultResp.landlordConfirmed(
                landlordId,
                getLandlordsPlayer(room, landlordId).getUserName(),
                room.getBottomCards().getAll(),
                "成为地主"
        );
        broadcastActionResult(room, actionResult);

        broadcastPlayTurnNotify(room, landlordId, false);
        roomManager.saveRoom(room);
    }

    private GameStateResp determineLandlord(LandlordsRoom room, Long landlordId) {
        room.setLandlord(landlordId);
        room.setState(GameRoom.RoomState.PLAYING);
        room.setCurrentPlayerId(landlordId);
        room.setLastPlayerId(landlordId);
        room.setLastPlayedCards(new PokerHand());

        cancelRobTimeout(room.getRoomId());

        for (LandlordsPlayer player : room.getOrderedLandlordsPlayers()) {
            sendPrivateState(room, player, player.getUserId());
        }

        ActionResultResp actionResult = ActionResultResp.landlordConfirmed(
                landlordId,
                getLandlordsPlayer(room, landlordId).getUserName(),
                room.getBottomCards().getAll(),
                "成为地主"
        );
        broadcastActionResult(room, actionResult);

        broadcastPlayTurnNotify(room, landlordId, false);
        roomManager.saveRoom(room);

        return buildGameState(room, landlordId);
    }

    private GameStateResp handleGameOver(LandlordsRoom room, Long winnerId) {
        log.info("游戏结束: roomId={}, winnerId={}", room.getRoomId(), winnerId);

        room.setState(GameRoom.RoomState.ENDING);
        cancelRobTimeout(room.getRoomId());
        cancelPlayTimeout(room.getRoomId());

        for (LandlordsPlayer player : room.getOrderedLandlordsPlayers()) {
            if (!player.isOnline()) {
                roomCache.removeUserRoom(player.getUserId());
            }
        }

        LandlordsPlayer winner = getLandlordsPlayer(room, winnerId);
        boolean isLandlordWin = room.isLandlord(winnerId);
        String winTeam = isLandlordWin ? "地主" : "农民";

        // 计算分数变化
        calculateScoreDeltas(room, winnerId);

        List<ActionResultResp.PlayerResultVO> playerResults = room.getOrderedLandlordsPlayers().stream()
                .map(p -> ActionResultResp.PlayerResultVO.builder()
                        .userId(p.getUserId())
                        .userName(p.getUserName())
                        .isWinner(p.getUserId().equals(winnerId))
                        .isLandlord(p.isLandlord())
                        .build())
                .collect(Collectors.toList());

        String message = String.format("游戏结束！%s 获胜！(%s方获胜)", winner.getUserName(), winTeam);

        ActionResultResp actionResult = ActionResultResp.gameOver(
                winnerId,
                winner.getUserName(),
                isLandlordWin,
                winTeam,
                playerResults,
                message
        );
        broadcastActionResult(room, actionResult);

        // 写入战绩
        recordGameStats(room, winnerId);

        GameStateResp gameState = buildGameState(room, null);
        broadcastGameState(room, null);

        room.resetForNewRound();

        Map<String, Object> resetData = new HashMap<>();
        resetData.put("roomInfo", room.toRoomInfoResp());
        resetData.put("players", room.toRoomInfoResp().getPlayers());
        resetData.put("playerCount", room.getPlayerCount());
        resetData.put("roomState", GameRoom.RoomState.WAITING.getCode());
        resetData.put("phase", GamePhaseEnum.WAITING);
        resetData.put("readyPhaseStartTime", room.getReadyPhaseStartTime());
        sessionManager.broadcastToRoom(room.getPlayerOrder(),
                GameMessageTypeEnum.STATE_UPDATE.getType(), resetData);

        roomManager.saveRoom(room);

        return gameState;
    }

    /**
     * 计算分数变化
     * 地主赢: 地主得 2*倍数, 农民各失 1*倍数
     * 地主输: 地主失 2*倍数, 农民各得 1*倍数
     */
    private void calculateScoreDeltas(LandlordsRoom room, Long winnerId) {
        int baseScore = 1; // 基础分数
        int multiple = room.getHighestRobScore() > 0 ? room.getHighestRobScore() : 1;
        int scoreChange = baseScore * multiple;

        boolean landlordWin = room.isLandlord(winnerId);

        for (LandlordsPlayer player : room.getOrderedLandlordsPlayers()) {
            if (player.isLandlord()) {
                // 地主
                player.setScoreDelta(landlordWin ? scoreChange : -scoreChange * 2);
            } else {
                // 农民
                player.setScoreDelta(landlordWin ? -scoreChange : scoreChange);
            }
        }
    }

    /**
     * 记录游戏战绩
     */
    private void recordGameStats(LandlordsRoom room, Long winnerId) {
        try {
            for (LandlordsPlayer player : room.getOrderedLandlordsPlayers()) {
                boolean isWinner = player.getUserId().equals(winnerId);
                String role = player.isLandlord() ? "landlord" : "farmer";

                GameStatsUpdateContext context = GameStatsUpdateContext.builder()
                        .userId(player.getUserId())
                        .gameType(GameTypeEnum.LANDLORDS_CLASSIC)
                        .roomId(room.getRoomId())
                        .isWin(isWinner)
                        .scoreDelta(player.getScoreDelta())
                        .role(role)
                        .build();

                gameStatsService.recordGameFinish(context);
                log.info("战绩写入成功: userId={}, isWin={}, role={}, scoreDelta={}",
                        player.getUserId(), isWinner, role, player.getScoreDelta());
            }
        } catch (Exception e) {
            log.error("战绩写入失败", e);
        }
    }

    public void forceEndGame(GameRoom room, String reason) {
        LandlordsRoom landlordsRoom = toLandlordsRoom(room);
        if (landlordsRoom == null) {
            return;
        }
        forceEndGameInternal(landlordsRoom, reason);
    }

    /**
     * 强制结束游戏内部方法
     */
    public void forceEndGameInternal(LandlordsRoom room, String reason) {
        cancelRobTimeout(room.getRoomId());
        cancelPlayTimeout(room.getRoomId());

        room.setState(GameRoom.RoomState.ENDING);
        room.exitReadyPhase();

        for (LandlordsPlayer player : room.getOrderedLandlordsPlayers()) {
            if (player.isRobotControlled()) {
                player.setRobotControlled(false);
            }
        }

        room.setBottomCards(null);

        ActionResultResp actionResult = ActionResultResp.builder()
                .event(GameActionEnum.GAME_FORCE_END.getCode())
                .phase(GamePhaseEnum.ENDING)
                .roomState(GameRoom.RoomState.ENDING)
                .message(reason)
                .build();
        broadcastActionResult(room, actionResult);

        broadcastGameState(room, null);
        roomManager.saveRoom(room);
    }

    private GameStateResp advanceToNextPlayer(LandlordsRoom room, Long currentUserId) {
        Long nextPlayerId = room.getNextPlayerId(currentUserId);
        room.setCurrentPlayerId(nextPlayerId);

        broadcastGameState(room, null);

        boolean canPass = room.getLastPlayerId() != null && !room.getLastPlayerId().equals(nextPlayerId);
        broadcastPlayTurnNotify(room, nextPlayerId, canPass);

        roomManager.saveRoom(room);
        return buildGameState(room, null);
    }

    // ==================== 状态构建方法 ====================

    private GameStateResp buildGameState(LandlordsRoom room, Long viewerId) {
        List<GameStateResp.PlayerStateVO> playerStates = buildPlayerStates(room, viewerId);

        List<GameStateResp.PokerCardVO> bottomCards = null;
        if (room.getLandlordId() != null && room.getBottomCards() != null) {
            bottomCards = GameStateResp.PokerCardVO.fromList(room.getBottomCards().getAll());
        }

        PokerHand lastPlayedCards = room.getLastPlayedCards();
        List<GameStateResp.PokerCardVO> lastPlayedCardList = null;
        String lastPlayerName = null;
        String lastPatternDesc = null;

        if (lastPlayedCards != null && !lastPlayedCards.isEmpty()) {
            PokerHand sortedLastPlayed = new PokerHand(lastPlayedCards.getAll());
            PokerSorter.sortByLandlordsWithUniversal(sortedLastPlayed);
            lastPlayedCardList = GameStateResp.PokerCardVO.fromList(sortedLastPlayed.getAll());
            Long lastPlayerId = room.getLastPlayerId();
            if (lastPlayerId != null) {
                LandlordsPlayer lastPlayer = getLandlordsPlayer(room, lastPlayerId);
                if (lastPlayer != null) {
                    lastPlayerName = lastPlayer.getUserName();
                }
            }
            lastPatternDesc = PokerComparator.getPatternDescription(PokerPatternMatcher.analyze(lastPlayedCards));
        }

        List<GameStateResp.PokerCardVO> handCards = null;
        if (viewerId != null) {
            LandlordsPlayer viewer = getLandlordsPlayer(room, viewerId);
            if (viewer != null && viewer.getHand() != null && !viewer.getHand().isEmpty()) {
                PokerHand sortedHand = new PokerHand(viewer.getHand().getAll());
                PokerSorter.sortByLandlordsWithUniversal(sortedHand);
                handCards = GameStateResp.PokerCardVO.fromList(sortedHand.getAll());
            }
        }

        return GameStateResp.builder()
                .roomId(room.getRoomId())
                .gameType(room.getGameType())
                .roomState(room.getState())
                .phase(toPhase(room.getState()))
                .ownerId(room.getOwnerId())
                .landlordId(room.getLandlordId())
                .bottomCards(bottomCards)
                .currentPlayerId(room.getCurrentPlayerId())
                .currentRobPlayerId(room.getCurrentRobPlayerId())
                .highestRobScore(room.getHighestRobScore())
                .players(playerStates)
                .lastPlayedCards(lastPlayedCardList)
                .lastPlayerId(room.getLastPlayerId())
                .lastPlayerName(lastPlayerName)
                .lastPatternDesc(lastPatternDesc)
                .handCards(handCards)
                .readyPhaseStartTime(room.getReadyPhaseStartTime() > 0L ? room.getReadyPhaseStartTime() : null)
                .build();
    }

    private GamePhaseEnum toPhase(GameRoom.RoomState state) {
        if (state == null) return GamePhaseEnum.WAITING;
        switch (state) {
            case DISTRIBUTING:
                return GamePhaseEnum.DEALING;
            case ROBBING:
                return GamePhaseEnum.ROBBING;
            case PLAYING:
                return GamePhaseEnum.PLAYING;
            case ENDING:
                return GamePhaseEnum.ENDING;
            case CLOSED:
                return GamePhaseEnum.CLOSED;
            case WAITING:
            default:
                return GamePhaseEnum.WAITING;
        }
    }

    private List<GameStateResp.PlayerStateVO> buildPlayerStates(LandlordsRoom room, Long viewerId) {
        return room.getOrderedLandlordsPlayers().stream()
                .map(p -> {
                    GameStateResp.PlayerStateVO.PlayerStateVOBuilder builder = GameStateResp.PlayerStateVO.builder()
                            .userId(p.getUserId())
                            .userName(p.getUserName())
                            .avatar(p.getAvatar())
                            .cardCount(p.getCardCount())
                            .isLandlord(p.isLandlord())
                            .isCurrentPlayer(p.getUserId().equals(room.getCurrentPlayerId()))
                            .isCurrentRobPlayer(p.getUserId().equals(room.getCurrentRobPlayerId()))
                            .isReady(p.isReady())
                            .isOnline(p.isOnline())
                            .isRobotControlled(p.isRobotControlled())
                            .robScore(p.getRobScore())
                            .role(p.getRole() != null ? p.getRole().name() : "PLAYER")
                            .currentPlayedCards(GameStateResp.PokerCardVO.fromList(p.getCurrentPlayedCards()));

                    if (viewerId == null || p.getUserId().equals(viewerId)) {
                        if (p.getHand() != null && !p.getHand().isEmpty()) {
                            PokerHand sortedHand = new PokerHand(p.getHand().getAll());
                            PokerSorter.sortByLandlordsWithUniversal(sortedHand);
                            builder.cards(GameStateResp.PokerCardVO.fromList(sortedHand.getAll()));
                        }
                    }

                    return builder.build();
                })
                .collect(Collectors.toList());
    }

    // ==================== 消息发送方法 ====================

    private void sendPrivateState(LandlordsRoom room, LandlordsPlayer player, Long userId) {
        GameStateResp state = buildGameState(room, userId);

        WSBaseResp<GameStateResp> wsBaseResp = WSBaseResp.<GameStateResp>builder()
                .type(GameMessageTypeEnum.STATE_UPDATE.getType())
                .data(state)
                .build();

        sessionManager.sendToUser(player.getUserId(), JSON.toJSONString(wsBaseResp, JSONWriter.Feature.WriteLongAsString));
    }

    private void broadcastGameState(LandlordsRoom room, Long excludeUserId) {
        List<Long> playerIds = room.getPlayerOrder();
        if (excludeUserId != null) {
            playerIds = playerIds.stream()
                    .filter(id -> !id.equals(excludeUserId))
                    .collect(Collectors.toList());
        }

        for (Long userId : playerIds) {
            LandlordsPlayer player = getLandlordsPlayer(room, userId);
            if (player != null) {
                sendPrivateState(room, player, userId);
            }
        }
    }

    private void broadcastGameStart(LandlordsRoom room) {
        LandlordsPlayer firstRobPlayer = getLandlordsPlayer(room, room.getCurrentRobPlayerId());
        String message = String.format("游戏开始！%s 先叫地主", firstRobPlayer.getUserName());

        TurnNotifyResp notify = TurnNotifyResp.builder()
                .event(GameActionEnum.GAME_START.getCode())
                .phase(GamePhaseEnum.ROBBING)
                .roomState(GameRoom.RoomState.ROBBING)
                .phaseDesc("叫地主阶段")
                .message(message)
                .build();

        String json = JSON.toJSONString(notify, JSONWriter.Feature.WriteLongAsString);
        for (Long userId : room.getPlayerOrder()) {
            WSBaseResp<Object> wsBaseResp = WSBaseResp.builder()
                    .type(GameMessageTypeEnum.START_GAME.getType())
                    .data(JSON.parseObject(json))
                    .build();
            sessionManager.sendToUser(userId, JSON.toJSONString(wsBaseResp, JSONWriter.Feature.WriteLongAsString));
        }
    }

    private void broadcastTurnNotify(LandlordsRoom room, TurnNotifyResp notify) {
        String json = JSON.toJSONString(notify, JSONWriter.Feature.WriteLongAsString);
        for (Long userId : room.getPlayerOrder()) {
            WSBaseResp<Object> wsBaseResp = WSBaseResp.<Object>builder()
                    .type(GameMessageTypeEnum.TURN_NOTIFY.getType())
                    .data(JSON.parseObject(json))
                    .build();
            sessionManager.sendToUser(userId, JSON.toJSONString(wsBaseResp, JSONWriter.Feature.WriteLongAsString));
        }
    }

    private void broadcastActionResult(LandlordsRoom room, ActionResultResp result) {
        String json = JSON.toJSONString(result, JSONWriter.Feature.WriteLongAsString);
        for (Long userId : room.getPlayerOrder()) {
            WSBaseResp<Object> wsBaseResp = WSBaseResp.<Object>builder()
                    .type(GameMessageTypeEnum.ACTION_RESULT.getType())
                    .data(JSON.parseObject(json))
                    .build();
            sessionManager.sendToUser(userId, JSON.toJSONString(wsBaseResp, JSONWriter.Feature.WriteLongAsString));
        }
    }

    public void cancelRobotControl(GameRoom room, Long userId) {
        updatePlayerStatus(room, userId, PlayerStatusEnum.ROBOT_DISABLED);
    }

    public void setRobotControl(GameRoom room, Long userId, RobotReasonEnum reason) {
        LandlordsRoom landlordsRoom = toLandlordsRoom(room);
        if (landlordsRoom == null) {
            return;
        }
        LandlordsPlayer player = getLandlordsPlayer(landlordsRoom, userId);
        if (player == null || player.isRobotControlled()) {
            return;
        }

        // 1. 设置状态并广播
        player.setRobotControlled(true);
        player.setRobotReason(reason);
        roomManager.saveRoom(landlordsRoom);
        broadcastPlayerStatusChange(landlordsRoom, player, PlayerStatusEnum.ROBOT_ENABLED);

        // 2. 执行后续业务逻辑
        boolean isCurrentRobPlayer = landlordsRoom.getState() == GameRoom.RoomState.ROBBING
                && landlordsRoom.getCurrentRobPlayerId() != null
                && landlordsRoom.getCurrentRobPlayerId().equals(userId);
        boolean isCurrentPlayPlayer = landlordsRoom.getState() == GameRoom.RoomState.PLAYING
                && landlordsRoom.getCurrentPlayerId() != null
                && landlordsRoom.getCurrentPlayerId().equals(userId);

        if (isCurrentRobPlayer) {
            int aiRobScore = robotService.getRobScore();
            robLandlordInternal(landlordsRoom, userId, aiRobScore);
        } else if (isCurrentPlayPlayer) {
            executeRobotPlay(landlordsRoom, userId);
        }
    }

    private void broadcastRobTurnNotify(LandlordsRoom room, Long currentRobPlayerId) {
        LandlordsPlayer player = getLandlordsPlayer(room, currentRobPlayerId);

        List<TurnNotifyResp.ActionOption> options = new ArrayList<>();
        int maxScore = Math.min(3, room.getHighestRobScore() + 1);
        for (int i = 0; i <= maxScore; i++) {
            boolean enabled = i == 0 || i > room.getHighestRobScore();
            options.add(TurnNotifyResp.ActionOption.builder()
                    .value(i)
                    .name(i == 0 ? "不叫" : i + "分")
                    .enabled(enabled)
                    .hint(enabled ? "" : "分数太低")
                    .build());
        }

        String message = String.format("请 %s 叫地主 (当前最高 %d 分)",
                player.getUserName(), room.getHighestRobScore());

        TurnNotifyResp notify = TurnNotifyResp.builder()
                .event(GameActionEnum.TURN_START.getCode())
                .phase(GamePhaseEnum.ROBBING)
                .roomState(GameRoom.RoomState.ROBBING)
                .phaseDesc("叫地主阶段")
                .currentPlayerId(currentRobPlayerId)
                .currentPlayerName(player.getUserName())
                .action(GameActionEnum.ROB.getCode())
                .actionOptions(options)
                .canPass(true)
                .canPlay(false)
                .timeout(GameConstants.TURN_TIMEOUT_SECONDS)
                .startTime(System.currentTimeMillis())
                .highestScore(room.getHighestRobScore())
                .message(message)
                .build();

        broadcastTurnNotify(room, notify);
    }

    private void broadcastPlayTurnNotify(LandlordsRoom room, Long currentPlayerId, boolean canPass) {
        LandlordsPlayer player = getLandlordsPlayer(room, currentPlayerId);
        String message = canPass
                ? String.format("请 %s 出牌或选择不出", player.getUserName())
                : String.format("请 %s 出牌", player.getUserName());

        if (player.isRobotControlled()) {
            log.info("轮到托管玩家出牌，延迟2秒执行AI: playerId={}", currentPlayerId);

            TurnNotifyResp notify = TurnNotifyResp.builder()
                    .event(GameActionEnum.TURN_START.getCode())
                    .phase(GamePhaseEnum.PLAYING)
                    .roomState(GameRoom.RoomState.PLAYING)
                    .phaseDesc("出牌阶段")
                    .currentPlayerId(currentPlayerId)
                    .currentPlayerName(player.getUserName())
                    .action(GameActionEnum.PLAY.getCode())
                    .canPass(canPass)
                    .canPlay(true)
                    .timeout(GameConstants.TURN_TIMEOUT_SECONDS)
                    .startTime(System.currentTimeMillis())
                    .message(message)
                    .build();

            broadcastTurnNotify(room, notify);

            scheduler.schedule(() -> {
                log.info("AI延迟出牌开始: playerId={}", currentPlayerId);
                executeRobotPlay(room, currentPlayerId);
            }, 2, TimeUnit.SECONDS);

            return;
        }

        TurnNotifyResp notify = TurnNotifyResp.builder()
                .event(GameActionEnum.TURN_START.getCode())
                .phase(GamePhaseEnum.PLAYING)
                .roomState(GameRoom.RoomState.PLAYING)
                .phaseDesc("出牌阶段")
                .currentPlayerId(currentPlayerId)
                .currentPlayerName(player.getUserName())
                .action(GameActionEnum.PLAY.getCode())
                .canPass(canPass)
                .canPlay(true)
                .timeout(GameConstants.TURN_TIMEOUT_SECONDS)
                .startTime(System.currentTimeMillis())
                .message(message)
                .build();

        broadcastTurnNotify(room, notify);
        startPlayTimeout(room);
    }

    // ==================== 超时管理 ====================

    private void startRobTimeout(LandlordsRoom room) {
        String roomId = room.getRoomId();
        cancelRobTimeout(roomId);

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            if (room.getState() == GameRoom.RoomState.ROBBING) {
                try {
                    Long currentPlayerId = room.getCurrentRobPlayerId();
                    LandlordsPlayer player = getLandlordsPlayer(room, currentPlayerId);

                    player.setRobotControlled(true);
                    player.setRobotReason(RobotReasonEnum.TIMEOUT);
                    roomManager.saveRoom(room);

                    broadcastPlayerStatusChange(room, player, PlayerStatusEnum.ROBOT_ENABLED);

                    int aiRobScore = robotService.getRobScore();
                    robLandlordInternal(room, currentPlayerId, aiRobScore);
                } catch (GameBusinessException e) {
                    log.error("叫地主超时处理失败: {}", e.getMessage());
                }
            }
        }, GameConstants.TURN_TIMEOUT_SECONDS * 1000L, TimeUnit.MILLISECONDS);

        robTimeoutTasks.put(roomId, future);
    }

    private void startPlayTimeout(LandlordsRoom room) {
        String roomId = room.getRoomId();
        cancelPlayTimeout(roomId);

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            if (room.getState() == GameRoom.RoomState.PLAYING) {
                try {
                    Long currentPlayerId = room.getCurrentPlayerId();
                    LandlordsPlayer currentPlayer = getLandlordsPlayer(room, currentPlayerId);

                    if (currentPlayer.getHand() == null || currentPlayer.getHand().isEmpty()) {
                        log.error("出牌超时: 玩家手牌为空");
                        return;
                    }

                    currentPlayer.setRobotControlled(true);
                    currentPlayer.setRobotReason(RobotReasonEnum.TIMEOUT);
                    roomManager.saveRoom(room);

                    broadcastPlayerStatusChange(room, currentPlayer, PlayerStatusEnum.ROBOT_ENABLED);
                    executeRobotPlay(room, currentPlayerId);
                } catch (GameBusinessException e) {
                    log.error("出牌超时处理失败: {}", e.getMessage());
                }
            }
        }, GameConstants.TURN_TIMEOUT_SECONDS * 1000L, TimeUnit.MILLISECONDS);

        playTimeoutTasks.put(roomId, future);
    }

    private void cancelRobTimeout(String roomId) {
        ScheduledFuture<?> task = robTimeoutTasks.remove(roomId);
        if (task != null) {
            task.cancel(false);
        }
    }

    private void cancelPlayTimeout(String roomId) {
        ScheduledFuture<?> task = playTimeoutTasks.remove(roomId);
        if (task != null) {
            task.cancel(false);
        }
    }

    private void executeRobotPlay(LandlordsRoom room, Long playerId) {
        LandlordsPlayer player = getLandlordsPlayer(room, playerId);
        PokerHand hand = player.getHand();

        if (hand == null || hand.isEmpty()) {
            log.error("AI出牌: 玩家手牌为空");
            return;
        }

        boolean canPass = room.getLastPlayerId() != null && !room.getLastPlayerId().equals(playerId);

        if (canPass) {
            List<String> playCards = robotService.getPlayCards(room, playerId);
            if (playCards.isEmpty()) {
                log.info("AI托管无法压牌: playerId={}", playerId);
                passInternal(room, playerId);
            } else {
                log.info("AI托管出牌: playerId={}, cards={}", playerId, playCards);
                playCardsInternal(room, playerId, playCards);
            }
        } else {
            PokerSorter.sortByLandlords(hand);
            Poker smallestCard = hand.getAll().get(hand.getAll().size() - 1);
            String cardId = smallestCard.getId();
            log.info("AI托管出最小牌: playerId={}, card={}", playerId, cardId);
            playCardsInternal(room, playerId, Collections.singletonList(cardId));
        }
    }

    // ==================== 工具方法 ====================

    private void validatePlayerCount(LandlordsRoom room) {
        if (room.getPlayerCount() < 3) {
            throw new GameBusinessException("INVALID_PLAYER_COUNT", "斗地主需要至少3名玩家");
        }
    }

    private List<Poker> parsePokers(List<String> pokerIds) {
        return pokerIds.stream()
                .map(PokerGenerator::parseById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private boolean containsCardByValue(PokerHand hand, Poker target) {
        if (hand == null || target == null) {
            return false;
        }
        for (Poker p : hand.getAll()) {
            if (p.getValue() == target.getValue()) {
                return true;
            }
        }
        return false;
    }

    private boolean removeCardByValue(PokerHand hand, Poker target) {
        if (hand == null || target == null) {
            return false;
        }
        for (Poker p : hand.getAll()) {
            if (p.getValue() == target.getValue()) {
                hand.remove(p);
                return true;
            }
        }
        return false;
    }

    public List<String> getPlayerHand(LandlordsRoom room, Long userId) {
        LandlordsPlayer player = getLandlordsPlayer(room, userId);
        if (player != null && player.getHand() != null) {
            return player.getHand().toIdList();
        }
        return null;
    }

    public void cleanupRoomTasks(String roomId) {
        cancelRobTimeout(roomId);
        cancelPlayTimeout(roomId);
    }

    // ==================== 统一状态更新入口 ====================

    /**
     * 统一状态更新入口
     * 所有玩家状态变更都通过此方法处理，包括：
     * - 玩家离线/在线
     * - AI托管开启/关闭
     *
     * @param room    房间
     * @param userId  玩家ID
     * @param type    状态变更类型
     */
    public void updatePlayerStatus(GameRoom room, Long userId, PlayerStatusEnum type) {
        LandlordsRoom landlordsRoom = toLandlordsRoom(room);
        if (landlordsRoom == null) {
            return;
        }

        LandlordsPlayer player = getLandlordsPlayer(landlordsRoom, userId);
        if (player == null) {
            return;
        }

        // 1. 修改状态
        boolean statusChanged = applyPlayerStatus(player, type);

        // 2. 保存房间
        roomManager.saveRoom(landlordsRoom);

        // 3. 如果状态有变化，广播给所有玩家
        if (statusChanged) {
            broadcastPlayerStatusChange(landlordsRoom, player, type);
        }
    }

    /**
     * 应用玩家状态变更
     *
     * @param player 玩家
     * @param type   状态类型
     * @return 是否实际发生了状态变化
     */
    private boolean applyPlayerStatus(LandlordsPlayer player, PlayerStatusEnum type) {
        switch (type) {
            case OFFLINE:
                player.setOnline(false);
                return true;

            case ONLINE:
                player.setOnline(true);
                return true;

            case ROBOT_ENABLED:
                if (!player.isRobotControlled()) {
                    player.setRobotControlled(true);
                    if (player.getRobotReason() == null) {
                        player.setRobotReason(RobotReasonEnum.MANUAL);
                    }
                    return true;
                }
                return false;

            case ROBOT_DISABLED:
                if (player.isRobotControlled()) {
                    player.setRobotControlled(false);
                    player.setRobotReason(null);
                    return true;
                }
                return false;

            default:
                return false;
        }
    }

    /**
     * 广播玩家状态变更
     *
     * @param room   房间
     * @param player 玩家
     * @param type   状态类型
     */
    private void broadcastPlayerStatusChange(LandlordsRoom room, LandlordsPlayer player, PlayerStatusEnum type) {
        String message = buildStatusChangeMessage(player, type);

        ActionResultResp actionResult = ActionResultResp.builder()
                .event(getEventForStatusType(type))
                .phase(toPhase(room.getState()))
                .roomState(room.getState())
                .playerId(player.getUserId())
                .playerName(player.getUserName())
                .action(GameActionEnum.ROBOT.getCode())
                .result(message)
                .message(message)
                .build();

        broadcastActionResult(room, actionResult);

        // 广播完整游戏状态，确保前端同步
        if (room.getState().isPlaying()) {
            broadcastGameState(room, null);
        }
    }

    /**
     * 根据状态类型获取对应的事件
     */
    private String getEventForStatusType(PlayerStatusEnum type) {
        switch (type) {
            case OFFLINE:
                return GameActionEnum.PLAYER_STATUS_CHANGE.getCode();
            case ONLINE:
                return GameActionEnum.PLAYER_RECONNECT.getCode();
            case ROBOT_ENABLED:
                return GameActionEnum.ROBOT_ENABLED.getCode();
            case ROBOT_DISABLED:
                return GameActionEnum.ROBOT_DISABLED.getCode();
            default:
                return GameActionEnum.PLAYER_STATUS_CHANGE.getCode();
        }
    }

    /**
     * 构建状态变更消息
     */
    private String buildStatusChangeMessage(LandlordsPlayer player, PlayerStatusEnum type) {
        switch (type) {
            case OFFLINE:
                return String.format("%s 离线", player.getUserName());
            case ONLINE:
                return String.format("%s 已重连", player.getUserName());
            case ROBOT_ENABLED:
                RobotReasonEnum reason = player.getRobotReason();
                String reasonDesc = reason == RobotReasonEnum.TIMEOUT ? "超时" :
                        (reason == RobotReasonEnum.LEAVE ? "离开" : "主动托管");
                return String.format("%s [%s]，AI托管中", player.getUserName(), reasonDesc);
            case ROBOT_DISABLED:
                return String.format("%s 取消了AI托管", player.getUserName());
            default:
                return String.format("%s 状态变更", player.getUserName());
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    private static class GameValidationResult {
        private boolean valid;
        private String errorCode;
        private String errorMessage;

        public static GameValidationResult valid() {
            return new GameValidationResult(true, null, null);
        }

        public static GameValidationResult invalid(String errorCode, String errorMessage) {
            return new GameValidationResult(false, errorCode, errorMessage);
        }
    }
}
