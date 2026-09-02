package com.sap.jw.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sap.common.BusinessException;
import com.sap.jw.config.JwProperties;
import com.sap.jw.util.JwErrorMessages;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 教学质量保障系统 CAS 单点登录：门户卡片入口 → CAS ticket → userToken → accessToken。 */
@Component
public class JwQualityAuthClient {

    private static final int MAX_REDIRECTS = 12;

    private final JwProperties props;

    public JwQualityAuthClient(JwProperties props) {
        this.props = props;
    }

    public JwQualitySession login(JwHttpSession casSession) {
        try {
            // 教务会话走 WebVPN 时必须沿用代理域名与同一 Cookie；直连回退则沿用直连域名。
            String base = trimSlash(casSession.getQualityBase());
            URI current = URI.create(base + ensureSlash(props.getQualitySsoPath()));
            for (int i = 0; i < MAX_REDIRECTS; i++) {
                HttpResponse<String> response = casSession.get(current.toString());
                int status = response.statusCode();
                if (status >= 300 && status < 400) {
                    String location = response.headers().firstValue("location").orElse(null);
                    if (location == null) break;
                    current = current.resolve(location);
                    continue;
                }
                break;
            }

            String userToken = queryParam(current, "userToken");
            if (userToken == null || userToken.isBlank()) {
                throw new BusinessException("教学评价单点登录失败：未获取 userToken");
            }
            String exchangeUrl = base + "/api/manage/cas/doLogin?userToken="
                    + URLEncoder.encode(userToken, StandardCharsets.UTF_8);
            HttpResponse<String> exchange = casSession.post(exchangeUrl, Map.of());
            JSONObject json = JSON.parseObject(exchange.body());
            JSONObject data = json == null ? null : json.getJSONObject("data");
            String accessToken = data == null ? null : data.getString("accessToken");
            if (exchange.statusCode() != 200 || json == null || json.getInteger("code") == null
                    || json.getInteger("code") != 200 || accessToken == null || accessToken.isBlank()) {
                String message = json == null ? null : json.getString("message");
                throw new BusinessException(message == null || message.isBlank()
                        ? "教学评价单点登录失败：未获取 accessToken" : message);
            }
            return new JwQualitySession(casSession, base, accessToken);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("教学评价单点登录异常：" + JwErrorMessages.userDetail(
                    e, "教学评价登录流程暂时异常，请稍后重试"));
        }
    }

    private static String queryParam(URI uri, String key) {
        String query = uri.getRawQuery();
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            if (!key.equals(name)) continue;
            return URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8);
        }
        return null;
    }

    private static String ensureSlash(String value) {
        return value != null && value.startsWith("/") ? value : "/" + value;
    }

    private static String trimSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
