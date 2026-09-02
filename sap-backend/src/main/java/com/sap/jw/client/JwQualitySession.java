package com.sap.jw.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sap.common.BusinessException;
import com.sap.jw.util.JwErrorMessages;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 已完成教学质量保障系统 CAS 换票的 API 会话。
 * <p>该平台不是 HTML 表单系统：CAS 登录后还要用一次性 userToken 换取 accessToken，
 * 后续 REST 请求统一携带 {@code Authorization: Bearer<token>}（官方前端没有空格）。</p>
 */
public class JwQualitySession {

    private final JwHttpSession http;
    private final String base;
    private final String accessToken;
    private final long createdAt = System.currentTimeMillis();

    public JwQualitySession(JwHttpSession http, String base, String accessToken) {
        this.http = http;
        this.base = trimSlash(base);
        this.accessToken = accessToken;
    }

    public boolean isExpired(int ttlMinutes) {
        return System.currentTimeMillis() - createdAt > ttlMinutes * 60_000L;
    }

    /** POST + query 参数（新平台的查询接口均采用此形式）。 */
    public JSONObject post(String path, Map<String, ?> params) {
        try {
            HttpResponse<String> response = http.post(url(path, params), authHeaders());
            return requireSuccess(response);
        } catch (JwQualitySessionExpiredException | BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("教学评价系统请求失败：" + JwErrorMessages.userDetail(
                    e, "教学评价系统暂时异常，请稍后重试"));
        }
    }

    /** POST JSON（评价提交接口）。 */
    public JSONObject postJson(String path, Object body) {
        try {
            HttpResponse<String> response = http.postJson(url(path, Map.of()), JSON.toJSONString(body), authHeaders());
            return requireSuccess(response);
        } catch (JwQualitySessionExpiredException | BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("教学评价系统请求失败：" + JwErrorMessages.userDetail(
                    e, "教学评价系统暂时异常，请稍后重试"));
        }
    }

    private JSONObject requireSuccess(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status == 401 || status == 403) {
            throw new JwQualitySessionExpiredException("教学评价登录已失效");
        }
        if (status < 200 || status >= 300) {
            throw new BusinessException("教学评价系统请求失败（HTTP " + status + "）");
        }
        JSONObject json;
        try {
            json = JSON.parseObject(response.body());
        } catch (Exception e) {
            throw new BusinessException("教学评价系统返回格式异常");
        }
        Integer code = json == null ? null : json.getInteger("code");
        String message = json == null ? null : json.getString("message");
        if (code != null && code == 200) return json;
        if (code != null && (code == 21325 || code == 21327 || code == 504)
                || containsLoginExpired(message)) {
            throw new JwQualitySessionExpiredException(message == null ? "教学评价登录已失效" : message);
        }
        throw new BusinessException(message == null || message.isBlank() ? "教学评价系统操作失败" : message);
    }

    private Map<String, String> authHeaders() {
        return Map.of("Authorization", "Bearer" + accessToken);
    }

    private String url(String path, Map<String, ?> params) {
        String p = path.startsWith("/") ? path : "/" + path;
        StringBuilder out = new StringBuilder(base).append("/api").append(p);
        Map<String, ?> safe = params == null ? Map.of() : new LinkedHashMap<>(params);
        boolean first = !p.contains("?");
        for (Map.Entry<String, ?> entry : safe.entrySet()) {
            if (entry.getValue() == null) continue;
            out.append(first ? '?' : '&');
            first = false;
            out.append(enc(entry.getKey())).append('=').append(enc(String.valueOf(entry.getValue())));
        }
        return out.toString();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean containsLoginExpired(String message) {
        return message != null && (message.contains("登录超时") || message.contains("登录失效")
                || message.contains("Token") || message.contains("token"));
    }

    private static String trimSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
