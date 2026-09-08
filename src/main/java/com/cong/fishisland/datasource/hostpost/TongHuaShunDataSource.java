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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 同花顺用户关注股票热榜数据源。
 *
 * <p>Cookie 从 datasource_cookie 表读取，dataSourceKey 为 TongHuaShun。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TongHuaShunDataSource implements DataSource {

    private static final String HOT_LIST_URL =
            "https://dq.10jqka.com.cn/fuyao/hot_list_data/out/hot_list/v1/stock"
                    + "?stock_type=a&type=hour&list_type=normal";
    private static final String REFERER = "https://eq.10jqka.com.cn/";
    private static final String STOCK_URL_PREFIX = "https://stockpage.10jqka.com.cn/";
    private static final int TOP_N = 20;
    private static final int TIMEOUT = 20_000;

    private final DataSourceCookieService dataSourceCookieService;

    @Override
    public HotPost getHotPost() {
        String cookie = dataSourceCookieService.getEnabledCookie(HotDataKeyEnum.TONG_HUA_SHUN.getValue());
        if (!StringUtils.hasText(cookie)) {
            log.warn("同花顺热榜 Cookie 为空，请在 datasource_cookie 表配置 dataSourceKey=TongHuaShun");
            return buildHotPost(Collections.emptyList());
        }

        try (HttpResponse response = HttpRequest.get(HOT_LIST_URL)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Cookie", cookie)
                .header("Origin", "https://eq.10jqka.com.cn")
                .header("Referer", REFERER)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36")
                .timeout(TIMEOUT)
                .execute()) {
            if (!response.isOk()) {
                log.warn("同花顺热榜返回异常状态码: {}", response.getStatus());
                return buildHotPost(Collections.emptyList());
            }
            return buildHotPost(parseHotList(response.body()));
        } catch (Exception e) {
            log.error("获取同花顺热榜失败", e);
            return buildHotPost(Collections.emptyList());
        }
    }

    List<HotPostDataVO> parseHotList(String result) {
        JSONObject root = JSON.parseObject(result);
        JSONArray stockList = root == null || root.getJSONObject("data") == null
                ? null : root.getJSONObject("data").getJSONArray("stock_list");
        if (stockList == null || stockList.isEmpty()) {
            return Collections.emptyList();
        }

        List<HotPostDataVO> dataList = new ArrayList<>(Math.min(stockList.size(), TOP_N));
        for (int i = 0; i < stockList.size() && dataList.size() < TOP_N; i++) {
            JSONObject item = stockList.getJSONObject(i);
            if (item == null || !StringUtils.hasText(item.getString("name"))) {
                continue;
            }
            String code = item.getString("code");
            dataList.add(HotPostDataVO.builder()
                    .title(item.getString("name"))
                    .url(buildStockUrl(code))
                    .followerCount(parseRate(item.getString("rate")))
                    .market(item.getInteger("market"))
                    .code(code)
                    .rate(item.getString("rate"))
                    .riseAndFall(item.getBigDecimal("rise_and_fall"))
                    .analyse(item.getString("analyse"))
                    .hotRankChg(item.getInteger("hot_rank_chg"))
                    .topic(item.get("topic"))
                    .tag(parseTag(item.getJSONObject("tag")))
                    .order(item.getInteger("order"))
                    .analyseTitle(item.getString("analyse_title"))
                    .build());
        }
        return dataList;
    }

    private HotPostDataVO.StockTagVO parseTag(JSONObject tag) {
        if (tag == null) {
            return null;
        }
        HotPostDataVO.StockTagVO result = new HotPostDataVO.StockTagVO();
        result.setConceptTag(tag.getJSONArray("concept_tag") == null
                ? Collections.emptyList()
                : tag.getJSONArray("concept_tag").toJavaList(String.class));
        result.setPopularityTag(tag.getString("popularity_tag"));
        return result;
    }

    private int parseRate(String rate) {
        try {
            return new BigDecimal(rate).intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private String buildStockUrl(String code) {
        return StringUtils.hasText(code) ? STOCK_URL_PREFIX + code + "/" : REFERER;
    }

    private HotPost buildHotPost(List<HotPostDataVO> dataList) {
        return HotPost.builder()
                .sort(CategoryTypeEnum.GENERAL_DISCUSSION.getSort() + 2)
                .category(CategoryTypeEnum.GENERAL_DISCUSSION.getValue())
                .name("同花顺热榜")
                .updateInterval(UpdateIntervalEnum.ONE_HOUR.getValue())
                .iconUrl("https://ts1.tc.mm.bing.net/th/id/OIP-C.02qOoK0N2El13xuAHzDcxQAAAA?w=193&h=193&c=8&rs=1&qlt=90&o=6&dpr=1.3&pid=ImgAns&rm=2")
                .hostJson(JSON.toJSONString(dataList))
                .typeName("同花顺")
                .build();
    }
}
