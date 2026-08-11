package com.cong.fishisland.game.task;

import com.cong.fishisland.game.constant.GameConstants;
import com.cong.fishisland.game.manager.GameRoomManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 房间超时清理任务（斗地主等房间通用）
 *
 * 解决的问题：
 *  - 等待中凑不齐人 → 房间卡着，用户被限制加入其他房间
 *  - 房间满员后长时间没人开始 → 3 人干等
 *  - 准备阶段超时未点准备 → 自动踢人（包括房主）
 *
 * 处理策略（详见 GameRoomManager.cleanTimeoutRooms）：
 *  - 等待中房间（玩家数 < maxPlayers）空闲 > WAITING_ROOM_TIMEOUT_MS → 解散并广播 roomClosed
 *  - 房间满员后空闲 > ROOM_FULL_NO_START_TIMEOUT_MS 且没人开始 → 解散并广播 roomClosed
 *  - 准备阶段超时未准备 → 踢人（广播 playerKicked / playerLeave）
 *  - 空房间空闲 > WAITING_ROOM_TIMEOUT_MS → 直接删除（无需广播）
 *
 * 游戏中断线 / 全员离线不在本任务范围：单个玩家断线保留在房间等重连，
 * 全员离线时由 removeRoom 自然清理。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RoomTimeoutTask {

    private final GameRoomManager roomManager;

    @Scheduled(fixedRate = 5_000L, initialDelay = 5_000L)
    public void cleanTimeoutRooms() {
        try {
            roomManager.cleanTimeoutRooms(GameConstants.WAITING_ROOM_TIMEOUT_MS);
        } catch (Exception e) {
            log.warn("房间超时清理任务异常", e);
        }
    }
}
