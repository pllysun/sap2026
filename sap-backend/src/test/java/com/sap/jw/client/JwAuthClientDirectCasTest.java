package com.sap.jw.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sap.jw.config.JwProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwAuthClientDirectCasTest {

    private HttpServer cas;
    private HttpServer jw;
    private String casBase;
    private String jwBase;
    private final AtomicInteger jwTicketCallbacks = new AtomicInteger();
    private final AtomicReference<String> casPostQuery = new AtomicReference<>();
    private final AtomicReference<String> casPostBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        cas = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jw = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        casBase = "http://127.0.0.1:" + cas.getAddress().getPort();
        jwBase = "http://127.0.0.1:" + jw.getAddress().getPort();

        String publicKey = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(
                KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic().getEncoded());
        String pem = "-----BEGIN PUBLIC KEY-----\n" + publicKey + "\n-----END PUBLIC KEY-----";

        cas.createContext("/cas/login", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                send(exchange, 200, "<html><input name=\"execution\" value=\"execution-token\"></html>");
                return;
            }
            casPostQuery.set(exchange.getRequestURI().getRawQuery());
            casPostBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Location",
                    jwBase + "/Logon.do?method=logonByZnlkd&ticket=ST-direct-cas-test");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        cas.createContext("/cas/jwt/publicKey", exchange -> send(exchange, 200, pem));
        cas.createContext("/cas/mfa/detect", exchange ->
                send(exchange, 200, "{\"code\":0,\"data\":{\"need\":false,\"state\":\"\"}}"));

        jw.createContext("/Logon.do", exchange -> {
            jwTicketCallbacks.incrementAndGet();
            assertTrue(exchange.getRequestURI().getRawQuery().contains("ticket=ST-direct-cas-test"));
            exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=direct-cas-test; Path=/");
            exchange.getResponseHeaders().add("Location", "/jsxsd/framework/xsMain.jsp");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        jw.createContext("/jsxsd/framework/xsMain.jsp", exchange -> send(exchange, 200, "xsMain"));
        jw.createContext("/jsxsd/xskb/xskb_list.do", exchange -> authenticated(exchange, "schedule-dataList"));
        jw.createContext("/jsxsd/kscj/cjcx_list", exchange -> authenticated(exchange, "grade-dataList"));
        cas.start();
        jw.start();
    }

    @AfterEach
    void tearDown() {
        if (cas != null) cas.stop(0);
        if (jw != null) jw.stop(0);
    }

    @Test
    void logsInThroughDirectCasTicketAndReusesSessionForScheduleAndGrades() throws Exception {
        JwProperties properties = new JwProperties();
        properties.setWebvpnPreferred(false);
        properties.setCasBase(casBase);
        properties.setJwglBase(jwBase);
        properties.setJwSsoPath("/Logon.do?method=logonByZnlkd");
        properties.setHttpTimeoutSeconds(3);
        JwAuthClient client = new JwAuthClient(properties, new OcrClient(properties));

        JwHttpSession session = client.login("student", "secret");

        assertNotNull(session);
        assertEquals(1, jwTicketCallbacks.get());
        assertNotNull(casPostQuery.get());
        assertEquals(properties.getJwServiceUrl(), queryParam(casPostQuery.get(), "service"));
        assertTrue(casPostBody.get().contains("execution=execution-token"));
        assertTrue(casPostBody.get().contains("password=__RSA__"));
        assertFalse(casPostBody.get().contains("secret"));

        String schedule = new String(session.getFollow(
                jwBase + "/jsxsd/xskb/xskb_list.do", 2).body(), StandardCharsets.UTF_8);
        String grades = new String(session.getFollow(
                jwBase + "/jsxsd/kscj/cjcx_list", 2).body(), StandardCharsets.UTF_8);
        assertEquals("schedule-dataList", schedule);
        assertEquals("grade-dataList", grades);
    }

    @Test
    void fallsBackToDirectCasWhenWebvpnDiscoveryIsUnavailable() {
        JwProperties properties = new JwProperties();
        properties.setWebvpnPreferred(true);
        properties.setWebvpnBase(casBase + "/unavailable-webvpn");
        properties.setCasBase(casBase);
        properties.setJwglBase(jwBase);
        properties.setJwSsoPath("/Logon.do?method=logonByZnlkd");
        properties.setHttpTimeoutSeconds(3);
        JwAuthClient client = new JwAuthClient(properties, new OcrClient(properties));

        JwHttpSession session = client.login("student", "secret");

        assertNotNull(session);
        assertFalse(session.isWebvpn());
        assertEquals(jwBase, session.getJwglBase());
        assertEquals(1, jwTicketCallbacks.get());
    }

    private static void authenticated(HttpExchange exchange, String body) throws IOException {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie == null || !cookie.contains("JSESSIONID=direct-cas-test")) {
            send(exchange, 401, "missing session");
            return;
        }
        send(exchange, 200, body);
    }

    private static String queryParam(String query, String name) {
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (name.equals(URLDecoder.decode(pair[0], StandardCharsets.UTF_8))) {
                return URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
