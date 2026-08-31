package com.cong.fishisland.datasource.hostpost;

import com.cong.fishisland.model.vo.hot.HotPostDataVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaiLianSheDataSourceTest {

    @Test
    void parseHotListMapsArticleFields() {
        String response = "{"
                + "\"errno\":0,\"msg\":\"\","
                + "\"data\":[{"
                + "\"id\":2468988,"
                + "\"title\":\"房地产行业重磅政策密集推出\","
                + "\"brief\":\"个人住房贷款最长期限延长至40年\","
                + "\"img\":\"https://image.cls.cn/images/20260831/example.jpg\","
                + "\"ctime\":1788130800,\"readNum\":186334,"
                + "\"author\":\"财联社\",\"stocks\":\"\""
                + "}]}";

        CaiLianSheDataSource dataSource = new CaiLianSheDataSource(null);
        List<HotPostDataVO> result = dataSource.parseHotList(response);

        assertEquals(1, result.size());
        HotPostDataVO article = result.get(0);
        assertEquals("房地产行业重磅政策密集推出", article.getTitle());
        assertEquals("https://www.cls.cn/detail/2468988", article.getUrl());
        assertEquals(186334, article.getFollowerCount());
        assertEquals(2468988L, article.getArticleId());
        assertEquals("个人住房贷款最长期限延长至40年", article.getBrief());
        assertEquals("财联社", article.getAuthor());
    }
}
