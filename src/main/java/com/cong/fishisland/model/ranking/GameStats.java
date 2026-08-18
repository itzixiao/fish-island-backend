package com.cong.fishisland.model.ranking;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 通用游戏排行榜主表实体
 *
 * @author cong
 */
@Data
@Builder
@TableName("game_stats")
public class GameStats implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Integer gameType;

    private Integer totalGames;

    private Integer winGames;

    private Integer loseGames;

    private Integer drawGames;

    private Long totalScore;

    private BigDecimal winRate;

    private String extraStats;

    private Date lastPlayTime;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
