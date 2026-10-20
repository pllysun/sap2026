package com.sap.jw.client;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 一次教务登录会话：持有独立的 HttpClient + CookieManager（保存 CAS/强智的 cookie）。
 * <p>登录成功后由 {@link JwAuthClient} 返回；后续抓课表/成绩复用同一会话，避免重复登录。
 * 重定向手动跟随，确保每一跳都带上 cookie（Java 自动重定向对 cookie 处理不稳定）。</p>
 */
public class JwHttpSession {

    /** 当前会话实际使用的访问链路；登录完成后的所有教务/评教请求必须沿用同一组域名和 Cookie。 */
    public record Route(
            boolean webvpn,
            String casBase,
            String casLoginUrl,
            String jwglBase,
            String qualityBase,
            String webvpnBase,
            String externalId,
            String callbackUrl,
            String directCasBase,
            String directJwglBase,
            String directQualityBase) {
    }

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0 Safari/537.36";

    private final HttpClient http;
    private final CookieManager cookieManager;
    private final int timeoutSeconds;
    private final long createdAt = System.currentTimeMillis();
    private volatile Route route;
    private volatile JwOrigins jwOrigins;
    private final java.util.Set<String> trustedOrigins = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 仅由服务端配置与固定路由调用，不接受客户端输入。 */
    public void trustConfiguredOrigin(String value) {
        if (value != null && !value.isBlank()) trustedOrigins.add(origin(URI.create(value)));
    }

    /** MFA 地址来自学校 CAS 的 HTTPS 响应，限定为既有源或学校 HTTPS 域名。 */
    public void trustSchoolAttestationOrigin(String value) {
        URI uri = URI.create(value);
        String target = origin(uri);
        if (trustedOrigins.contains(target)) return;
        String host = uri.getHost().toLowerCase(java.util.Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || effectivePort(uri) != 443 || !host.endsWith(".csuft.edu.cn"))
            throw new com.sap.common.BusinessException(502, "学校二次验证地址不受信任");
        trustedOrigins.add(target);
    }

