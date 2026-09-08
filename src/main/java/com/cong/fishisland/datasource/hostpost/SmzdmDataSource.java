package com.cong.fishisland.datasource.hostpost;

import com.alibaba.fastjson.JSON;
import com.cong.fishisland.common.ErrorCode;
import com.cong.fishisland.common.exception.BusinessException;
import com.cong.fishisland.model.entity.hot.HotPost;
import com.cong.fishisland.model.enums.CategoryTypeEnum;
import com.cong.fishisland.model.enums.UpdateIntervalEnum;
import com.cong.fishisland.model.vo.hot.HotPostDataVO;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 什么值得买数据源
 *
 * @author shing
 */
@Slf4j
@Component
public class SmzdmDataSource implements DataSource {
    @Override
    public HotPost getHotPost() {

        String smzdmUrl = "https://www.smzdm.com/top/";
        List<HotPostDataVO> dataList = new ArrayList<>();
        try {
            // 获取主页面内容
            Document document = Jsoup.connect(smzdmUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:66.0) Gecko/20100101 Firefox/66.0")
                    .get();
            // 解析热门商品列表
            Elements items = document.select(".feed-hot-card");

            for (Element item : items) {
                String position = item.attr("data-position");
                // 定位 a 标签
                Element link = item.select("a[target=_blank]").first();
                if (link == null) continue;
                // 从 a 标签中提取 title 和 url
                String title = link.select(".feed-hot-title").text();
                String url = link.attr("href");
                String onclickData = link.attr("onclick");

                // 直接判断是否属于 "好价品类榜"
                if (onclickData.contains("'floor':'好价品类榜'")) {
                    dataList.add(HotPostDataVO.builder()
                            .title(title)
                            .url(url)
                            .followerCount(Integer.parseInt(position))
                            .build());
                }
            }
            dataList.sort(Comparator.comparingInt(HotPostDataVO::getFollowerCount));

        } catch (IOException e) {
            log.error("获取数据失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "获取数据失败");
        }

        return HotPost.builder()
                .sort(CategoryTypeEnum.GOODS_SHARE.getSort())
                .name("什么值得买热榜")
                .category(CategoryTypeEnum.GOODS_SHARE.getValue())
                .updateInterval(UpdateIntervalEnum.TWO_HOUR.getValue())
                .iconUrl("https://www.smzdm.com/favicon.ico")
                .hostJson(JSON.toJSONString(dataList.subList(0, Math.min(dataList.size(), 20))))
                .typeName("什么值得买")
                .build();
    }
}
