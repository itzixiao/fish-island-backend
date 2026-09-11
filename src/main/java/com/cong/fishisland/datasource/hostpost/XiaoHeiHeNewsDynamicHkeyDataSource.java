package com.cong.fishisland.datasource.hostpost;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cong.fishisland.model.enums.CategoryTypeEnum;
import com.cong.fishisland.model.enums.HotDataKeyEnum;
import com.cong.fishisland.service.datasource.DataSourceCookieService;
import org.springframework.stereotype.Component;

/**
 * 小黑盒热议数据源。
 * <p>
 * 复用动态 hkey 请求逻辑，但仅解析 Lists 中 tab_type = news 的榜单。
 */
@Component
public class XiaoHeiHeNewsDynamicHkeyDataSource extends XiaoHeiHeDynamicHkeyDataSource {

    public XiaoHeiHeNewsDynamicHkeyDataSource(DataSourceCookieService dataSourceCookieService) {
        super(dataSourceCookieService);
    }

    @Override
    protected String getCookieDataSourceKey() {
        // 与原小黑盒热榜共用同一套基础参数配置。
        return HotDataKeyEnum.XIAO_HEI_HE.getValue();
    }

    @Override
    protected JSONObject selectTargetList(JSONArray lists) {
        for (int i = 0; i < lists.size(); i++) {
            JSONObject item = lists.getJSONObject(i);
            if (item != null && "news".equals(item.getString("tab_type"))) {
                return item;
            }
        }
        return null;
    }

    @Override
    protected Integer getSort() {
        return CategoryTypeEnum.GENERAL_DISCUSSION.getSort() + 2;
    }

    @Override
    protected String getDisplayName() {
        return "小黑盒热议";
    }

    @Override
    protected String getTypeName() {
        return "小黑盒热议";
    }
}
