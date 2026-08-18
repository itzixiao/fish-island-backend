package com.cong.fishisland.game.common.manager;

import com.cong.fishisland.game.common.cache.GameRoomRedisCache;
import com.cong.fishisland.game.common.cache.GameSessionRedisCache;
import com.cong.fishisland.game.common.enums.GameTypeEnum;
import com.cong.fishisland.game.common.enums.PlayerRoleEnum;
import com.cong.fishisland.game.common.model.player.GamePlayer;
import com.cong.fishisland.game.common.model.room.GameRoom;
import com.cong.fishisland.game.common.model.session.GameSession;
import com.cong.fishisland.game.framework.landlords.model.LandlordsPlayer;
import com.cong.fishisland.game.framework.landlords.model.LandlordsRoom;
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

        // 根据游戏类型创建对应的房间
        GameRoom room = createRoomByType(roomId, gameType, ownerId, ownerName, ownerAvatar);

        // 保存到 Redis 和内存缓存
        saveRoom(room);
        roomCache.putUserRoom(ownerId, roomId);

        // 缓存会话
        saveSession(ownerId, roomId, ownerName, ownerAvatar);

        log.info("创建游戏房间: roomId={}, gameType={}", roomId, gameType);

        // 广播房间新增消息给所有在线用户
        broadcastRoomAdded(room);

        return room;
    }

    /**
     * 根据游戏类型创建房间
     */
    private GameRoom createRoomByType(String roomId, GameTypeEnum gameType, Long ownerId, String ownerName, String ownerAvatar) {
        switch (gameType) {
            case LANDLORDS_CLASSIC:
            case LANDLORDS_LAIZI:
            case LANDLORDS_SKILL:
                return createLandlordsRoom(roomId, gameType, ownerId, ownerName, ownerAvatar);
            default:
                // 默认创建基础房间（未来可扩展其他游戏）
                GameRoom room = new GameRoom(roomId, gameType, ownerId);
                addOwnerToRoom(room, ownerId, ownerName, ownerAvatar);
                return room;
        }
    }

    /**
     * 创建斗地主房间
     */
    private LandlordsRoom createLandlordsRoom(
            String roomId, GameTypeEnum gameType, Long ownerId, String ownerName, String ownerAvatar) {
        LandlordsRoom room =
                new LandlordsRoom(roomId, gameType, ownerId);
        addOwnerToRoom(room, ownerId, ownerName, ownerAvatar);
        return room;
    }

    /**
     * 添加房主到房间
     */
    private void addOwnerToRoom(GameRoom room, Long ownerId, String ownerName, String ownerAvatar) {
        LandlordsPlayer owner =
                new LandlordsPlayer(ownerId, ownerName, ownerAvatar);
        owner.setRole(PlayerRoleEnum.OWNER);
        owner.setOnline(true);
        room.addPlayer(owner);
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

            // 检查房间状态（由具体游戏服务决定，这里只做基础检查）
            if (room.getState() == GameRoom.RoomState.CLOSED) {
                log.warn("房间已关闭: roomId={}", roomId);
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
            // 新玩家加入 - 根据房间类型创建对应玩家
            player = createPlayerByType(room, userId, userName, userAvatar);
            player.setOnline(true);

            if (!room.addPlayer(player)) {
                return null;
            }

            // 新玩家加入后满员则进入准备阶段
            if (room.getPlayerCount() >= room.getMaxPlayers()) {
                room.enterReadyPhase(System.currentTimeMillis());
            }
        } else {
            // 重连：恢复玩家在线状态
            player.setOnline(true);
            player.setUserName(userName);
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
        if (room.getState() == GameRoom.RoomState.PLAYING) {
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
                .filter(r -> r.getState() == GameRoom.RoomState.WAITING || r.getState() == GameRoom.RoomState.READY)
                .filter(r -> !r.isNeedPassword())
                .sorted(Comparator.comparing(GameRoom::getCreateTime).reversed())
                .collect(Collectors.toList());
    }

    /**
     * 按游戏类型获取房间列表
     */
    public List<GameRoom> getRoomsByType(GameTypeEnum gameType) {
        return roomCache.getRoomsByType(gameType).stream()
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
     * 清理超时房间
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

            // 规则 A：房间没人 + 超时
            if (room.getPlayerCount() == 0 && now - room.getCreateTime() > roomTimeoutMs) {
                log.info("房间[{}]无玩家超时，自动删除", roomId);
                removeRoom(roomId);
                continue;
            }

            // 规则 B：等待中房间 createTime 超时
            if ((room.getState() == GameRoom.RoomState.WAITING || room.getState() == GameRoom.RoomState.READY)
                    && now - room.getCreateTime() > roomTimeoutMs) {
                log.info("房间[{}]超时未开始游戏，自动解散", roomId);
                forceCloseRoom(roomId, "房间超时未开始游戏，已自动解散");
                continue;
            }
        }
    }

    /**
     * 强制解散房间：先广播 roomClosed 给原成员 → 再删除房间
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
                    "gameRoomClosed", data);
        }

        // 删除房间
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
     * 根据房间类型创建玩家
     */
    private GamePlayer createPlayerByType(GameRoom room, Long userId, String userName, String userAvatar) {
        if (room instanceof LandlordsRoom) {
            return new LandlordsPlayer(userId, userName, userAvatar);
        }
        return new GamePlayer(userId, userName, userAvatar);
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
            sessionManager.broadcastToAll("gameRoomAdded", data);
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
            sessionManager.broadcastToAll("gameRoomRemoved", data);
        }
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

        String tempRoomId = session.getTempLeaveRoomId();
        GameRoom room = getRoom(tempRoomId);

        if (room == null) {
            session.clearTempLeave();
            sessionCache.saveSession(session);
            return false;
        }

        return room.getState().isPlaying();
    }

    /**
     * 获取用户当前房间限制信息
     */
    public GameRoomRestrictionInfo getRoomRestrictionInfo(Long userId) {
        GameSession session = sessionCache.getSession(userId);
        if (session == null || !session.hasTempLeave()) {
            return null;
        }

        String tempRoomId = session.getTempLeaveRoomId();
        GameRoom room = getRoom(tempRoomId);

        if (room == null) {
            session.clearTempLeave();
            sessionCache.saveSession(session);
            return null;
        }

        return GameRoomRestrictionInfo.builder()
                .roomId(tempRoomId)
                .gameType(room.getGameType())
                .state(room.getState())
                .reason(room.getState().isPlaying() ? "游戏进行中" : "等待中")
                .build();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class GameRoomRestrictionInfo {
        private String roomId;
        private GameTypeEnum gameType;
        private GameRoom.RoomState state;
        private String reason;
    }
}
