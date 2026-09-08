package com.cong.fishisland.datasource.hostpost;


import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cong.fishisland.model.entity.hot.HotPost;
import com.cong.fishisland.model.enums.CategoryTypeEnum;
import com.cong.fishisland.model.enums.UpdateIntervalEnum;
import com.cong.fishisland.model.vo.hot.HotPostDataVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 哔哩哔哩数据源
 *
 * @author shing
 */
@Slf4j
@Component
public class BiliBiliDataSource implements DataSource {

    @Override
    public HotPost getHotPost() {
        String urlBilibili = "https://api.bilibili.com/x/web-interface/popular";
        String result = HttpRequest.get(urlBilibili).execute().body();
        JSONObject resultJson = JSON.parseObject(result);

        // 获取嵌套数据结构
        JSONObject data = resultJson.getJSONObject("data");
        JSONArray list = data.getJSONArray("list");

        List<HotPostDataVO> dataList = list.stream().map(item -> {
            JSONObject jsonItem = (JSONObject) item;

            // 提取基础字段
            String title = jsonItem.getString("title");
            String shortLink = jsonItem.getString("short_link_v2");

            // 提取嵌套的统计信息
            JSONObject stat = jsonItem.getJSONObject("stat");
            int view = stat.getIntValue("view");

            return HotPostDataVO.builder()
                    .title(title)
                    .url(shortLink)
                    .followerCount(view)
                    .build();
        }).collect(Collectors.toList());

        return HotPost.builder()
                .sort(CategoryTypeEnum.VIDEO_ENTERTAINMENT.getSort())
                .name("B站热门")
                .category(CategoryTypeEnum.VIDEO_ENTERTAINMENT.getValue())
                .updateInterval(UpdateIntervalEnum.HALF_HOUR.getValue())
                .iconUrl("https://www.bilibili.com/favicon.ico")
                //按 followerCount 降序排序
                .hostJson(JSON.toJSONString(dataList
                        .stream().sorted((a, b) -> b.getFollowerCount() - a.getFollowerCount()).collect(Collectors.toList())
                        .subList(0, Math.min(dataList.size(), 20))))
                .typeName("哔哩哔哩")
                .build();
    }

}
