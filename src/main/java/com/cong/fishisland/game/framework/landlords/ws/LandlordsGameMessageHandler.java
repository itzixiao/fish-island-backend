package com.cong.fishisland.game.framework.landlords.ws;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.cong.fishisland.game.common.enums.GameTypeEnum;
import com.cong.fishisland.game.common.enums.PlayerStatusEnum;
import com.cong.fishisland.game.common.enums.RobotReasonEnum;
import com.cong.fishisland.game.common.manager.GameRoomManager;
import com.cong.fishisland.game.common.manager.GameSessionManager;
import com.cong.fishisland.game.common.model.dto.request.CreateRoomReq;
import com.cong.fishisland.game.common.model.dto.request.JoinRoomReq;
import com.cong.fishisland.game.common.model.dto.request.RoomListReq;
import com.cong.fishisland.game.common.model.dto.response.*;
import com.cong.fishisland.game.common.model.player.GamePlayer;
import com.cong.fishisland.game.common.model.room.GameRoom;
import com.cong.fishisland.game.common.model.session.GameSession;
import com.cong.fishisland.game.common.ws.GameMessageHandler;
import com.cong.fishisland.game.framework.landlords.dto.request.PlayCardsReq;
import com.cong.fishisland.game.framework.landlords.dto.request.RobLandlordReq;
import com.cong.fishisland.game.framework.landlords.dto.response.GameStateResp;
import com.cong.fishisland.game.framework.landlords.enums.GameActionEnum;
import com.cong.fishisland.game.framework.landlords.enums.GameMessageTypeEnum;
import com.cong.fishisland.game.framework.landlords.enums.GamePhaseEnum;
import com.cong.fishisland.game.framework.landlords.model.LandlordsPlayer;
import com.cong.fishisland.game.framework.landlords.model.LandlordsRoom;
import com.cong.fishisland.game.framework.landlords.model.poker.Poker;
import com.cong.fishisland.game.framework.landlords.model.poker.PokerHand;
import com.cong.fishisland.game.framework.landlords.service.GameBusinessException;
import com.cong.fishisland.game.framework.landlords.service.LandlordsGameService;
import com.cong.fishisland.model.entity.user.User;
import com.cong.fishisland.model.ws.response.WSBaseResp;
import com.cong.fishisland.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * 斗地主游戏消息处理器
 * 统一管理游戏消息的处理和分发
 *
 * @author cong
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LandlordsGameMessageHandler implements GameMessageHandler {

    private final GameRoomManager roomManager;
    private final GameSessionManager sessionManager;
    private final LandlordsGameService gameService;
    private final UserService userService;

    private Map<GameMessageTypeEnum, BiFunction<String, Long, GameMessageResult>> handlers;

    @Override
    public GameTypeEnum getGameType() {
        return GameTypeEnum.LANDLORDS_CLASSIC;
    }

    @Override
    public Object handle(String messageType, String jsonContent, Long userId) {
        GameMessageTypeEnum type = GameMessageTypeEnum.of(messageType);
        if (type == null) {
            return GameMessageResult.error(messageType, "未知的消息类型");
        }

        BiFunction<String, Long, GameMessageResult> handler = handlers.get(type);
        if (handler == null) {
            return GameMessageResult.error(messageType, "该消息类型不支持");
        }

        try {
            return handler.apply(jsonContent, userId);
        } catch (GameBusinessException e) {
            log.warn("游戏业务异常: type={}, userId={}, error={}", type, userId, e.getMessage());
            return GameMessageResult.error(type.getType(), e.getMessage());
        } catch (Exception e) {
            log.error("处理游戏消息失败: type={}, userId={}", type, userId, e);
            return GameMessageResult.error(type.getType(), "服务器内部错误");
        }
    }

    @Override
    public void onDisconnect(Long userId) {
        String roomId = roomManager.getUserRoomId(userId);
        if (roomId != null) {
            GameRoom room = roomManager.getRoom(roomId);
            if (room instanceof LandlordsRoom) {
                LandlordsRoom landlordsRoom = (LandlordsRoom) room;
                gameService.disconnect(landlordsRoom, userId);
                // 状态广播已由 gameService.disconnect() 内部处理

                if (landlordsRoom.getOnlinePlayerCount() == 0) {
                    if (landlordsRoom.getState() == GameRoom.RoomState.PLAYING || landlordsRoom.getState() == GameRoom.RoomState.ROBBING) {
                        gameService.forceEndGame(landlordsRoom, "所有玩家都已离线");
                    }
                    roomManager.removeRoom(roomId);
                }
            }
            roomManager.leaveRoom(roomId, userId);
        }
    }

    @PostConstruct
    public void initHandlers() {
        handlers = new HashMap<>();

        handlers.put(GameMessageTypeEnum.CREATE_ROOM, this::handleCreateRoom);
        handlers.put(GameMessageTypeEnum.JOIN_ROOM, this::handleJoinRoom);
        handlers.put(GameMessageTypeEnum.LEAVE_ROOM, this::handleLeaveRoom);
        handlers.put(GameMessageTypeEnum.ROOM_LIST, this::handleRoomList);
        handlers.put(GameMessageTypeEnum.READY, this::handleReady);
        handlers.put(GameMessageTypeEnum.START_GAME, this::handleStartGame);
        handlers.put(GameMessageTypeEnum.ROB_LANDLORD, this::handleRobLandlord);
        handlers.put(GameMessageTypeEnum.PLAY_CARDS, this::handlePlayCards);
        handlers.put(GameMessageTypeEnum.PASS, this::handlePass);
        handlers.put(GameMessageTypeEnum.CANCEL_ROBOT, this::handleCancelRobot);
        handlers.put(GameMessageTypeEnum.SET_ROBOT, this::handleSetRobot);
        handlers.put(GameMessageTypeEnum.CHAT, this::handleChat);
    }

    // ==================== 房间管理 ====================

    private GameMessageResult handleCreateRoom(String json, Long userId) {
        CreateRoomReq req = parseJson(json, CreateRoomReq.class);

        GameTypeEnum gameType = parseGameType(req != null ? req.getGameType() : null);

        Map<String, String> userInfo = getUserInfo(userId);
        String userName = userInfo.get("userName");
        String userAvatar = userInfo.get("avatar");

        GameRoom room = roomManager.createRoom(gameType, userId, userName, userAvatar);

        // 构建统一的 RoomStateResp
        RoomStateResp roomState = buildRoomStateResp("CREATE", room, userId);

        log.info("[创建房间] roomId={}, userId={}", room.getRoomId(), userId);

        // 返回统一的 ROOM_STATE 消息
        return GameMessageResult.success(GameMessageTypeEnum.ROOM_STATE.getType(), roomState);
    }

    private GameMessageResult handleJoinRoom(String json, Long userId) {
        JoinRoomReq req = parseJson(json, JoinRoomReq.class);

        if (req != null && !StringUtils.hasText(req.getRoomId())) {
            return GameMessageResult.error(GameMessageTypeEnum.ROOM_STATE.getType(), "房间号不能为空");
        }

        if (roomManager.hasRoomRestriction(userId)) {
            GameRoomManager.GameRoomRestrictionInfo restrictionInfo = roomManager.getRoomRestrictionInfo(userId);
            if (req != null && restrictionInfo != null && req.getRoomId().equals(restrictionInfo.getRoomId())) {
                // 允许回到自己的房间
            } else {
                String message = String.format("你正在房间 %s 中游戏（%s），请先回到该房间或等待游戏结束后再加入其他房间",
                        restrictionInfo != null ? restrictionInfo.getRoomId() : "",
                        restrictionInfo != null ? restrictionInfo.getReason() : "");
                return GameMessageResult.error(GameMessageTypeEnum.ROOM_STATE.getType(), message);
            }
        }

        Map<String, String> userInfo = getUserInfo(userId);
        String userName = userInfo.get("userName");
        String userAvatar = userInfo.get("avatar");

        GameRoom targetRoom = null;
        if (req != null) {
            targetRoom = roomManager.getRoom(req.getRoomId());
        }
        boolean isRoomOwner = targetRoom != null && targetRoom.getOwnerId() != null
                && targetRoom.getOwnerId().equals(userId);

        boolean isExistingPlayer = targetRoom != null && targetRoom.getPlayer(userId) != null;
        GameSession cachedSession = roomManager.getUserSession(userId);

        boolean isReconnecting = isExistingPlayer && cachedSession != null && !cachedSession.isOnline() && !isRoomOwner;

        GameRoom room = null;
        if (req != null) {
            room = roomManager.joinRoom(req.getRoomId(), userId, userName, userAvatar, req.getPassword(), isRoomOwner);
        }

        if (room == null) {
            if (isReconnecting) {
                return GameMessageResult.error(GameMessageTypeEnum.ROOM_STATE.getType(), "重连失败，请重新加入");
            }
            return GameMessageResult.error(GameMessageTypeEnum.ROOM_STATE.getType(), "加入房间失败");
        }

        GameSession session = roomManager.getUserSession(userId);
        if (session != null && session.hasTempLeave()
                && req.getRoomId().equals(session.getTempLeaveRoomId())) {
            session.clearTempLeave();
        }

        // 构建统一的 RoomStateResp
        RoomStateResp roomState = buildRoomStateResp("JOIN", room, userId);

        if (isReconnecting && room instanceof LandlordsRoom) {
            LandlordsRoom landlordsRoom = (LandlordsRoom) room;
            LandlordsPlayer reconnectPlayer = landlordsRoom.getPlayer(userId) instanceof LandlordsPlayer
                    ? (LandlordsPlayer) landlordsRoom.getPlayer(userId)
                    : null;
            boolean wasRobotControlled = reconnectPlayer != null && reconnectPlayer.isRobotControlled();

            if (wasRobotControlled) {
                // 如果玩家在 AI 托管中，取消托管
                // cancelRobotControl 会处理状态更新和广播
                gameService.cancelRobotControl(landlordsRoom, userId);
            } else {
                // 如果不在托管中，调用重连方法处理状态更新和广播
                gameService.reconnect(landlordsRoom, userId);

                // 广播给房间内其他玩家（重连事件）
                Map<String, Object> stateUpdateData = new HashMap<>();
                stateUpdateData.put("event", GameActionEnum.PLAYER_RECONNECT.getCode());
                stateUpdateData.put("reconnectUserId", userId);
                stateUpdateData.put("players", room.toRoomInfoResp().getPlayers());

                sessionManager.broadcastToRoomExcept(userId, landlordsRoom.getPlayerOrder(),
                        GameMessageTypeEnum.STATE_UPDATE.getType(), stateUpdateData);
            }

            // 添加重连信息
            roomState.setAction("RECONNECT");
        } else {
            // 广播给房间内其他玩家（玩家加入消息）
            Map<String, Object> stateUpdateData = new HashMap<>();
            stateUpdateData.put("event", GameActionEnum.PLAYER_JOIN.getCode());
            stateUpdateData.put("players", room.toRoomInfoResp().getPlayers());
            stateUpdateData.put("playerCount", room.getPlayerCount());

            sessionManager.broadcastToRoomExcept(userId, room.getPlayerOrder(),
                    GameMessageTypeEnum.STATE_UPDATE.getType(), stateUpdateData);
        }

        log.info("[加入房间] roomId={}, userId={}, isReconnecting={}", room.getRoomId(), userId, isReconnecting);

        // 返回统一的 ROOM_STATE 消息
        return GameMessageResult.success(GameMessageTypeEnum.ROOM_STATE.getType(), roomState);
    }

    private GameMessageResult handleLeaveRoom(String json, Long userId) {
        GameRoom room = getUserRoom(userId);
        if (room instanceof LandlordsRoom) {
            LandlordsRoom landlordsRoom = (LandlordsRoom) room;
            LandlordsPlayer player = landlordsRoom.getPlayer(userId) instanceof LandlordsPlayer
                    ? (LandlordsPlayer) landlordsRoom.getPlayer(userId)
                    : null;
            String playerName = player != null ? player.getUserName() : "玩家";

            GameRoom.RoomState state = landlordsRoom.getState();
            boolean inGame = state == GameRoom.RoomState.DISTRIBUTING || state == GameRoom.RoomState.ROBBING
                    || state == GameRoom.RoomState.PLAYING;
            boolean gameEnded = state == GameRoom.RoomState.ENDING || state == GameRoom.RoomState.CLOSED;

            if (inGame) {
                // 设置 AI 托管（内部会广播状态变更）
                gameService.setRobotControl(landlordsRoom, userId, RobotReasonEnum.LEAVE);

                GameSession session = roomManager.getUserSession(userId);
                if (session != null) {
                    session.setTempLeave(landlordsRoom.getRoomId());
                    roomManager.saveSession(session);
                }

                if (landlordsRoom.getOnlinePlayerCount() == 0) {
                    log.info("所有玩家都已离线，强制结束游戏并关闭房间: roomId={}", landlordsRoom.getRoomId());
                    if (state == GameRoom.RoomState.PLAYING || state == GameRoom.RoomState.ROBBING) {
                        gameService.forceEndGame(landlordsRoom, "所有玩家都已离线");
                    }
                    roomManager.removeRoom(landlordsRoom.getRoomId());
                }

                Map<String, Object> resultData = new HashMap<>();
                resultData.put("tempLeaveRoomId", landlordsRoom.getRoomId());
                resultData.put("message", "游戏仍在进行中，你可以随时回来");
                return GameMessageResult.success(GameMessageTypeEnum.LEAVE_ROOM.getType(), resultData);
            } else if (gameEnded) {
                roomManager.leaveRoom(landlordsRoom.getRoomId(), userId);
                broadcastPlayerStatusChange(landlordsRoom, userId);

                if (landlordsRoom.getPlayerCount() > 0) {
                    RoomStateUpdateResp eventBuilder = RoomStateUpdateResp.builder()
                            .event(GameActionEnum.PLAYER_LEAVE.getCode())
                            .playerName(playerName)
                            .playerCount(landlordsRoom.getPlayerCount())
                            .roomInfo(landlordsRoom.toRoomInfoResp())
                            .build();
                    sessionManager.broadcastToRoomExcept(userId, landlordsRoom.getPlayerOrder(),
                            GameMessageTypeEnum.STATE_UPDATE.getType(), eventBuilder);
                }
            } else {
                roomManager.leaveRoom(landlordsRoom.getRoomId(), userId);

                if (landlordsRoom.getPlayerCount() > 0) {
                    RoomStateUpdateResp eventBuilder = RoomStateUpdateResp.builder()
                            .event(GameActionEnum.PLAYER_LEAVE.getCode())
                            .playerName(playerName)
                            .playerCount(landlordsRoom.getPlayerCount())
                            .roomInfo(landlordsRoom.toRoomInfoResp())
                            .build();
                    sessionManager.broadcastToRoomExcept(userId, landlordsRoom.getPlayerOrder(),
                            GameMessageTypeEnum.STATE_UPDATE.getType(), eventBuilder);
                }
            }
        }

        return GameMessageResult.success(GameMessageTypeEnum.LEAVE_ROOM.getType(), null);
    }

    private GameMessageResult handleRoomList(String json, Long userId) {
        RoomListReq req = parseJson(json, RoomListReq.class);

        GameTypeEnum gameType = parseGameType(req != null ? req.getGameType() : null);

        List<RoomListResp.RoomItem> roomItems = roomManager.getRoomList(gameType).stream()
                .map(info -> {
                    GameRoom room = roomManager.getRoom(info.getRoomId());
                    if (room == null) return null;
                    RoomInfoResp roomInfo = room.toRoomInfoResp();
                    return RoomListResp.RoomItem.builder()
                            .roomId(roomInfo.getRoomId())
                            .gameType(roomInfo.getGameType())
                            .playerCount(roomInfo.getPlayerCount())
                            .maxPlayers(roomInfo.getMaxPlayers())
                            .needPassword(roomInfo.getNeedPassword())
                            .ownerId(roomInfo.getOwnerId())
                            .players(roomInfo.getPlayers())
                            .build();
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        GameRoomManager.GameRoomRestrictionInfo restrictionInfo = roomManager.getRoomRestrictionInfo(userId);

        RoomListResp.RoomRestrictionInfo restriction = null;
        if (restrictionInfo != null) {
            restriction = RoomListResp.RoomRestrictionInfo.builder()
                    .roomId(restrictionInfo.getRoomId())
                    .gameType(restrictionInfo.getGameType())
                    .state(restrictionInfo.getState() != null ? restrictionInfo.getState().getCode() : null)
                    .reason(restrictionInfo.getReason())
                    .build();
        }

        return GameMessageResult.success(GameMessageTypeEnum.ROOM_LIST.getType(),
                RoomListResp.builder()
                        .rooms(roomItems)
                        .restriction(restriction)
                        .total(roomItems.size())
                        .build());
    }

    // ==================== 游戏准备 ====================

    private GameMessageResult handleReady(String json, Long userId) {
        GameRoom room = getUserRoom(userId);
        if (room == null) {
            return GameMessageResult.error(GameMessageTypeEnum.READY.getType(), "你不在任何房间中");
        }

        GamePlayer player = room.getPlayer(userId);
        if (player == null) {
            return GameMessageResult.error(GameMessageTypeEnum.READY.getType(), "不在此房间中");
        }

        if (room.getState() != GameRoom.RoomState.WAITING) {
            return GameMessageResult.error(GameMessageTypeEnum.READY.getType(),
                    "当前房间状态不允许准备（" + room.getState() + "）");
        }

        boolean newReadyState = !player.isReady();
        player.setReady(newReadyState);
        if (newReadyState) {
            // 只有 LandlordsPlayer 需要清空手牌
            if (player instanceof LandlordsPlayer) {
                LandlordsPlayer lp = (LandlordsPlayer) player;
                if (lp.getHand() != null) {
                    lp.getHand().clear();
                }
            }
            player.resetForNewGame();
        }
        roomManager.saveRoom(room);

        Map<String, Object> broadcastData = new HashMap<>();
        broadcastData.put("roomInfo", room.toRoomInfoResp());
        broadcastData.put("players", room.toRoomInfoResp().getPlayers());
        broadcastData.put("playerCount", room.getPlayerCount());
        broadcastData.put("readyPhaseStartTime", room.getReadyPhaseStartTime());
        sessionManager.broadcastToRoom(room.getPlayerOrder(),
                GameMessageTypeEnum.STATE_UPDATE.getType(), broadcastData);

        return GameMessageResult.success(GameMessageTypeEnum.READY.getType(), room.toRoomInfoResp());
    }

    private GameMessageResult handleStartGame(String json, Long userId) {
        GameRoom room = getUserRoom(userId);
        if (room == null) {
            return GameMessageResult.error(GameMessageTypeEnum.START_GAME.getType(), "你不在任何房间中");
        }

        if (!(room instanceof LandlordsRoom)) {
            return GameMessageResult.error(GameMessageTypeEnum.START_GAME.getType(), "房间类型不支持");
        }

        LandlordsRoom landlordsRoom = (LandlordsRoom) room;

        if (!landlordsRoom.getOwnerId().equals(userId)) {
            return GameMessageResult.error(GameMessageTypeEnum.START_GAME.getType(), "只有房主可以开始游戏");
        }

        if (landlordsRoom.getPlayerCount() < 3) {
            return GameMessageResult.error(GameMessageTypeEnum.START_GAME.getType(), "斗地主需要至少3名玩家");
        }

        if (!landlordsRoom.isAllReady()) {
            return GameMessageResult.error(GameMessageTypeEnum.START_GAME.getType(), "所有玩家必须准备才能开始游戏");
        }

        GameStateResp gameState = gameService.startGame(landlordsRoom);

        for (Long playerId : landlordsRoom.getPlayerOrder()) {
            LandlordsPlayer p = landlordsRoom.getPlayer(playerId) instanceof LandlordsPlayer
                    ? (LandlordsPlayer) landlordsRoom.getPlayer(playerId)
                    : null;
            GameStateResp privateState = GameStateResp.builder()
                    .roomId(landlordsRoom.getRoomId())
                    .gameType(landlordsRoom.getGameType())
                    .roomState(landlordsRoom.getState())
                    .phase(GamePhaseEnum.ROBBING)
                    .currentRobPlayerId(landlordsRoom.getCurrentRobPlayerId())
                    .highestRobScore(landlordsRoom.getHighestRobScore())
                    .players(gameState.getPlayers())
                    .build();
            if (p != null && p.getHand() != null && !p.getHand().isEmpty()) {
                privateState.setHandCards(sortAndConvertHand(p.getHand()));
            }
            WSBaseResp<GameStateResp> wsResp = WSBaseResp.<GameStateResp>builder()
                    .type(GameMessageTypeEnum.START_GAME.getType())
                    .data(privateState)
                    .build();
            sessionManager.sendToUser(playerId, JSON.toJSONString(wsResp, JSONWriter.Feature.WriteLongAsString));
        }

        LandlordsPlayer player = landlordsRoom.getPlayer(userId) instanceof LandlordsPlayer
                ? (LandlordsPlayer) landlordsRoom.getPlayer(userId)
                : null;
        if (player != null && player.getHand() != null && !player.getHand().isEmpty()) {
            gameState.setHandCards(sortAndConvertHand(player.getHand()));
        }

        return GameMessageResult.success(GameMessageTypeEnum.START_GAME.getType(), gameState);
    }

    // ==================== 游戏进行 ====================

    private GameMessageResult handleRobLandlord(String json, Long userId) {
        GameRoom room = getUserRoom(userId);
        if (room == null) {
            return GameMessageResult.error(GameMessageTypeEnum.ROB_LANDLORD.getType(), "你不在任何房间中");
        }

        if (!(room instanceof LandlordsRoom)) {
            return GameMessageResult.error(GameMessageTypeEnum.ROB_LANDLORD.getType(), "房间类型不支持");
        }

        LandlordsRoom landlordsRoom = (LandlordsRoom) room;

        if (landlordsRoom.getState() != GameRoom.RoomState.ROBBING) {
            return GameMessageResult.error(GameMessageTypeEnum.ROB_LANDLORD.getType(), "当前不在叫地主阶段");
        }

        if (!landlordsRoom.getCurrentRobPlayerId().equals(userId)) {
            return GameMessageResult.error(GameMessageTypeEnum.ROB_LANDLORD.getType(), "还没轮到你叫地主");
        }

        RobLandlordReq req = parseJson(json, RobLandlordReq.class);
        GameStateResp gameState = null;
        if (req != null) {
            gameState = gameService.robLandlord(landlordsRoom, userId, req.getAction());
        }

        return GameMessageResult.success(GameMessageTypeEnum.ROB_LANDLORD.getType(), gameState);
    }

    private GameMessageResult handlePlayCards(String json, Long userId) {
        GameRoom room = getUserRoom(userId);
        if (room == null) {
            return GameMessageResult.error(GameMessageTypeEnum.PLAY_CARDS.getType(), "你不在任何房间中");
        }

        if (!(room instanceof LandlordsRoom)) {
            return GameMessageResult.error(GameMessageTypeEnum.PLAY_CARDS.getType(), "房间类型不支持");
        }

        LandlordsRoom landlordsRoom = (LandlordsRoom) room;

        if (landlordsRoom.getState() != GameRoom.RoomState.PLAYING) {
            return GameMessageResult.error(GameMessageTypeEnum.PLAY_CARDS.getType(), "当前不在出牌阶段");
        }

        if (!landlordsRoom.getCurrentPlayerId().equals(userId)) {
            return GameMessageResult.error(GameMessageTypeEnum.PLAY_CARDS.getType(), "还没轮到你出牌");
        }

        PlayCardsReq req = parseJson(json, PlayCardsReq.class);
        if (req != null && (req.getPokers() == null || req.getPokers().isEmpty())) {
            return GameMessageResult.error(GameMessageTypeEnum.PLAY_CARDS.getType(), "请选择要出的牌");
        }

        GameStateResp gameState = null;
        if (req != null) {
            gameState = gameService.playCards(landlordsRoom, userId, req.getPokers());
        }

        return GameMessageResult.success(GameMessageTypeEnum.PLAY_CARDS.getType(), gameState);
    }

    private GameMessageResult handlePass(String json, Long userId) {
        GameRoom room = getUserRoom(userId);
        if (room == null) {
            return GameMessageResult.error(GameMessageTypeEnum.PASS.getType(), "你不在任何房间中");
        }

        if (!(room instanceof LandlordsRoom)) {
            return GameMessageResult.error(GameMessageTypeEnum.PASS.getType(), "房间类型不支持");
        }

        LandlordsRoom landlordsRoom = (LandlordsRoom) room;

        if (landlordsRoom.getState() != GameRoom.RoomState.PLAYING) {
            return GameMessageResult.error(GameMessageTypeEnum.PASS.getType(), "当前不在出牌阶段");
        }

        if (!landlordsRoom.getCurrentPlayerId().equals(userId)) {
            return GameMessageResult.error(GameMessageTypeEnum.PASS.getType(), "还没轮到你出牌");
        }

        GameStateResp gameState = gameService.pass(landlordsRoom, userId);

        return GameMessageResult.success(GameMessageTypeEnum.PASS.getType(), gameState);
    }

    // ==================== AI托管 ====================

    private GameMessageResult handleCancelRobot(String json, Long userId) {
        GameRoom room = getUserRoom(userId);
        if (room == null) {
            return GameMessageResult.error(GameMessageTypeEnum.CANCEL_ROBOT.getType(), "你不在任何房间中");
        }

        if (!(room instanceof LandlordsRoom)) {
            return GameMessageResult.error(GameMessageTypeEnum.CANCEL_ROBOT.getType(), "房间类型不支持");
        }

        LandlordsRoom landlordsRoom = (LandlordsRoom) room;
        LandlordsPlayer player = landlordsRoom.getPlayer(userId) instanceof LandlordsPlayer
                ? (LandlordsPlayer) landlordsRoom.getPlayer(userId)
                : null;
        if (player == null) {
            return GameMessageResult.error(GameMessageTypeEnum.CANCEL_ROBOT.getType(), "不在此房间中");
        }

        if (!player.isRobotControlled()) {
            return GameMessageResult.error(GameMessageTypeEnum.CANCEL_ROBOT.getType(), "当前不在AI托管状态");
        }

        gameService.cancelRobotControl(landlordsRoom, userId);

        return GameMessageResult.success(GameMessageTypeEnum.CANCEL_ROBOT.getType(), null);
    }

    private GameMessageResult handleSetRobot(String json, Long userId) {
        GameRoom room = getUserRoom(userId);
        if (room == null) {
            return GameMessageResult.error(GameMessageTypeEnum.SET_ROBOT.getType(), "你不在任何房间中");
        }

        if (!(room instanceof LandlordsRoom)) {
            return GameMessageResult.error(GameMessageTypeEnum.SET_ROBOT.getType(), "房间类型不支持");
        }

        LandlordsRoom landlordsRoom = (LandlordsRoom) room;
        LandlordsPlayer player = landlordsRoom.getPlayer(userId) instanceof LandlordsPlayer
                ? (LandlordsPlayer) landlordsRoom.getPlayer(userId)
                : null;
        if (player == null) {
            return GameMessageResult.error(GameMessageTypeEnum.SET_ROBOT.getType(), "不在此房间中");
        }

        if (player.isRobotControlled()) {
            return GameMessageResult.error(GameMessageTypeEnum.SET_ROBOT.getType(), "已经在AI托管状态");
        }

        if (!landlordsRoom.getState().isPlaying()) {
            return GameMessageResult.error(GameMessageTypeEnum.SET_ROBOT.getType(), "游戏未开始，无法托管");
        }

        gameService.setRobotControl(landlordsRoom, userId, RobotReasonEnum.MANUAL);

        return GameMessageResult.success(GameMessageTypeEnum.SET_ROBOT.getType(), null);
    }

    // ==================== 其他 ====================

    private GameMessageResult handleChat(String json, Long userId) {
        GameRoom room = getUserRoom(userId);
        if (room == null) {
            return GameMessageResult.error(GameMessageTypeEnum.CHAT.getType(), "你不在任何房间中");
        }

        try {
            JSONObject jsonObj = JSONUtil.parseObj(json);
            String content = jsonObj.getStr("content");

            if (!StringUtils.hasText(content)) {
                return GameMessageResult.error(GameMessageTypeEnum.CHAT.getType(), "消息内容不能为空");
            }

            if (content.length() > 200) {
                return GameMessageResult.error(GameMessageTypeEnum.CHAT.getType(), "消息内容不能超过200字");
            }

            String userName = null;
            GamePlayer player = room.getPlayer(userId);
            if (player != null && StringUtils.hasText(player.getUserName())) {
                userName = player.getUserName();
            } else if (StringUtils.hasText(jsonObj.getStr("userName"))) {
                userName = jsonObj.getStr("userName");
            } else {
                userName = getUserInfo(userId).get("userName");
            }

            Map<String, Object> chatData = new HashMap<>();
            chatData.put("userId", userId);
            chatData.put("userName", userName);
            chatData.put("content", content);

            sessionManager.broadcastToRoom(room.getPlayerOrder(),
                    GameMessageTypeEnum.CHAT.getType(), chatData);

            return GameMessageResult.success(GameMessageTypeEnum.CHAT.getType(), null);
        } catch (Exception e) {
            log.error("处理聊天消息失败", e);
            return GameMessageResult.error(GameMessageTypeEnum.CHAT.getType(), "发送消息失败");
        }
    }

    // ==================== 辅助方法 ====================

    private <T> T parseJson(String json, Class<T> clazz) {
        if (json == null || json.isEmpty()) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                return null;
            }
        }
        try {
            return JSONUtil.toBean(json, clazz);
        } catch (Exception e) {
            log.error("parseJson 失败, class={}, json={}", clazz.getName(), json, e);
            return null;
        }
    }

    private GameRoom getUserRoom(Long userId) {
        String roomId = roomManager.getUserRoomId(userId);
        return roomId != null ? roomManager.getRoom(roomId) : null;
    }

    private void broadcastPlayerStatusChange(GameRoom room, Long userId) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("status", PlayerStatusEnum.OFFLINE.name().toLowerCase());
        data.put("event", GameActionEnum.PLAYER_STATUS_CHANGE.getCode());

        sessionManager.broadcastToRoom(room.getPlayerOrder(),
                GameMessageTypeEnum.STATE_UPDATE.getType(), data);
    }

    private Map<String, String> getUserInfo(Long userId) {
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("userName", "未知玩家");
        userInfo.put("avatar", "");

        try {
            User user = userService.getById(userId);
            if (user != null) {
                userInfo.put("userName", user.getUserName());
                userInfo.put("avatar", user.getUserAvatar() != null ? user.getUserAvatar() : "");
            }
        } catch (Exception e) {
            log.warn("获取用户信息失败: userId={}", userId, e);
        }

        return userInfo;
    }

    /**
     * 构建统一的房间状态响应
     *
     * @param action 操作类型: CREATE, JOIN, PLAYER_JOIN, PLAYER_LEAVE, STATE_UPDATE
     * @param room   房间
     * @param userId 当前用户ID
     * @return RoomStateResp
     */
    private RoomStateResp buildRoomStateResp(String action, GameRoom room, Long userId) {
        RoomStateResp.RoomStateRespBuilder builder = RoomStateResp.builder()
                .action(action)
                .roomId(room.getRoomId())
                .gameType(room.getGameType())
                .playerId(userId)
                .players(room.toRoomInfoResp().getPlayers())
                .state(room.getState() != null ? room.getState().getCode() : null)
                .readyPhaseStartTime(room.getReadyPhaseStartTime() > 0 ? room.getReadyPhaseStartTime() : null)
                .success(true);

        // 斗地主专属字段
        if (room instanceof LandlordsRoom) {
            LandlordsRoom landlordsRoom = (LandlordsRoom) room;
            builder.landlordId(landlordsRoom.getLandlordId());

            // 如果玩家已有手牌，返回手牌信息（不只是房主）
            LandlordsPlayer currentPlayer = landlordsRoom.getPlayer(userId) instanceof LandlordsPlayer
                    ? (LandlordsPlayer) landlordsRoom.getPlayer(userId)
                    : null;
            if (currentPlayer != null && currentPlayer.getHand() != null && !currentPlayer.getHand().isEmpty()) {
                builder.handCards(currentPlayer.getHand().getAll().stream()
                        .map(Poker::getId)
                        .collect(Collectors.toList()));
            }

            // 如果地主已确定，返回底牌
            if (landlordsRoom.getLandlordId() != null && landlordsRoom.getBottomCards() != null) {
                builder.bottomCards(landlordsRoom.getBottomCards().getAll().stream()
                        .map(Poker::getId)
                        .collect(Collectors.toList()));
            }
        }

        return builder.build();
    }

    /**
     * 解析游戏类型
     * 支持字符串枚举名称和数字代码
     */
    private GameTypeEnum parseGameType(String gameTypeStr) {
        if (gameTypeStr == null || gameTypeStr.isEmpty()) {
            return GameTypeEnum.LANDLORDS_CLASSIC;
        }

        // 尝试作为枚举名称解析
        try {
            return GameTypeEnum.valueOf(gameTypeStr);
        } catch (IllegalArgumentException ignored) {
            // 不是枚举名称，尝试作为数字解析
        }

        // 尝试作为数字解析
        try {
            int code = Integer.parseInt(gameTypeStr);
            return GameTypeEnum.getByCode(code);
        } catch (NumberFormatException e) {
            log.warn("无法解析游戏类型: {}, 使用默认类型", gameTypeStr);
            return GameTypeEnum.LANDLORDS_CLASSIC;
        }
    }

    private List<GameStateResp.PokerCardVO> sortAndConvertHand(PokerHand hand) {
        if (hand == null || hand.isEmpty()) {
            return new ArrayList<>();
        }
        List<Poker> sortedList = new ArrayList<>(hand.getAll());
        sortedList.sort((a, b) -> {
            if (a.isUniversal() != b.isUniversal()) return a.isUniversal() ? 1 : -1;
            return b.getLandlordsSortValue() - a.getLandlordsSortValue();
        });
        return GameStateResp.PokerCardVO.fromList(sortedList);
    }

    private GamePhaseEnum toPhase(GameRoom.RoomState state) {
        if (state == null) return GamePhaseEnum.WAITING;
        switch (state) {
            case WAITING:
                return GamePhaseEnum.WAITING;
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
            default:
                return GamePhaseEnum.WAITING;
        }
    }
}
