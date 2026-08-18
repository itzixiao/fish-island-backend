package com.cong.fishisland.controller.game;

import cn.dev33.satoken.stp.StpUtil;
import com.cong.fishisland.common.BaseResponse;
import com.cong.fishisland.common.ResultUtils;
import com.cong.fishisland.game.common.manager.GameRoomManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 游戏房间 HTTP 接口
 * 用于处理页面卸载时的离开房间请求（sendBeacon 等场景）
 *
 * @author cong
 */
@RestController
@RequestMapping("/game/room")
@RequiredArgsConstructor
@Slf4j
public class GameRoomController {

    private final GameRoomManager roomManager;

    /**
     * 页面卸载时离开房间
     * 通过 sendBeacon 调用，确保页面关闭时也能发送请求
     */
    @PostMapping("/leave")
    public BaseResponse<Boolean> leaveRoomOnUnload(HttpServletRequest request) {
        try {
            // 获取当前登录用户
            if (!StpUtil.isLogin()) {
                return ResultUtils.success(true);
            }
            Long userId = StpUtil.getLoginIdAsLong();

            // 获取用户所在的房间
            String roomId = roomManager.getUserRoomId(userId);
            if (roomId != null) {
                log.info("页面卸载，用户离开房间: userId={}, roomId={}", userId, roomId);
                roomManager.leaveRoom(roomId, userId);
            }

            return ResultUtils.success(true);
        } catch (Exception e) {
            log.error("页面卸载离开房间失败", e);
            return ResultUtils.success(false);
        }
    }

    /**
     * 页面卸载时离开房间（支持 sendBeacon 的 POST 请求）
     * sendBeacon 发送的是 JSON Blob，需要手动解析
     */
    @PostMapping(value = "/leave-beacon", consumes = "application/json")
    public BaseResponse<Boolean> leaveRoomOnUnloadBeacon(HttpServletRequest request) {
        try {
            // 获取当前登录用户
            if (!StpUtil.isLogin()) {
                return ResultUtils.success(true);
            }
            Long userId = StpUtil.getLoginIdAsLong();

            // 获取用户所在的房间
            String roomId = roomManager.getUserRoomId(userId);
            if (roomId != null) {
                log.info("页面卸载(sendBeacon)，用户离开房间: userId={}, roomId={}", userId, roomId);
                roomManager.leaveRoom(roomId, userId);
            }

            return ResultUtils.success(true);
        } catch (Exception e) {
            log.error("页面卸载离开房间失败(sendBeacon)", e);
            return ResultUtils.success(false);
        }
    }

    /**
     * 获取当前用户所在的房间ID
     */

    @GetMapping("/current")
    public BaseResponse<String> getCurrentRoomId() {
        try {
            if (!StpUtil.isLogin()) {
                return ResultUtils.success(null);
            }
            Long userId = StpUtil.getLoginIdAsLong();
            String roomId = roomManager.getUserRoomId(userId);
            return ResultUtils.success(roomId);
        } catch (Exception e) {
            log.error("获取当前房间ID失败", e);
            return ResultUtils.success(null);
        }
    }
}
