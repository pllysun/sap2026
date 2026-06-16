package com.sap.jw.client;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 一次教务登录会话：持有独立的 HttpClient + CookieManager（保存 WebVPN/CAS/强智 的 cookie）。
 * <p>登录成功后由 {@link JwAuthClient} 返回；后续抓课表/成绩复用同一会话，避免重复登录。
 * 重定向手动跟随，确保每一跳都带上 cookie（Java 自动重定向对 cookie 处理不稳定）。</p>
 */
public class JwHttpSession {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36";

    private final HttpClient http;
    private final int timeoutSeconds;
    private final long createdAt = System.currentTimeMillis();

    public JwHttpSession(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.http = HttpClient.newBuilder()
                .cookieHandler(cm)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    public boolean isExpired(int ttlMinutes) {
        return System.currentTimeMillis() - createdAt > ttlMinutes * 60_000L;
    }

    public HttpResponse<String> get(String url) throws Exception {
        return http.send(builder(url).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** GET 并手动跟随重定向（每跳带 cookie），返回最终响应（字节，调用方按需解码）。 */
    public HttpResponse<byte[]> getFollow(String url, int maxHops) throws Exception {
        String current = url;
        HttpResponse<byte[]> resp = null;
        for (int i = 0; i < maxHops; i++) {
            resp = http.send(builder(current).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            int sc = resp.statusCode();
            if (sc >= 300 && sc < 400) {
                String loc = resp.headers().firstValue("location").orElse(null);
                if (loc == null) break;
                current = URI.create(current).resolve(loc).toString();
            } else {
                break;
            }
        }
        return resp;
    }

    public HttpResponse<String> postJson(String url, String json) throws Exception {
        return http.send(builder(url)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public HttpResponse<String> postForm(String url, Map<String, String> form) throws Exception {
        java.util.List<String[]> pairs = new java.util.ArrayList<>();
        for (Map.Entry<String, String> e : form.entrySet()) pairs.add(new String[]{e.getKey(), e.getValue()});
        return postFormPairs(url, pairs);
    }

    /**
     * POST 表单，键值对形式（允许重复键，保留顺序）。
     * 评教表单含重复的 {@code pj06xh} 和大量 {@code pj0601fz_*} 隐藏域，必须原样回传，故不能用 Map。
     */
    public HttpResponse<String> postFormPairs(String url, java.util.List<String[]> pairs) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (String[] p : pairs) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(p[0], StandardCharsets.UTF_8)).append('=')
              .append(URLEncoder.encode(p[1] == null ? "" : p[1], StandardCharsets.UTF_8));
        }
        return http.send(builder(url)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(sb.toString(), StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder builder(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", UA)
                .timeout(Duration.ofSeconds(timeoutSeconds));
    }
}
