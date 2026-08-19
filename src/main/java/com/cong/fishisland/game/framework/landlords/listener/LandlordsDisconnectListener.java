package com.cong.fishisland.game.framework.landlords.listener;

import com.cong.fishisland.game.framework.landlords.ws.LandlordsGameMessageHandler;
import com.cong.fishisland.websocket.event.UserOfflineEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 游戏断开连接监听器
 * 监听用户 WebSocket 断开事件，清理游戏房间资源
 *
 * @author cong
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LandlordsDisconnectListener {

    private final LandlordsGameMessageHandler gameMessageHandler;

    @Async
    @EventListener(classes = UserOfflineEvent.class)
    public void onUserDisconnect(UserOfflineEvent event) {
        Long userId = event.getUser().getId();
        gameMessageHandler.onDisconnect(userId);
    }
}