    private String origin(URI uri) {
        if (uri.getHost() == null || uri.getUserInfo() != null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())))
            throw new com.sap.common.BusinessException(502, "学校响应包含无效跳转地址");
        return uri.getScheme().toLowerCase(java.util.Locale.ROOT) + "://" + uri.getHost().toLowerCase(java.util.Locale.ROOT) + ":" + effectivePort(uri);
    }

    /** 同一配置教务源站的 HTTP/HTTPS 地址对；不接受任意 WebVPN 子域。 */
    private record JwOrigins(String httpDirect, String httpsDirect, String httpProxy, String httpsProxy) {}

    public JwHttpSession(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.cookieManager = cm;
        this.http = HttpClient.newBuilder()
                .cookieHandler(cm)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    /** 只返回 Cookie 名称与作用域，不含任何值；用于安全诊断跨域 SSO 是否建立。 */
    String cookieSummary() {
        return cookieManager.getCookieStore().getCookies().stream()
                .map(cookie -> cookie.getName() + "@" + (cookie.getDomain() == null ? "" : cookie.getDomain())
                        + (cookie.getPath() == null ? "" : cookie.getPath()))
                .distinct()
                .sorted()
                .toList()
                .toString();
    }

    /** 返回指定请求实际会携带的 Cookie 名称，不包含值。 */
    String requestCookieSummary(String url) {
        try {
            return cookieManager.get(URI.create(url), Map.of()).values().stream()
                    .flatMap(java.util.Collection::stream)
                    .flatMap(value -> java.util.Arrays.stream(value.split(";")))
                    .map(String::trim)
                    .map(value -> {
                        int split = value.indexOf('=');
                        return split < 0 ? value : value.substring(0, split);
                    })
                    .filter(value -> !value.isBlank() && !value.startsWith("$"))
                    .distinct()
                    .sorted()
                    .toList()
                    .toString();
        } catch (Exception ignored) {
            return "[]";
        }
    }

    /**
     * 把门户 Cookie 限定复制到一个明确的 WebVPN 代理主机。
     * <p>WebVPN 的 {@code webvpn-token} 使用父域 Cookie，浏览器会把它发送给任意层级的
     * {@code *.webvpn.csuft.edu.cn}。JDK 的 Version=1 Cookie 实现却只允许一层子域，
     * 导致多级改写主机收不到登录票据。副本使用目标主机精确域名和 Version=0，行为与
     * 浏览器一致，同时不会扩大 Cookie 的作用域。</p>
     */
    boolean mirrorCookieToHost(String cookieName, String targetUrl) {
        if (cookieName == null || cookieName.isBlank() || targetUrl == null || targetUrl.isBlank()) return false;
        URI target = URI.create(targetUrl);
        if (target.getHost() == null) return false;
        HttpCookie source = cookieManager.getCookieStore().getCookies().stream()
                .filter(cookie -> cookieName.equalsIgnoreCase(cookie.getName()))
                .filter(cookie -> !cookie.hasExpired())
                .findFirst()
                .orElse(null);
        if (source == null) return false;

        HttpCookie copy = new HttpCookie(source.getName(), source.getValue());
        copy.setDomain(target.getHost());
        copy.setPath("/");
        copy.setSecure(source.getSecure());
        copy.setHttpOnly(source.isHttpOnly());
        copy.setDiscard(source.getDiscard());
        copy.setMaxAge(source.getMaxAge());
        // Version=1 正是 JDK 只允许一层子域并发出 $Version/$Domain 属性的根源。
        copy.setVersion(0);
        cookieManager.getCookieStore().add(target, copy);
        return true;
    }

    /**
     * 将 WebVPN 门户域下的全部有效 Cookie 复制到指定的代理资源主机。
     *
     * <p>学校新版 WebVPN 的资源单点登录除了 {@code webvpn-token} 外，还会按部署情况附带
     * 路由或负载均衡 Cookie。只复制 token 会使门户 API 已登录、强智资源却仍回到登录页。
     * 这里仅复制本次门户域能够匹配的 Cookie，并把域收紧为目标主机；CAS、强智自身等其它域的
     * Cookie 不会跨域带出。</p>
     *
     * @return 实际复制的 Cookie 数量
     */
    int mirrorPortalCookiesToHost(String portalBase, String targetUrl) {
        if (portalBase == null || portalBase.isBlank() || targetUrl == null || targetUrl.isBlank()) return 0;
        URI portal = URI.create(portalBase);
        URI target = URI.create(targetUrl);
        if (portal.getHost() == null || target.getHost() == null) return 0;

        int copied = 0;
        // 复制时会向同一个 CookieStore 新增目标域 Cookie，故必须先取快照，避免并发修改迭代器。
        for (HttpCookie source : new java.util.ArrayList<>(cookieManager.getCookieStore().getCookies())) {
            if (source.hasExpired() || !matchesPortalHost(source, portal.getHost())) continue;
            HttpCookie copy = new HttpCookie(source.getName(), source.getValue());
            copy.setDomain(target.getHost());
            copy.setPath(source.getPath() == null || source.getPath().isBlank() ? "/" : source.getPath());
            copy.setSecure(source.getSecure());
            copy.setHttpOnly(source.isHttpOnly());
            copy.setDiscard(source.getDiscard());
            copy.setMaxAge(source.getMaxAge());
            // 参见 mirrorCookieToHost：避免 Version=1 的子域匹配限制。
            copy.setVersion(0);
            cookieManager.getCookieStore().add(target, copy);
            copied++;
        }
        return copied;
    }

    private static boolean matchesPortalHost(HttpCookie cookie, String portalHost) {
        String domain = cookie.getDomain();
        if (domain == null || domain.isBlank()) return false;
        String normalized = domain.startsWith(".") ? domain.substring(1) : domain;
        // CookieStore 可能保留不带前导点的父域（例如 csuft.edu.cn）。JDK 的
        // HttpCookie.domainMatches 对这类 Version=1 Cookie 返回 false，但浏览器会发送给
        // webvpn.csuft.edu.cn；显式后缀判断需保留边界点，避免误匹配 evilcsuft.edu.cn。
        return portalHost.equalsIgnoreCase(normalized)
                || portalHost.toLowerCase(java.util.Locale.ROOT)
                .endsWith("." + normalized.toLowerCase(java.util.Locale.ROOT));
    }

    public boolean isExpired(int ttlMinutes) {
        return System.currentTimeMillis() - createdAt > ttlMinutes * 60_000L;
    }

    public void configureRoute(Route route) {
        if (route == null) throw new IllegalArgumentException("route 不能为空");
        this.jwOrigins = configuredJwOrigins(route);
        this.route = route;
        for (String value : new String[]{route.casBase(), route.jwglBase(), route.qualityBase(), route.webvpnBase(),
                route.directCasBase(), route.directJwglBase(), route.directQualityBase()}) trustConfiguredOrigin(value);
        if (jwOrigins != null) for (String value : new String[]{jwOrigins.httpDirect(), jwOrigins.httpsDirect(), jwOrigins.httpProxy(), jwOrigins.httpsProxy()}) trustConfiguredOrigin(value);
    }

    public Route getRoute() {
        Route value = route;
        if (value == null) throw new IllegalStateException("教务会话访问链路尚未初始化");
        return value;
    }

    public boolean isWebvpn() { return getRoute().webvpn(); }

    public String getCasBase() { return getRoute().casBase(); }

    public String getCasLoginUrl() { return getRoute().casLoginUrl(); }

    public String getJwglBase() { return getRoute().jwglBase(); }

    public String getQualityBase() { return getRoute().qualityBase(); }

    public HttpResponse<String> get(String url) throws Exception {
        return http.send(builder(url).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** GET with caller-supplied headers (for systems that issue a bearer token after CAS). */
    public HttpResponse<String> get(String url, Map<String, String> headers) throws Exception {
        return http.send(builder(url, headers).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** GET 并手动跟随重定向（每跳带 cookie），返回最终响应（字节，调用方按需解码）。 */
    public HttpResponse<byte[]> getFollow(String url, int maxHops) throws Exception {
        return getFollow(url, maxHops, Map.of());
    }

    /**
     * GET 并手动跟随重定向，且为整条导航链保持同一组受控的浏览器导航头。
     * WebVPN 校内服务会识别来源门户；调用方不得传入用户提供的 Header。
     */
    public HttpResponse<byte[]> getFollow(String url, int maxHops, Map<String, String> headers) throws Exception {
        String current = rewriteWebvpnRedirect(URI.create(url)).toString();
        HttpResponse<byte[]> resp = null;
        for (int i = 0; i < maxHops; i++) {
            resp = http.send(builder(current, headers).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            int sc = resp.statusCode();
            if (sc >= 300 && sc < 400) {
                String loc = resp.headers().firstValue("location").orElse(null);
                if (loc == null) break;
                current = rewriteWebvpnRedirect(URI.create(current).resolve(loc)).toString();
            } else {
                break;
            }
        }
        return resp;
    }

    /**
     * WebVPN 代理页偶尔原样返回校内源站的绝对 Location。浏览器注入脚本会把它重新
     * 代理化，Java 客户端则会误连校内 HTTP 地址并得到 502。这里只改写当前路由中
     * 明确配置的三个源站，路径、查询参数（含一次性票据）和片段均原样保留。
     */
    URI rewriteWebvpnRedirect(URI redirect) {
        Route value = route;
        if (value == null || !value.webvpn() || redirect == null || redirect.getHost() == null) return redirect;
        JwOrigins origins = jwOrigins;
        if (origins != null) {
            boolean secureTarget = sameNetworkOrigin(redirect, URI.create(origins.httpsDirect()))
                    || sameNetworkOrigin(redirect, URI.create(origins.httpsProxy()));
            if (secureTarget && !sameNetworkOrigin(URI.create(value.jwglBase()), URI.create(origins.httpsProxy()))) {
                // 学校把 HTTP/80 教务升级到 HTTPS/443 后，代理主机也随之改变。先把本次
                // 门户票据及教务 Cookie 复制到这个明确的同源升级主机，再发下一跳请求。
                mirrorPortalCookiesToHost(value.webvpnBase(), origins.httpsProxy());
                mirrorPortalCookiesToHost(value.jwglBase(), origins.httpsProxy());
                value = new Route(value.webvpn(), value.casBase(), value.casLoginUrl(), origins.httpsProxy(),
                        value.qualityBase(), value.webvpnBase(), value.externalId(), value.callbackUrl(),
                        value.directCasBase(), origins.httpsDirect(), value.directQualityBase());
                route = value;
            }
            // CAS 仍可能按旧 HTTP service 回跳。会话已升级后保持在 HTTPS 代理内，
            // 同时让后续课表、成绩和缓存会话使用实际的教务地址。
            for (String origin : new String[]{origins.httpDirect(), origins.httpsDirect(),
                    origins.httpProxy(), origins.httpsProxy()}) {
                if (sameNetworkOrigin(redirect, URI.create(origin))) {
                    return replaceConfiguredOrigin(redirect, origin, value.jwglBase());
                }
            }
        }
        URI rewritten = replaceConfiguredOrigin(redirect, value.directCasBase(), value.casBase());
        if (!rewritten.equals(redirect)) return rewritten;
        rewritten = replaceConfiguredOrigin(redirect, value.directJwglBase(), value.jwglBase());
        if (!rewritten.equals(redirect)) return rewritten;
        return replaceConfiguredOrigin(redirect, value.directQualityBase(), value.qualityBase());
    }

    private static JwOrigins configuredJwOrigins(Route value) {
        if (!value.webvpn()) return null;
        try {
            URI direct = URI.create(value.directJwglBase());
            URI proxy = URI.create(value.jwglBase());
            URI portal = URI.create(value.webvpnBase());
            if (direct.getHost() == null || portal.getHost() == null || proxy.getHost() == null
                    || !"https".equalsIgnoreCase(proxy.getScheme())
                    || !"https".equalsIgnoreCase(portal.getScheme())
                    || !("http".equalsIgnoreCase(direct.getScheme()) && effectivePort(direct) == 80
                        || "https".equalsIgnoreCase(direct.getScheme()) && effectivePort(direct) == 443)) return null;
            String host = direct.getHost().toLowerCase(java.util.Locale.ROOT);
            if (!host.matches("[a-z0-9.-]+")) return null;
            String suffix = host.replace('.', '-') + "-";
            String gateway = "." + portal.getHost();
            String httpProxy = new URI("https", null, "http-" + suffix + "80" + gateway,
                    proxy.getPort(), null, null, null).toString();
            String httpsProxy = new URI("https", null, "https-" + suffix + "443" + gateway,
                    proxy.getPort(), null, null, null).toString();
            if (!sameNetworkOrigin(proxy, URI.create(httpProxy))
                    && !sameNetworkOrigin(proxy, URI.create(httpsProxy))) return null;
            return new JwOrigins("http://" + host, "https://" + host, httpProxy, httpsProxy);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static URI replaceConfiguredOrigin(URI redirect, String directBase, String proxyBase) {
        if (directBase == null || directBase.isBlank() || proxyBase == null || proxyBase.isBlank()) return redirect;
        URI direct = URI.create(directBase);
        URI proxy = URI.create(proxyBase);
        if (!sameNetworkOrigin(redirect, direct)) return redirect;
        try {
            String origin = new URI(proxy.getScheme(), null, proxy.getHost(), proxy.getPort(),
                    null, null, null).toString();
            return URI.create(origin + (redirect.getRawPath() == null ? "" : redirect.getRawPath())
                    + (redirect.getRawQuery() == null ? "" : "?" + redirect.getRawQuery())
                    + (redirect.getRawFragment() == null ? "" : "#" + redirect.getRawFragment()));
        } catch (Exception ignored) {
            return redirect;
        }
    }

    private static boolean sameNetworkOrigin(URI first, URI second) {
        return first.getScheme() != null && second.getScheme() != null
                && first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost() != null && second.getHost() != null
                && first.getHost().equalsIgnoreCase(second.getHost())
                && effectivePort(first) == effectivePort(second);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        if ("http".equalsIgnoreCase(uri.getScheme())) return 80;
        if ("https".equalsIgnoreCase(uri.getScheme())) return 443;
        return -1;
    }

    public HttpResponse<String> postJson(String url, String json) throws Exception {
        return postJson(url, json, Map.of());
    }

    /** JSON POST with caller-supplied headers. */
    public HttpResponse<String> postJson(String url, String json, Map<String, String> headers) throws Exception {
        return http.send(builder(url, headers)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** Empty POST with caller-supplied headers. */
    public HttpResponse<String> post(String url, Map<String, String> headers) throws Exception {
        return http.send(builder(url, headers)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public HttpResponse<String> postForm(String url, Map<String, String> form) throws Exception {
        return postForm(url, form, Map.of());
    }

    /** POST 表单并附带导航/AJAX 请求头。 */
    public HttpResponse<String> postForm(String url, Map<String, String> form, Map<String, String> headers) throws Exception {
        java.util.List<String[]> pairs = new java.util.ArrayList<>();
        for (Map.Entry<String, String> e : form.entrySet()) pairs.add(new String[]{e.getKey(), e.getValue()});
        return postFormPairs(url, pairs, headers);
    }

    /**
     * POST 表单，键值对形式（允许重复键，保留顺序）。
     * 评教表单含重复的 {@code pj06xh} 和大量 {@code pj0601fz_*} 隐藏域，必须原样回传，故不能用 Map。
     */
    public HttpResponse<String> postFormPairs(String url, java.util.List<String[]> pairs) throws Exception {
        return postFormPairs(url, pairs, Map.of());
    }

    public HttpResponse<String> postFormPairs(String url, java.util.List<String[]> pairs, Map<String, String> headers) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (String[] p : pairs) {
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(p[0], StandardCharsets.UTF_8)).append('=')
              .append(URLEncoder.encode(p[1] == null ? "" : p[1], StandardCharsets.UTF_8));
        }
        return http.send(builder(url, headers)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(sb.toString(), StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpRequest.Builder builder(String url) {
        URI uri = URI.create(url);
        String target = origin(uri);
        synchronized (trustedOrigins) {
            // 兼容没有路由的单源抓取器；首个配置源站确定后，每次重定向都复验。
            if (trustedOrigins.isEmpty() && route == null) trustedOrigins.add(target);
            if (!trustedOrigins.contains(target)) throw new com.sap.common.BusinessException(502, "已阻止学校页面跳转到未配置的源站");
        }
        return HttpRequest.newBuilder(uri)
                .header("User-Agent", UA)
                .timeout(Duration.ofSeconds(timeoutSeconds));
    }

    private HttpRequest.Builder builder(String url, Map<String, String> headers) {
        HttpRequest.Builder b = builder(url);
        if (headers != null) headers.forEach(b::header);
        return b;
    }
}
