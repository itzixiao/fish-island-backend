package com.cong.fishisland.datasource.hostpost;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cong.fishisland.model.entity.hot.HotPost;
import com.cong.fishisland.model.enums.CategoryTypeEnum;
import com.cong.fishisland.model.enums.HotDataKeyEnum;
import com.cong.fishisland.model.enums.UpdateIntervalEnum;
import com.cong.fishisland.model.vo.hot.HotPostDataVO;
import com.cong.fishisland.service.datasource.DataSourceCookieService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 财联社热点资讯热榜数据源。
 *
 * <p>Cookie 从 datasource_cookie 表读取，dataSourceKey 为 CaiLianShe。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaiLianSheDataSource implements DataSource {

    private static final String HOT_LIST_URL =
            "https://www.cls.cn/v2/article/hot/list"
                    + "?app=CailianpressWeb&os=web&sv=8.7.9&sign=b02d8f7bc4c45eeb3e86904203597da2";
    private static final String REFERER = "https://www.cls.cn/depth?id=1000";
    private static final String ARTICLE_URL_PREFIX = "https://www.cls.cn/detail/";
    private static final int TOP_N = 20;
    private static final int TIMEOUT = 20_000;

    private final DataSourceCookieService dataSourceCookieService;

    @Override
    public HotPost getHotPost() {
        String cookie = dataSourceCookieService.getEnabledCookie(HotDataKeyEnum.CAI_LIAN_SHE.getValue());
        if (!StringUtils.hasText(cookie)) {
            log.warn("财联社热榜 Cookie 为空，请在 datasource_cookie 表配置 dataSourceKey=CaiLianShe");
            return buildHotPost(Collections.emptyList());
        }

        try (HttpResponse response = HttpRequest.get(HOT_LIST_URL)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Content-Type", "application/json")
                .header("Cookie", cookie)
                .header("Referer", REFERER)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36")
                .timeout(TIMEOUT)
                .execute()) {
            if (!response.isOk()) {
                log.warn("财联社热榜返回异常状态码: {}", response.getStatus());
                return buildHotPost(Collections.emptyList());
            }
            return buildHotPost(parseHotList(response.body()));
        } catch (Exception e) {
            log.error("获取财联社热榜失败", e);
            return buildHotPost(Collections.emptyList());
        }
    }

    List<HotPostDataVO> parseHotList(String result) {
        JSONObject root = JSON.parseObject(result);
        JSONArray articles = root == null ? null : root.getJSONArray("data");
        if (articles == null || articles.isEmpty()) {
            return Collections.emptyList();
        }

        List<HotPostDataVO> dataList = new ArrayList<>(Math.min(articles.size(), TOP_N));
        for (int i = 0; i < articles.size() && dataList.size() < TOP_N; i++) {
            JSONObject article = articles.getJSONObject(i);
            if (article == null || !StringUtils.hasText(article.getString("title"))) {
                continue;
            }
            Long id = article.getLong("id");
            Integer readNum = article.getInteger("readNum");
            dataList.add(HotPostDataVO.builder()
                    .title(article.getString("title"))
                    .url(buildArticleUrl(id))
                    .followerCount(readNum == null ? 0 : readNum)
                    .articleId(id)
                    .brief(article.getString("brief"))
                    .image(article.getString("img"))
                    .ctime(article.getLong("ctime"))
                    .author(article.getString("author"))
                    .stocks(article.getString("stocks"))
                    .build());
        }
        return dataList;
    }

    private String buildArticleUrl(Long id) {
        return id == null ? "https://www.cls.cn/" : ARTICLE_URL_PREFIX + id;
    }

    private HotPost buildHotPost(List<HotPostDataVO> dataList) {
        return HotPost.builder()
                .sort(CategoryTypeEnum.GENERAL_DISCUSSION.getSort() + 3)
                .category(CategoryTypeEnum.GENERAL_DISCUSSION.getValue())
                .name("财联社热榜")
                .updateInterval(UpdateIntervalEnum.ONE_HOUR.getValue())
                .iconUrl("https://ts1.tc.mm.bing.net/th/id/OIP-C.8K2b9ds62FAMZFCe_9Dg7wAAAA?w=193&h=193&c=8&rs=1&qlt=90&o=6&dpr=1.3&pid=ImgAns&rm=2")
                .hostJson(JSON.toJSONString(dataList))
                .typeName("财联社")
                .build();
    }
}
