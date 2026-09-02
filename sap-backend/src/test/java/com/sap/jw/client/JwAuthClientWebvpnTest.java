package com.sap.jw.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sap.common.BusinessException;
import com.sap.jw.config.JwProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwAuthClientWebvpnTest {

    private static final String CURRENT_EXTERNAL_ID = "current-cas-id";

    private HttpServer webvpn;
    private HttpServer cas;
    private HttpServer jw;
    private String webvpnBase;
    private String casBase;
    private String jwBase;
    private String callbackUrl;
    private String casCallbackUrl;
    private final List<String> startedExternalIds = new ArrayList<>();
    private final AtomicReference<JSONObject> finishBody = new AtomicReference<>();
    private final AtomicBoolean casReturnsConfiguredCallback = new AtomicBoolean();
    private final AtomicBoolean jwReturnsDirectOrigin = new AtomicBoolean();
    private final AtomicBoolean jwReturnsLoginPage = new AtomicBoolean();
    private final AtomicBoolean navigationEntryAuthenticates = new AtomicBoolean();
    private final AtomicBoolean requireMfaUntilTrusted = new AtomicBoolean();
    private final AtomicReference<String> trustedFingerprint = new AtomicReference<>();
    private final AtomicReference<String> mfaDetectBody = new AtomicReference<>();
    private final AtomicReference<String> casPostBody = new AtomicReference<>();
    private final AtomicInteger smsSends = new AtomicInteger();
    private final AtomicInteger smsValidations = new AtomicInteger();
    private final AtomicInteger navigationCodeRequests = new AtomicInteger();
    private final AtomicInteger portalBridgeVisits = new AtomicInteger();
    private final AtomicReference<String> jwReferer = new AtomicReference<>();
    private final AtomicReference<String> navigationQuery = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        webvpn = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        cas = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jw = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        webvpnBase = "http://127.0.0.1:" + webvpn.getAddress().getPort();
        casBase = "http://127.0.0.1:" + cas.getAddress().getPort();
        jwBase = "http://127.0.0.1:" + jw.getAddress().getPort();
        callbackUrl = webvpnBase + "/site-nav/home";
        casCallbackUrl = webvpnBase + "/callback/cas/" + CURRENT_EXTERNAL_ID;

        String publicKey = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(
                KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic().getEncoded());
        String pem = "-----BEGIN PUBLIC KEY-----\n" + publicKey + "\n-----END PUBLIC KEY-----";

        webvpn.createContext("/api/access/authentication/list", exchange -> sendJson(exchange, 200,
                "{\"code\":0,\"data\":{\"list\":["
                        + "{\"externalId\":\"not-cas\",\"authType\":3},"
                        + "{\"externalId\":\"" + CURRENT_EXTERNAL_ID + "\",\"authType\":4}]}}"));
        webvpn.createContext("/api/access/auth/start", exchange -> {
            JSONObject request = JSON.parseObject(readBody(exchange));
            String externalId = request.getString("externalId");
            startedExternalIds.add(externalId);
            if (!CURRENT_EXTERNAL_ID.equals(externalId)) {
                sendJson(exchange, 200, "{\"code\":20012,\"message\":\"找不到认证方式\",\"data\":null}");
                return;
            }
            JSONObject data = JSON.parseObject(request.getString("data"));
            assertEquals(callbackUrl, data.getString("callbackUrl"));
            String loginUrl = casBase + "/cas/login?service="
                    + java.net.URLEncoder.encode(casCallbackUrl, StandardCharsets.UTF_8);
            sendJson(exchange, 200, "{\"code\":0,\"data\":{\"action\":{\"login_url\":\""
                    + loginUrl.replace("&", "\\u0026") + "\"}}}");
        });
        webvpn.createContext("/api/access/auth/finish", exchange -> {
            finishBody.set(JSON.parseObject(readBody(exchange)));
            exchange.getResponseHeaders().add("Set-Cookie",
                    "webvpn-token=test-token; Path=/; Domain=127.0.0.1; Version=1");
            sendJson(exchange, 200, "{\"code\":0,\"message\":\"ok\"}");
        });
        webvpn.createContext("/api/access/user/info", exchange ->
                sendJson(exchange, 200, "{\"code\":0,\"data\":{\"userId\":1}}"));
        webvpn.createContext("/api/access/nav/site-list", exchange -> sendJson(exchange, 200,
                navigationEntryAuthenticates.get()
                        ? "{\"code\":0,\"data\":{\"list\":[{\"sites\":[{\"url\":\""
                                + jwBase + "/jsxsd/framework/xsMainV.htmlx?opaque=${CODE}\"}]}]}}"
                        : "{\"code\":0,\"data\":{\"list\":[]}}"));
        webvpn.createContext("/api/access/imufe/code", exchange -> {
            navigationCodeRequests.incrementAndGet();
            sendJson(exchange, 200, "{\"code\":0,\"data\":{\"code\":\"nav-code\"}}");
        });
        webvpn.createContext("/api/guard/securephone/send", exchange -> {
            smsSends.incrementAndGet();
            sendJson(exchange, 200, "{\"code\":0,\"data\":{}}");
        });
        webvpn.createContext("/api/guard/securephone/valid", exchange -> {
            smsValidations.incrementAndGet();
            sendJson(exchange, 200, "{\"code\":0,\"data\":{\"status\":2}}");
        });

        cas.createContext("/cas/login", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                send(exchange, 200, "<input name=\"execution\" value=\"execution-webvpn\">");
                return;
            }
            String form = readBody(exchange);
            casPostBody.set(form);
            if ("true".equals(formParam(form, "trustAgent"))) {
                trustedFingerprint.set(formParam(form, "fpVisitorId"));
            }
            String ticketCallback = casReturnsConfiguredCallback.get() ? callbackUrl : casCallbackUrl;
            exchange.getResponseHeaders().add("Location", ticketCallback + "?ticket=ST-webvpn-test");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        cas.createContext("/cas/jwt/publicKey", exchange -> send(exchange, 200, pem));
        cas.createContext("/cas/mfa/detect", exchange -> {
            String form = readBody(exchange);
            mfaDetectBody.set(form);
            String fingerprint = formParam(form, "fpVisitorId");
            boolean need = requireMfaUntilTrusted.get() && !fingerprint.equals(trustedFingerprint.get());
            sendJson(exchange, 200, need
                    ? "{\"code\":0,\"data\":{\"need\":true,\"state\":\"mfa-state\",\"mfaTypeSecurePhone\":true}}"
                    : "{\"code\":0,\"data\":{\"need\":false,\"state\":\"\"}}");
        });
        cas.createContext("/cas/mfa/initByType/securephone", exchange -> sendJson(exchange, 200,
                "{\"code\":0,\"data\":{\"attestServerUrl\":\"" + webvpnBase
                        + "\",\"gid\":\"mfa-gid\",\"securePhone\":\"138****0000\"}}"));

        jw.createContext("/main.html", exchange -> {
            portalBridgeVisits.incrementAndGet();
            send(exchange, 200, "fusion portal");
        });
        jw.createContext("/Logon.do", exchange -> {
            jwReferer.set(exchange.getRequestHeaders().getFirst("Referer"));
            exchange.getResponseHeaders().add("Location", jwReturnsDirectOrigin.get()
                    ? "http://jwgl.csuft.edu.cn/jsxsd/framework/xsMain.jsp"
                    : "/jsxsd/framework/xsMain.jsp");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        jw.createContext("/jsxsd/framework/xsMain.jsp", exchange -> send(exchange, 200,
                jwReturnsLoginPage.get()
                        ? "<form name=\"loginForm\" action=\"/jsxsd/xk/LoginToXk\"></form>"
                        : "xsMain"));
        jw.createContext("/jsxsd/framework/xsMainV.htmlx", exchange -> {
            jwReferer.set(exchange.getRequestHeaders().getFirst("Referer"));
            navigationQuery.set(exchange.getRequestURI().getRawQuery());
            send(exchange, 200, "xsMainV");
        });
        jw.createContext("/cas/toUrl", exchange -> {
            exchange.getResponseHeaders().add("Location", "/quality/callback?userToken=quality-user-token");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        jw.createContext("/quality/callback", exchange -> send(exchange, 200, "quality callback"));
        jw.createContext("/api/manage/cas/doLogin", exchange ->
                sendJson(exchange, 200, "{\"code\":200,\"data\":{\"accessToken\":\"access-1\"}}"));

        webvpn.start();
        cas.start();
        jw.start();
    }

    @AfterEach
    void tearDown() {
        if (webvpn != null) webvpn.stop(0);
        if (cas != null) cas.stop(0);
        if (jw != null) jw.stop(0);
    }

    @Test
    void dynamicallyDiscoversCurrentExternalIdAndKeepsProxyRoute() {
        JwHttpSession session = client("").login("student", "secret");

        assertTrue(session.isWebvpn());
        assertEquals(List.of(CURRENT_EXTERNAL_ID), startedExternalIds);
        assertEquals(jwBase, session.getJwglBase());
        assertEquals(jwBase, session.getQualityBase());
        assertFinishUsesCurrentIdAndCallback(casCallbackUrl);
    }

    @Test
    void opensTheWebvpnNavigationResourceWithItsOneTimeCodeBeforeLegacySsoFallback() {
        navigationEntryAuthenticates.set(true);

        JwHttpSession session = client("").login("student", "secret");

        assertTrue(session.isWebvpn());
        // 本地契约测试中融合门户与教务复用同一模拟主机，故门户桥接和教务入口都可能
        // 申请一次 code；生产环境两者为不同 WebVPN 子域。
        assertTrue(navigationCodeRequests.get() >= 1);
        assertEquals(jwBase, session.getJwglBase());
        assertEquals("nav-code", formParam(navigationQuery.get(), "code"));
        assertTrue(formParam(navigationQuery.get(), "opaque").matches("[A-Za-z0-9]{6}"));
    }

    @Test
    void bridgesFusionPortalBeforeOpeningNewJwxtPage() {
        JwHttpSession session = client("").login("student", "secret");

        assertTrue(session.isWebvpn());
        assertEquals(1, portalBridgeVisits.get());
        assertEquals(jwBase + "/main.html", jwReferer.get());
    }

    @Test
    void refreshesStaleConfiguredExternalIdBeforeSubmittingCredentials() {
        JwHttpSession session = client("old-cas-id").login("student", "secret");

        assertTrue(session.isWebvpn());
        assertEquals(List.of("old-cas-id", CURRENT_EXTERNAL_ID), startedExternalIds);
        assertFinishUsesCurrentIdAndCallback(casCallbackUrl);
    }

    @Test
    void usesConfiguredLandingPageWhenCasReturnsTicketDirectlyToIt() {
        casReturnsConfiguredCallback.set(true);

        JwHttpSession session = client("").login("student", "secret");

        assertTrue(session.isWebvpn());
        assertFinishUsesCurrentIdAndCallback(callbackUrl);
    }

    @Test
    void rewritesStrongAbsoluteOriginRedirectBackThroughWebvpnProxy() {
        jwReturnsDirectOrigin.set(true);

        JwHttpSession session = client("").login("student", "secret");

        assertTrue(session.isWebvpn());
        assertEquals(jwBase, session.getJwglBase());
    }

    @Test
    void fallsBackToLegacyJwEndpointWhenNewEndpointDoesNotEstablishSso() {
        JwProperties properties = properties("");
        // 模拟新版入口存在但返回非教务页（例如学校灰度迁移/503），旧入口仍可完成单点登录。
        properties.setWebvpnJwglBase(webvpnBase + "/unavailable");
        properties.setJwglBase(webvpnBase);
        properties.setWebvpnLegacyJwglBase(jwBase);
        properties.setLegacyJwglBase(jwBase);

        JwHttpSession session = new JwAuthClient(properties, new OcrClient(properties))
                .login("student", "secret");

        assertEquals(jwBase, session.getJwglBase());
    }

    @Test
    void rejectsNewLoginPageEvenWhenItUsesAJsxsdUrlAndHttp200() {
        jwReturnsLoginPage.set(true);

        BusinessException error = assertThrows(BusinessException.class,
                () -> client("").login("student", "secret"));

        assertTrue(error.getMessage().contains("单点登录未完成"));
    }

    @Test
    void qualityLoginUsesTheSameWebvpnProxyRouteAndCorrectCasEntry() {
        JwProperties properties = properties("");
        JwHttpSession session = new JwAuthClient(properties, new OcrClient(properties))
                .login("student", "secret");

        JwQualitySession quality = new JwQualityAuthClient(properties).login(session);

        assertNotNull(quality);
        assertEquals(jwBase, session.getQualityBase());
    }

    @Test
    void trustsStableAccountFingerprintOnlyAfterSuccessfulSmsVerification() {
        requireMfaUntilTrusted.set(true);
        JwAuthClient client = client("");

        MfaRequiredException challenge = assertThrows(MfaRequiredException.class,
                () -> client.login("student", "secret"));
        String firstFingerprint = formParam(mfaDetectBody.get(), "fpVisitorId");
        assertEquals(20, firstFingerprint.length());
        assertEquals(1, smsSends.get());

        JwHttpSession verified = client.continueWithMfa(challenge.getPending(), "1234");

        assertTrue(verified.isWebvpn());
        assertEquals(1, smsValidations.get());
        assertEquals("true", formParam(casPostBody.get(), "trustAgent"));
        assertEquals(firstFingerprint, formParam(casPostBody.get(), "fpVisitorId"));
        assertEquals(firstFingerprint, trustedFingerprint.get());

        // 新建 HTTP 会话仍使用相同账号专属指纹，因此模拟 CAS 不再要求短信。
        JwHttpSession trustedLogin = client.login("student", "secret");
        assertTrue(trustedLogin.isWebvpn());
        assertEquals(firstFingerprint, formParam(mfaDetectBody.get(), "fpVisitorId"));
        assertEquals(1, smsSends.get());
    }

    private JwAuthClient client(String configuredExternalId) {
        JwProperties properties = properties(configuredExternalId);
        return new JwAuthClient(properties, new OcrClient(properties));
    }

    private JwProperties properties(String configuredExternalId) {
        JwProperties properties = new JwProperties();
        properties.setWebvpnPreferred(true);
        properties.setWebvpnBase(webvpnBase);
        properties.setWebvpnCasBase(casBase);
        properties.setWebvpnPortalBase(jwBase);
        properties.setWebvpnJwglBase(jwBase);
        properties.setWebvpnLegacyJwglBase(jwBase);
        properties.setJwglBase("http://jwgl.csuft.edu.cn");
        properties.setLegacyJwglBase("http://jwgl.csuft.edu.cn");
        properties.setWebvpnJwSsoPath("/Logon.do?method=logonByZnlkd");
        properties.setWebvpnLegacyJwSsoPath("/Logon.do?method=logonByZnlkd");
        properties.setWebvpnQualityBase(jwBase);
        properties.setWebvpnCallbackUrl(callbackUrl);
        properties.setWebvpnExternalId(configuredExternalId);
        properties.setHttpTimeoutSeconds(3);
        return properties;
    }

    private void assertFinishUsesCurrentIdAndCallback(String expectedCallbackUrl) {
        JSONObject finish = finishBody.get();
        assertNotNull(finish);
        assertEquals(CURRENT_EXTERNAL_ID, finish.getString("externalId"));
        JSONObject data = JSON.parseObject(finish.getString("data"));
        assertEquals(expectedCallbackUrl, data.getString("callbackUrl"));
        assertEquals("ST-webvpn-test", data.getString("ticket"));
        assertEquals(formParam(mfaDetectBody.get(), "fpVisitorId"), data.getString("deviceId"));
    }

    private static String formParam(String form, String name) {
        if (form == null) return null;
        for (String part : form.split("&")) {
            String[] pair = part.split("=", 2);
            if (name.equals(URLDecoder.decode(pair[0], StandardCharsets.UTF_8))) {
                return URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        send(exchange, status, body);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
