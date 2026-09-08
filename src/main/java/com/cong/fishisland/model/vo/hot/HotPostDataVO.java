package com.cong.fishisland.model.vo.hot;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.io.Serializable;
import java.util.List;

/**
 * 热榜视图
 * # @author <a href="https://github.com/lhccong">程序员聪</a>
 */
@Data
@Builder
public class HotPostDataVO implements Serializable {

    /**
     * 标题
     */
    private String title;

    /**
     * 热度
     */
    private Integer followerCount;

    /**
     * 链接
     */
    private String url;

    /**
     * 股票市场标识（同花顺专用）
     */
    private Integer market;

    /**
     * 股票代码（同花顺专用）
     */
    private String code;

    /**
     * 同花顺热度值（接口返回字符串，保留原始精度）
     */
    private String rate;

    /**
     * 涨跌幅（百分比）
     */
    private BigDecimal riseAndFall;

    /**
     * AI 分析正文
     */
    private String analyse;

    /**
     * 热榜排名变化
     */
    private Integer hotRankChg;

    /**
     * 主题信息
     */
    private Object topic;

    /**
     * 股票标签
     */
    private StockTagVO tag;

    /**
     * 榜单排名
     */
    private Integer order;

    /**
     * AI 分析标题
     */
    private String analyseTitle;

    /**
     * 资讯文章 ID（财联社专用）
     */
    private Long articleId;

    /**
     * 资讯摘要（财联社专用）
     */
    private String brief;

    /**
     * 封面图片（财联社专用）
     */
    private String image;

    /**
     * 发布时间戳（秒）（财联社专用）
     */
    private Long ctime;

    /**
     * 作者（财联社专用）
     */
    private String author;

    /**
     * 关联股票信息（财联社专用）
     */
    private String stocks;

    @Data
    public static class StockTagVO implements Serializable {

        private List<String> conceptTag;

        private String popularityTag;
    }

}
