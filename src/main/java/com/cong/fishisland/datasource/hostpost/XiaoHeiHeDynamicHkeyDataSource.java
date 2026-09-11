package com.cong.fishisland.datasource.hostpost;

import cn.hutool.crypto.SecureUtil;
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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 小黑盒热榜数据源（动态 hkey 版本）
 * <p>
 * 该类将前端 bundle 中的 hkey / nonce 生成逻辑翻译为 Java，
 * 并作为小黑盒系列数据源的公共基类复用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XiaoHeiHeDynamicHkeyDataSource implements DataSource {

    protected static final String BASE_URL = "https://api.xiaoheihe.cn/bbs/app/api/search/welcome_page/v2";
    protected static final String REQUEST_PATH = "/bbs/app/api/search/welcome_page/v2";
    protected static final String SIGN_ALPHABET = "AB45STUVWZEFGJ6CH01D237IXYPQRKLMN89";
    protected static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    protected static final String GAME_URL_PREFIX = "https://www.xiaoheihe.cn/app/topic/game/pc/";
    protected static final String NEWS_URL_PREFIX = "https://www.xiaoheihe.cn/app/bbs/link/";
    protected static final int TOP_N = 20;

    protected final DataSourceCookieService dataSourceCookieService;

    @Override
    public HotPost getHotPost() {
        try {
            String result = HttpRequest.get(buildRequestUrl())
                    .header("user-agent", USER_AGENT)
                    .header("referer", "https://www.xiaoheihe.cn/")
                    .header("accept", "application/json, text/plain, */*")
                    .header("accept-language", "zh-CN,zh;q=0.9")
                    .execute()
                    .body();

            List<HotPostDataVO> dataList = parseHotList(result);
            return buildHotPost(dataList);
        } catch (Exception e) {
            log.error("获取小黑盒热榜失败（动态 hkey 版本）", e);
            return buildHotPost(Collections.emptyList());
        }
    }

    protected String buildRequestUrl() {
        Map<String, String> queryParams = buildBaseQueryParams();
        long currentSecond = System.currentTimeMillis() / 1000;
        String nonce = createNonce(currentSecond);
        queryParams.put("version", "999.0.4");
        queryParams.put("hkey", generateHkey(REQUEST_PATH, currentSecond, nonce));
        queryParams.put("_time", String.valueOf(currentSecond));
        queryParams.put("nonce", nonce);
        return BASE_URL + "?" + toQueryString(queryParams);
    }

    /**
     * 优先复用 datasource_cookie 中保存的静态参数，动态字段由代码实时覆盖。
     */
    protected Map<String, String> buildBaseQueryParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("app", "heybox");
        params.put("os_type", "web");
        params.put("x_app", "heybox_website");
        params.put("x_client_type", "web");
        params.put("x_os_type", "Windows");
        params.put("x_client_version", "");
        params.put("client_type", "web");
        params.put("web_version", "3.0");

        String rawQuery = dataSourceCookieService.getEnabledCookie(getCookieDataSourceKey());
        if (!StringUtils.hasText(rawQuery)) {
            return params;
        }
        String normalized = rawQuery.trim();
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(queryIndex + 1);
        }
        for (String pair : normalized.split("&")) {
            if (!StringUtils.hasText(pair)) {
                continue;
            }
            int index = pair.indexOf('=');
            String key = index >= 0 ? pair.substring(0, index) : pair;
            String value = index >= 0 ? pair.substring(index + 1) : "";
            params.put(urlDecode(key), urlDecode(value));
        }
        return params;
    }

    /**
     * 默认复用原有小黑盒参数配置。
     */
    protected String getCookieDataSourceKey() {
        return HotDataKeyEnum.XIAO_HEI_HE.getValue();
    }

    protected String toQueryString(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(urlEncode(entry.getKey()))
                    .append("=")
                    .append(urlEncode(entry.getValue()));
        }
        return sb.toString();
    }

    protected String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 解码失败", e);
        }
    }

    protected String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 编码失败", e);
        }
    }

    /**
     * 对应前端 Pwe() 中的 nonce 生成逻辑。
     * 浏览器里会混入 WebGL / canvas 指纹，这里改用 JVM 可稳定获取的运行时信息作为种子。
     */
    protected String createNonce(long currentSecond) {
        long nowMillis = System.currentTimeMillis();
        String runtimeFingerprint = buildRuntimeFingerprint();
        String randomSeed = currentSecond
                + String.valueOf(nowMillis)
                + String.valueOf(ThreadLocalRandom.current().nextDouble())
                + runtimeFingerprint
                + runtimeFingerprint;
        return SecureUtil.md5(randomSeed).toUpperCase(Locale.ROOT);
    }

    protected String buildRuntimeFingerprint() {
        return USER_AGENT
                + System.getProperty("os.name", "")
                + System.getProperty("os.arch", "")
                + ZoneId.systemDefault().getId()
                + TimeZone.getDefault().getID()
                + Locale.getDefault();
    }

    /**
     * 对应前端 PM.g(path, time, nonce) -> Tr(path, time + 1, nonce)。
     */
    protected String generateHkey(String requestPath, long currentSecond, String nonce) {
        String normalizedPath = normalizeRequestPath(requestPath);
        String timePart = mapByTruncatedAlphabet(String.valueOf(currentSecond + 1), SIGN_ALPHABET, -2);
        String pathPart = mapByAlphabet(normalizedPath, SIGN_ALPHABET);
        String noncePart = mapByAlphabet(nonce, SIGN_ALPHABET);
        String mixed = interleaveStrings(timePart, pathPart, noncePart);
        String hash = SecureUtil.md5(mixed.substring(0, Math.min(20, mixed.length())));
        String prefix = mapByTruncatedAlphabet(hash.substring(0, 5), SIGN_ALPHABET, -4);
        int suffixValue = sumArray(mixTailChars(toAsciiArray(hash.substring(hash.length() - 6)))) % 100;
        return prefix + String.format("%02d", suffixValue);
    }

    protected String normalizeRequestPath(String path) {
        StringBuilder sb = new StringBuilder("/");
        String[] parts = path.split("/");
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                sb.append(part).append("/");
            }
        }
        return sb.toString();
    }

    protected String mapByTruncatedAlphabet(String value, String alphabet, int endIndex) {
        String usedAlphabet = alphabet.substring(0, alphabet.length() + endIndex);
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            int index = value.charAt(i) % usedAlphabet.length();
            sb.append(usedAlphabet.charAt(index));
        }
        return sb.toString();
    }

    protected String mapByAlphabet(String value, String alphabet) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            int index = value.charAt(i) % alphabet.length();
            sb.append(alphabet.charAt(index));
        }
        return sb.toString();
    }

    protected String interleaveStrings(String... values) {
        int maxLength = 0;
        for (String value : values) {
            maxLength = Math.max(maxLength, value.length());
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLength; i++) {
            for (String value : values) {
                if (i < value.length()) {
                    sb.append(value.charAt(i));
                }
            }
        }
        return sb.toString();
    }

    protected int[] toAsciiArray(String value) {
        int[] result = new int[value.length()];
        for (int i = 0; i < value.length(); i++) {
            result[i] = value.charAt(i);
        }
        return result;
    }

    /**
     * 对应前端 Iwe()，只会重写前 4 个元素，其余元素保留原值参与求和。
     */
    protected int[] mixTailChars(int[] values) {
        if (values.length < 4) {
            return values;
        }
        int[] mixed = values.clone();
        mixed[0] = ig(values[0]) ^ lh(values[1]) ^ sf(values[2]) ^ pc(values[3]);
        mixed[1] = pc(values[0]) ^ ig(values[1]) ^ lh(values[2]) ^ sf(values[3]);
        mixed[2] = sf(values[0]) ^ pc(values[1]) ^ ig(values[2]) ^ lh(values[3]);
        mixed[3] = lh(values[0]) ^ sf(values[1]) ^ pc(values[2]) ^ ig(values[3]);
        return mixed;
    }

    protected int sumArray(int[] values) {
        int sum = 0;
        for (int value : values) {
            sum += value;
        }
        return sum;
    }

    protected int f3(int value) {
        return (value & 128) != 0 ? ((value << 1) ^ 27) & 255 : value << 1;
    }

    protected int pc(int value) {
        return f3(value) ^ value;
    }

    protected int sf(int value) {
        return pc(f3(value));
    }

    protected int lh(int value) {
        return sf(pc(f3(value)));
    }

    protected int ig(int value) {
        return lh(value) ^ sf(value) ^ pc(value);
    }

    /**
     * 解析接口响应，取 Lists 第一个列表的 items。
     */
    protected List<HotPostDataVO> parseHotList(String result) {
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

        JSONObject targetList = selectTargetList(lists);
        if (targetList == null) {
            log.error("未找到目标小黑盒榜单列表，type={}", getTypeName());
            return Collections.emptyList();
        }
        JSONArray items = targetList.getJSONArray("items");
        if (items == null || items.isEmpty()) {
            log.error("目标小黑盒榜单 items 为空，type={}", getTypeName());
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

    /**
     * 默认取 Lists 第一组，与现有小黑盒热榜逻辑保持一致。
     */
    protected JSONObject selectTargetList(JSONArray lists) {
        return lists.getJSONObject(0);
    }

    protected String buildItemUrl(JSONObject report) {
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

    protected HotPost buildHotPost(List<HotPostDataVO> dataList) {
        List<HotPostDataVO> topList = dataList.subList(0, Math.min(dataList.size(), TOP_N));
        return HotPost.builder()
                .sort(getSort())
                .category(CategoryTypeEnum.GENERAL_DISCUSSION.getValue())
                .name(getDisplayName())
                .updateInterval(UpdateIntervalEnum.HALF_HOUR.getValue())
                .iconUrl("https://www.xiaoheihe.cn/favicon.ico")
                .hostJson(JSON.toJSONString(topList))
                .typeName(getTypeName())
                .build();
    }

    protected Integer getSort() {
        return CategoryTypeEnum.GENERAL_DISCUSSION.getSort() + 1;
    }

    protected String getDisplayName() {
        return "小黑盒热榜";
    }

    protected String getTypeName() {
        return "小黑盒热榜";
    }
}
