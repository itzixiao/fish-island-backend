package com.cong.fishisland.game.manager;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.cong.fishisland.game.cache.GameRoomRedisCache;
import com.cong.fishisland.game.cache.GameSessionRedisCache;
import com.cong.fishisland.game.constant.GameConstants;
import com.cong.fishisland.game.enums.GameActionEnum;
import com.cong.fishisland.game.enums.GameMessageTypeEnum;
import com.cong.fishisland.game.enums.GameTypeEnum;
import com.cong.fishisland.game.enums.RoomStateEnum;
import com.cong.fishisland.game.model.GameSession;
import com.cong.fishisland.game.model.player.GamePlayer;
import com.cong.fishisland.game.model.room.GameRoom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 游戏房间管理器
 * 数据存储改用 Redis，支持服务重启恢复
 *
 * @author cong
 */
@Slf4j
@Component
public class GameRoomManager {

    @Resource
    @Lazy
    private GameSessionManager sessionManager;

    @Resource
    private GameRoomRedisCache roomCache;

    @Resource
    private GameSessionRedisCache sessionCache;

    /**
     * 房间内存缓存（用于减少 Redis 访问，提升性能）
     */
    private final Map<String, GameRoom> roomMemoryCache = new java.util.concurrent.ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            // 恢复房间数据到内存缓存
            List<GameRoom> rooms = roomCache.getAllRooms();
            for (GameRoom room : rooms) {
                roomMemoryCache.put(room.getRoomId(), room);
            }
        } catch (Exception e) {
            log.error("从 Redis 恢复游戏数据失败", e);
        }
    }

    // ==================== 房间管理 ====================

    /**
     * 创建房间
     */
    public GameRoom createRoom(GameTypeEnum gameType, Long ownerId, String ownerName, String ownerAvatar) {
        String roomId = generateRoomId();

        GameRoom room = new GameRoom(roomId, gameType, ownerId);

        // 添加房主
        GamePlayer owner = new GamePlayer(ownerId, ownerName, ownerAvatar);
        owner.setRole(com.cong.fishisland.game.enums.PlayerRoleEnum.OWNER);
        owner.setOnline(true);
        room.addPlayer(owner);

        // 保存到 Redis 和内存缓存
        saveRoom(room);
        roomCache.putUserRoom(ownerId, roomId);

        // 缓存会话
        saveSession(ownerId, roomId, ownerName, ownerAvatar);

        log.info("创建游戏房间: roomId={}", roomId);

        // 广播房间新增消息给所有在线用户
        broadcastRoomAdded(room);

        return room;
    }

    /**
     * 加入房间（默认非创建者加入）
     */
    public GameRoom joinRoom(String roomId, Long userId, String userName, String userAvatar, String password) {
        return joinRoom(roomId, userId, userName, userAvatar, password, false);
    }

    /**
     * 加入房间
     * 支持断线重连
     *
     * @param isCreatorJoin 是否是创建房间后首次加入（用于区分重连）
     */
    public GameRoom joinRoom(String roomId, Long userId, String userName, String userAvatar, String password, boolean isCreatorJoin) {
        GameRoom room = getRoom(roomId);
        if (room == null) {
            log.warn("房间不存在: roomId={}", roomId);
            return null;
        }

        GameSession cachedSession = getUserSession(userId);
        boolean isExistingPlayer = room.getPlayer(userId) != null;

        // 判断是否为真正的重连：玩家已在房间中但当前离线
        // 注意：创建房间后加入不视为重连，即使会话中记录了房间
        boolean isReconnecting = isExistingPlayer
                && cachedSession != null
                && !cachedSession.isOnline();

        // 只有真正的重连才校验重连窗口（创建后加入不校验）
        if (isReconnecting && !cachedSession.isWithinReconnectWindow()) {
            log.warn("重连窗口已过期: userId={}, roomId={}", userId, roomId);
            sessionCache.deleteSession(userId);
            isReconnecting = false;
        }

        // 非重连用户检查
        if (!isReconnecting) {
            // 检查是否已在其他房间
            String existingRoomId = roomCache.getUserRoomId(userId);
            if (existingRoomId != null) {
                if (existingRoomId.equals(roomId)) {
                    return room;
                }
                return null;
            }

            // 检查房间状态
            if (room.getState() == RoomStateEnum.CLOSED) {
                log.warn("房间已关闭: roomId={}", roomId);
                return null;
            }

            // 非等待状态不能加入（新玩家）
            if (room.getState() != RoomStateEnum.WAITING && room.getState() != RoomStateEnum.READY) {
                log.warn("房间不在等待状态，无法加入: roomId={}, state={}", roomId, room.getState());
                return null;
            }

            if (room.getPlayerCount() >= room.getMaxPlayers()) {
                log.warn("房间已满: roomId={}, maxPlayers={}", roomId, room.getMaxPlayers());
                return null;
            }

            // 密码检查
            if (room.isNeedPassword() && !room.verifyPassword(password)) {
                log.warn("房间密码错误: roomId={}", roomId);
                return null;
            }
        }

        // 获取或创建玩家
        GamePlayer player = room.getPlayer(userId);

        if (player == null) {
            // 新玩家加入
            player = new GamePlayer(userId, userName, userAvatar);
            player.setOnline(true);

            // 重连时检查是否在游戏中（玩家不存在说明之前已完全离开）
            if (room.getState().isPlaying()) {
                log.warn("游戏进行中，新玩家无法加入: roomId={}, userId={}", roomId, userId);
                return null;
            }

            if (!room.addPlayer(player)) {
                return null;
            }

            // 新玩家加入后，检查房间是否已满，满则启动准备超时
            if (room.getPlayerCount() >= room.getMaxPlayers()) {
                room.enterReadyPhase(System.currentTimeMillis());
            }
        } else {
            // 重连：恢复玩家在线状态
            player.setOnline(true);
            player.setUserName(userName); // 更新用户名
            if (userAvatar != null) {
                player.setAvatar(userAvatar);
            }
        }

        // 更新映射
        roomCache.putUserRoom(userId, roomId);
        saveRoom(room);

        // 更新会话缓存
        saveSession(userId, roomId, userName, userAvatar);

        return room;
    }

    /**
     * 离开房间
     */
    public boolean leaveRoom(String roomId, Long userId) {
        GameRoom room = getRoom(roomId);
        if (room == null) {
            return false;
        }

        GamePlayer player = room.getPlayer(userId);
        if (player == null) {
            return false;
        }

        // 如果是游戏中，设置为离线（而不是移除）
        if (room.getState().isPlaying()) {
            player.setOnline(false);
            saveRoom(room);

            // 保存会话用于重连
            GameSession session = getUserSession(userId);
            if (session != null) {
                session.markOffline();
                sessionCache.saveSession(session);
            } else {
                saveSession(userId, roomId, player.getUserName(), player.getAvatar());
                GameSession newSession = getUserSession(userId);
                if (newSession != null) {
                    newSession.markOffline();
                    sessionCache.saveSession(newSession);
                }
            }

            // 检查是否所有玩家都离线了，如果是则删除房间
            if (room.getOnlinePlayerCount() == 0) {
                removeRoom(roomId);
            }
            return true;
        }

        // 非游戏中，真正离开
        if (room.removePlayer(userId)) {
            roomCache.removeUserRoom(userId);
            sessionCache.deleteSession(userId);
            saveRoom(room);

            // 如果房间空了，删除
            if (room.getPlayerCount() == 0) {
                removeRoom(roomId);
            }

            return true;
        }

        return false;
    }

    /**
     * 强制移除玩家（用于超时处理）
     */
    public boolean kickPlayer(String roomId, Long userId) {
        GameRoom room = getRoom(roomId);
        if (room == null) {
            return false;
        }

        GamePlayer player = room.getPlayer(userId);
        if (player == null) {
            return false;
        }

        roomCache.removeUserRoom(userId);
        sessionCache.deleteSession(userId);

        if (room.removePlayer(userId)) {
            saveRoom(room);

            // 如果房间空了，删除
            if (room.getPlayerCount() == 0) {
                removeRoom(roomId);
            }

            return true;
        }

        return false;
    }

    // ==================== 查询方法 ====================

    /**
     * 获取房间（先从内存缓存，未命中从 Redis 读取）
     */
    public GameRoom getRoom(String roomId) {
        if (roomId == null) {
            return null;
        }
        // 先从内存缓存读取
        GameRoom room = roomMemoryCache.get(roomId);
        if (room != null) {
            return room;
        }
        // 从 Redis 读取
        room = roomCache.getRoom(roomId);
        if (room != null) {
            roomMemoryCache.put(roomId, room);
        }
        return room;
    }

    /**
     * 获取用户所在房间
     */
    public GameRoom getUserRoom(Long userId) {
        String roomId = roomCache.getUserRoomId(userId);
        return roomId != null ? getRoom(roomId) : null;
    }

    /**
     * 获取用户所在房间ID
     */
    public String getUserRoomId(Long userId) {
        return roomCache.getUserRoomId(userId);
    }

    /**
     * 获取用户会话
     */
    public GameSession getUserSession(Long userId) {
        return sessionCache.getSession(userId);
    }

    /**
     * 获取所有等待中的房间
     */
    public List<GameRoom> getWaitingRooms() {
        return roomCache.getAllRooms().stream()
                .filter(r -> r.getState() == RoomStateEnum.WAITING || r.getState() == RoomStateEnum.READY)
                .filter(r -> !r.isNeedPassword())
                .sorted(Comparator.comparing(GameRoom::getCreateTime).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 按游戏类型获取房间列表
     */
    public List<GameRoom> getRoomsByType(GameTypeEnum gameType) {
        return roomCache.getRoomsByType(gameType).stream()
                .filter(r -> r.getState() == RoomStateEnum.WAITING
                        || r.getState() == RoomStateEnum.READY
                        || r.getState() == RoomStateEnum.ROBBING)
                .sorted(Comparator.comparing(GameRoom::getCreateTime).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 获取房间列表
     */
    public List<GameRoom.RoomInfo> getRoomList(GameTypeEnum gameType) {
        List<GameRoom> roomList;
        if (gameType != null) {
            roomList = getRoomsByType(gameType);
        } else {
            roomList = getWaitingRooms();
        }

        return roomList.stream()
                .map(GameRoom::getInfo)
                .collect(Collectors.toList());
    }

    /**
     * 移除房间
     */
    public void removeRoom(String roomId) {
        GameRoom room = getRoom(roomId);
        if (room != null) {
            GameTypeEnum gameType = room.getGameType();

            // 清除所有玩家的房间映射和会话
            for (Long userId : room.getPlayerOrder()) {
                roomCache.removeUserRoom(userId);
                sessionCache.deleteSession(userId);
            }

            // 从 Redis 删除
            roomCache.deleteRoom(roomId);
            roomCache.removeRoomExpiry(roomId);

            // 清除内存缓存
            roomMemoryCache.remove(roomId);

            // 广播房间删除消息给所有在线用户
            broadcastRoomRemoved(roomId, gameType);
        }
    }

    /**
     * 清理超时房间（统一超时策略）
     *
     * 规则：
     * - 房间没人（玩家数 = 0）且 createTime 超时 → 删除
     * - 等待中房间（WAITING/READY）createTime 超时 → 解散并通知
     * - 游戏进行中的房间不清理（游戏时间可能很长）
     *
     * 注意：准备超时由前端处理，后端不处理单个玩家的准备超时
     */
    public void cleanTimeoutRooms(long roomTimeoutMs) {
        long now = System.currentTimeMillis();
        Set<String> roomIds = roomCache.getAllRoomIds();

        for (String roomId : roomIds) {
            GameRoom room = getRoom(roomId);
            if (room == null) {
                continue;
            }

            // 游戏进行中的房间不清理
            if (room.getState().isPlaying()) {
                continue;
            }

            // 规则 A：房间没人 + 超时（无广播，直接删即可）
            if (room.getPlayerCount() == 0 && now - room.getCreateTime() > roomTimeoutMs) {
                log.info("房间[{}]无玩家超时，自动删除", roomId);
                removeRoom(roomId);
                continue;
            }

            // 规则 B：等待中房间 createTime 超时 → 解散并广播 roomClosed
            boolean isWaiting = room.getState() == RoomStateEnum.WAITING || room.getState() == RoomStateEnum.READY;
            if (isWaiting && now - room.getCreateTime() > roomTimeoutMs) {
                log.info("房间[{}]超时未开始游戏，自动解散", roomId);
                forceCloseRoom(roomId, "房间超时未开始游戏，已自动解散");
                continue;
            }
        }
    }

    /**
     * 构造 WS 消息 JSON
     */
    private String buildWsMessage(String type, Object data) {
        Map<String, Object> wsBaseResp = new HashMap<>();
        wsBaseResp.put("type", type);
        wsBaseResp.put("data", data);
        return JSON.toJSONString(wsBaseResp, JSONWriter.Feature.WriteLongAsString);
    }

    /**
     * 强制解散房间：先广播 roomClosed 给原成员 → 再删除房间
     * 用于超时/异常场景下，让正在房间页面的玩家立刻跳走，避免卡在房间
     */
    public void forceCloseRoom(String roomId, String reason) {
        GameRoom room = getRoom(roomId);
        if (room == null) {
            return;
        }

        // 先广播给房间内所有玩家
        if (sessionManager != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("roomId", roomId);
            data.put("reason", reason);
            sessionManager.broadcastToRoom(room.getPlayerOrder(),
                    GameMessageTypeEnum.ROOM_CLOSED.getType(), data);
        }

        // 删除房间（removeRoom 内会清理所有 userRoomId 映射和 session）
        removeRoom(roomId);
    }

    /**
     * 同步房间状态到 Redis（游戏过程中调用）
     */
    public void saveRoom(GameRoom room) {
        if (room == null || room.getRoomId() == null) {
            return;
        }
        roomCache.saveRoom(room);
        roomMemoryCache.put(room.getRoomId(), room);
    }

    // ==================== 私有方法 ====================

    /**
     * 保存会话到 Redis
     */
    public void saveSession(GameSession session) {
        if (session != null) {
            sessionCache.saveSession(session);
        }
    }

    /**
     * 生成房间ID
     */
    private String generateRoomId() {
        Random random = new Random();
        String roomId;
        int attempts = 0;

        do {
            roomId = String.format("%06d", random.nextInt(1000000));
            attempts++;
        } while (roomMemoryCache.containsKey(roomId) && attempts < 10);

        return roomId;
    }

    /**
     * 保存会话
     */
    private void saveSession(Long userId, String roomId, String userName, String avatar) {
        GameSession session = getUserSession(userId);
        if (session == null) {
            session = new GameSession();
        }
        session.setUserId(userId);
        session.setRoomId(roomId);
        session.setUserName(userName);
        session.setAvatar(avatar);
        session.setOnline(true);
        session.setLastHeartbeat(System.currentTimeMillis());
        sessionCache.saveSession(session);
    }

    /**
     * 广播房间新增消息给所有在线用户
     */
    private void broadcastRoomAdded(GameRoom room) {
        if (sessionManager != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("roomId", room.getRoomId());
            data.put("gameType", room.getGameType());
            data.put("playerCount", room.getPlayerCount());
            data.put("roomInfo", room.toRoomInfoResp());
            sessionManager.broadcastToAll(GameMessageTypeEnum.ROOM_ADDED.getType(), data);
        }
    }

    /**
     * 广播房间删除消息给所有在线用户
     */
    private void broadcastRoomRemoved(String roomId, GameTypeEnum gameType) {
        if (sessionManager != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("roomId", roomId);
            data.put("gameType", gameType);
            sessionManager.broadcastToAll(GameMessageTypeEnum.ROOM_REMOVED.getType(), data);
        }
    }

    /**
     * 房间限制信息
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RoomRestrictionInfo {
        private String roomId;
        private GameTypeEnum gameType;
        private RoomStateEnum state;
        private String reason;
    }

    // ==================== 房间限制相关 ====================

    /**
     * 检查用户是否有房间限制
     */
    public boolean hasRoomRestriction(Long userId) {
        GameSession session = sessionCache.getSession(userId);
        if (session == null || !session.hasTempLeave()) {
            return false;
        }

        // 检查原房间是否还存在
        String tempRoomId = session.getTempLeaveRoomId();
        GameRoom room = getRoom(tempRoomId);

        // 房间不存在，限制自动解除
        if (room == null) {
            session.clearTempLeave();
            sessionCache.saveSession(session);
            return false;
        }

        // 房间存在且游戏在进行中，有限制
        return room.getState().isPlaying();
    }

    /**
     * 获取用户当前房间限制信息
     */
    public RoomRestrictionInfo getRoomRestrictionInfo(Long userId) {
        GameSession session = sessionCache.getSession(userId);
        if (session == null || !session.hasTempLeave()) {
            return null;
        }

        String tempRoomId = session.getTempLeaveRoomId();
        GameRoom room = getRoom(tempRoomId);

        // 房间不存在，限制解除
        if (room == null) {
            session.clearTempLeave();
            sessionCache.saveSession(session);
            return null;
        }

        // 返回限制信息
        return RoomRestrictionInfo.builder()
                .roomId(tempRoomId)
                .gameType(room.getGameType())
                .state(room.getState())
                .reason(room.getState().isPlaying() ? "游戏进行中" : "等待中")
                .build();
    }
}
