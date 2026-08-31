package com.cong.fishisland.datasource.hostpost;

import com.cong.fishisland.model.vo.hot.HotPostDataVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TongHuaShunDataSourceTest {

    @Test
    void parseHotListMapsStockFields() {
        String response = "{"
                + "\"status_code\":0,"
                + "\"data\":{\"stock_list\":[{"
                + "\"market\":33,\"code\":\"000560\",\"rate\":\"1466368.0\","
                + "\"rise_and_fall\":9.8485,\"name\":\"我爱我家\","
                + "\"analyse\":\"公司上半年归母净利润同比增长110.4%\","
                + "\"hot_rank_chg\":0,\"topic\":null,"
                + "\"tag\":{\"concept_tag\":[\"租售同权\",\"物业管理\"],\"popularity_tag\":\"2天2板\"},"
                + "\"order\":1,\"analyse_title\":\"净利增长+房产经纪+AI应用\""
                + "}]}}";

        TongHuaShunDataSource dataSource = new TongHuaShunDataSource(null);
        List<HotPostDataVO> result = dataSource.parseHotList(response);

        assertEquals(1, result.size());
        HotPostDataVO stock = result.get(0);
        assertEquals("我爱我家", stock.getTitle());
        assertEquals("https://stockpage.10jqka.com.cn/000560/", stock.getUrl());
        assertEquals(1466368, stock.getFollowerCount());
        assertEquals("000560", stock.getCode());
        assertEquals("1466368.0", stock.getRate());
        assertEquals("9.8485", stock.getRiseAndFall().toPlainString());
        assertEquals("净利增长+房产经纪+AI应用", stock.getAnalyseTitle());
        assertNotNull(stock.getTag());
        assertEquals("物业管理", stock.getTag().getConceptTag().get(1));
        assertEquals("2天2板", stock.getTag().getPopularityTag());
    }
}
