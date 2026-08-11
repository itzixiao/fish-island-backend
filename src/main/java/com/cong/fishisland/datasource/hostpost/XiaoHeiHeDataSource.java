package com.cong.fishisland.datasource.hostpost;

import cn.hutool.http.HttpRequest;
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
 * 小黑盒热榜数据源
 * <p>
 * 请求参数从 datasource_cookie 表（dataSourceKey=XiaoHeiHe）实时读取并拼接到 URL 后。
 * 解析 result.Lists 第一个列表的 items。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XiaoHeiHeDataSource implements DataSource {

    private static final String BASE_URL = "https://api.xiaoheihe.cn/bbs/app/api/search/welcome_page/v2";
    private static final String GAME_URL_PREFIX = "https://www.xiaoheihe.cn/app/topic/game/pc/";
    private static final String NEWS_URL_PREFIX = "https://www.xiaoheihe.cn/app/bbs/link/";
    private static final int TOP_N = 20;

    private final DataSourceCookieService dataSourceCookieService;

    @Override
    public HotPost getHotPost() {
        String queryParams = dataSourceCookieService.getEnabledCookie(HotDataKeyEnum.XIAO_HEI_HE.getValue());
        if (!StringUtils.hasText(queryParams)) {
            log.error("小黑盒热榜请求参数为空，请在 datasource_cookie 表配置 dataSourceKey=XiaoHeiHe 的参数");
            return buildHotPost(Collections.emptyList());
        }

        String url = buildRequestUrl(queryParams);
        try {
            String result = HttpRequest.get(url)
                    .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("referer", "https://www.xiaoheihe.cn/")
                    .header("accept", "application/json, text/plain, */*")
                    .header("accept-language", "zh-CN,zh;q=0.9")
                    .execute()
                    .body();

            List<HotPostDataVO> dataList = parseHotList(result);
            return buildHotPost(dataList);
        } catch (Exception e) {
            log.error("获取小黑盒热榜失败", e);
            return buildHotPost(Collections.emptyList());
        }
    }

    private String buildRequestUrl(String queryParams) {
        String params = queryParams.trim();
        if (params.startsWith("?")) {
            params = params.substring(1);
        }
        return BASE_URL + "?" + params;
    }

    /**
     * 解析接口响应，取 Lists 第一个列表的 items
     */
    private List<HotPostDataVO> parseHotList(String result) {
        JSONObject resultJson = JSON.parseObject(result);
        if (resultJson == null) {
            log.error("小黑盒热榜响应解析失败，result 为空");
            return Collections.emptyList();
        }

        JSONObject data = resultJson.getJSONObject("result");
        if (data == null) {
            log.error("小黑盒热榜响应缺少 result 字段: {}", resultJson.getString("msg"));
            return Collections.emptyList();
        }

        JSONArray lists = data.getJSONArray("Lists");
        if (lists == null || lists.isEmpty()) {
            log.error("小黑盒热榜 Lists 为空");
            return Collections.emptyList();
        }

        JSONObject firstList = lists.getJSONObject(0);
        JSONArray items = firstList.getJSONArray("items");
        if (items == null || items.isEmpty()) {
            log.error("小黑盒热榜第一组 items 为空");
            return Collections.emptyList();
        }

        int size = items.size();
        List<HotPostDataVO> dataList = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            JSONObject item = items.getJSONObject(i);
            if (item == null) {
                continue;
            }
            String title = item.getString("text");
            if (!StringUtils.hasText(title)) {
                continue;
            }
            JSONObject report = item.getJSONObject("report");
            dataList.add(HotPostDataVO.builder()
                    .title(title)
                    .url(buildItemUrl(report))
                    .followerCount(size - i)
                    .build());
        }
        return dataList;
    }

    private String buildItemUrl(JSONObject report) {
        if (report == null) {
            return "https://www.xiaoheihe.cn/";
        }
        Long id = report.getLong("id");
        String type = report.getString("type");
        if (id == null) {
            return "https://www.xiaoheihe.cn/";
        }
        if ("game".equals(type)) {
            return GAME_URL_PREFIX + id;
        }
        if ("news".equals(type)) {
            return NEWS_URL_PREFIX + id;
        }
        return "https://www.xiaoheihe.cn/";
    }

    private HotPost buildHotPost(List<HotPostDataVO> dataList) {
        List<HotPostDataVO> topList = dataList.subList(0, Math.min(dataList.size(), TOP_N));
        return HotPost.builder()
                // 排在知乎（sort=1）之后，仍归类为综合资讯
                .sort(CategoryTypeEnum.GENERAL_DISCUSSION.getValue() + 1)
                .category(CategoryTypeEnum.GENERAL_DISCUSSION.getValue())
                .name("小黑盒热榜")
                .updateInterval(UpdateIntervalEnum.HALF_HOUR.getValue())
                .iconUrl("https://www.xiaoheihe.cn/favicon.ico")
                .hostJson(JSON.toJSONString(topList))
                .typeName("小黑盒")
                .build();
    }
}
