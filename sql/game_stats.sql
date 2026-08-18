-- =====================================================================
-- 通用游戏排行主表
-- 设计原则:一张表覆盖所有游戏,通用字段平铺,游戏专属字段存 JSON 扩展
-- 后续接入新游戏时:
--   1. 在 GameTypeEnum 新增枚举项
--   2. 在 GameExtraStatsFieldEnum 中声明该游戏需要扩展的字段
--   3. 在 GameStatsExtraDefinition 中定义字段类型 / 描述 / 排序权重
--   4. 即可使用,无需改表
-- =====================================================================

CREATE TABLE IF NOT EXISTS `game_stats` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `userId` BIGINT NOT NULL COMMENT '用户ID',
    `gameType` INT NOT NULL COMMENT '游戏类型(对应 GameTypeEnum.code: 1=斗地主...)',
    `totalGames` INT NOT NULL DEFAULT 0 COMMENT '总对局数',
    `winGames` INT NOT NULL DEFAULT 0 COMMENT '胜利局数',
    `loseGames` INT NOT NULL DEFAULT 0 COMMENT '失败局数',
    `drawGames` INT NOT NULL DEFAULT 0 COMMENT '平局局数(预留,多数游戏为0)',
    `totalScore` BIGINT NOT NULL DEFAULT 0 COMMENT '累计积分(可正可负)',
    `winRate` DECIMAL(5, 4) NOT NULL DEFAULT 0.0000 COMMENT '胜率 (0.0000 ~ 1.0000)',
    `extraStats` JSON DEFAULT NULL COMMENT '游戏专属扩展字段(JSON,见 GameExtraStatsFieldEnum)',
    `lastPlayTime` DATETIME DEFAULT NULL COMMENT '最后对局时间',
    `createTime` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除 (0=未删,1=已删)',
    UNIQUE KEY `uk_user_game` (`userId`, `gameType`, `isDelete`),
    INDEX `idx_game_type_win` (`gameType`, `winGames` DESC),
    INDEX `idx_game_type_score` (`gameType`, `totalScore` DESC),
    INDEX `idx_game_type_total` (`gameType`, `totalGames` DESC),
    INDEX `idx_user_id` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用游戏排行榜主表';

-- 单局明细记录表(用于追溯每一局结果,可与排行榜 JOIN 计算)
CREATE TABLE IF NOT EXISTS `game_stats_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    `userId` BIGINT NOT NULL COMMENT '用户ID',
    `gameType` INT NOT NULL COMMENT '游戏类型',
    `roomId` VARCHAR(64) NOT NULL COMMENT '房间ID',
    `isWin` TINYINT NOT NULL COMMENT '本局是否胜利 (1=胜,0=负,2=平)',
    `scoreDelta` INT NOT NULL DEFAULT 0 COMMENT '本局积分变动',
    `role` VARCHAR(20) DEFAULT NULL COMMENT '本局角色(如 landlord/farmer/black/white...)',
    `createTime` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX `idx_user_game` (`userId`, `gameType`),
    INDEX `idx_game_type_win` (`gameType`, `isWin`),
    INDEX `idx_create_time` (`createTime`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='游戏对局明细记录表';
