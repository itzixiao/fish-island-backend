package com.cong.fishisland.game.task;

import com.cong.fishisland.game.common.constant.GameConstants;
import com.cong.fishisland.game.common.manager.GameRoomManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 房间超时清理任务（斗地主等房间通用）
 *
 * 统一超时策略：房间创建后 10 分钟未开始游戏则自动解散
 * 不区分"人不够"和"满员没开始"，统一使用 ROOM_TIMEOUT_MS
 *
 * 注意：准备超时由前端处理，后端只负责房间级别的超时清理
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RoomTimeoutTask {

    private final GameRoomManager roomManager;

    @Scheduled(fixedRate = 5_000L, initialDelay = 5_000L)
    public void cleanTimeoutRooms() {
        try {
            roomManager.cleanTimeoutRooms(GameConstants.ROOM_TIMEOUT_MS);
        } catch (Exception e) {
            log.warn("房间超时清理任务异常", e);
        }
    }
}
