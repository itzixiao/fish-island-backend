package com.cong.fishisland.game.task;

import com.cong.fishisland.game.manager.GameRoomManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 斗地主房间超时清理任务
 *
 * 解决的问题：
 *  - 等待中凑不齐人 → 房间无人进入导致房间卡着，用户被限制加入其他房间
 *
 * 处理策略（详见 GameRoomManager.cleanTimeoutRooms）：
 *  - 等待中房间（玩家数 < maxPlayers）空闲 > 5 分钟 → 解散并广播 roomClosed
 *  - 空房间空闲 > 5 分钟 → 直接删除（无需广播）
 *
 * 游戏中断线 / 全员离线不在本任务范围：单个玩家断线保留在房间等重连，
 * 全员离线时由 removeRoom 自然清理。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RoomTimeoutTask {

    private final GameRoomManager roomManager;

    /** 等待中/空房间超时：5 分钟 */
    private static final long WAITING_TIMEOUT_MS = 5 * 60 * 1000L;

    /**
     * 每 30 秒扫描一次
     */
    @Scheduled(fixedRate = 30_000L, initialDelay = 30_000L)
    public void cleanTimeoutRooms() {
        try {
            roomManager.cleanTimeoutRooms(WAITING_TIMEOUT_MS);
        } catch (Exception e) {
            log.warn("斗地主房间超时清理任务异常", e);
        }
    }
}