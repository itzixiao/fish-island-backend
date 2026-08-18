package com.cong.fishisland.game.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 玩家状态枚举
 * 用于房间内玩家状态管理和变更广播
 *
 * @author cong
 */
@Getter
@AllArgsConstructor
public enum PlayerStatusEnum {

    /**
     * 在线
     */
    ONLINE("online", "在线"),

    /**
     * 离线
     */
    OFFLINE("offline", "离线"),

    /**
     * 重连中
     */
    RECONNECTING("reconnecting", "重连中"),

    /**
     * AI托管开启
     */
    ROBOT_ENABLED("robot_enabled", "AI托管开启"),

    /**
     * AI托管关闭
     */
    ROBOT_DISABLED("robot_disabled", "AI托管关闭");

    private final String code;
    private final String description;

    public static PlayerStatusEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (PlayerStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
