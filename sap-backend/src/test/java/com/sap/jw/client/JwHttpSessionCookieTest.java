package com.sap.jw.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import org.junit.jupiter.api.Test;

class JwHttpSessionCookieTest {

    @org.junit.jupiter.api.Test
    void refusesResponseControlledForeignOriginsBeforeAnyNetworkRequest() {
        JwHttpSession session = schoolSession();
        for (String target : java.util.List.of("http://127.0.0.1:5051/status", "http://169.254.169.254/latest/meta-data/", "https://evil.test/", "https://cas.csuft.edu.cn@evil.test/")) {
            org.junit.jupiter.api.Assertions.assertThrows(com.sap.common.BusinessException.class,
                    () -> session.get(target));
        }
        org.junit.jupiter.api.Assertions.assertThrows(com.sap.common.BusinessException.class,
                () -> session.trustSchoolAttestationOrigin("https://evil.test/"));
    }

    private static final String HTTP_JW = "https://http-jwxt-csuft-edu-cn-80.webvpn.csuft.edu.cn";
    private static final String HTTPS_JW = "https://https-jwxt-csuft-edu-cn-443.webvpn.csuft.edu.cn";

    private JwHttpSession schoolSession() {
        JwHttpSession session = new JwHttpSession(3);
        session.configureRoute(new JwHttpSession.Route(true,
                "https://https-cas-csuft-edu-cn-443.webvpn.csuft.edu.cn", "https://cas.csuft.edu.cn/cas/login",
                HTTP_JW, "https://https-jxzlpt-csuft-edu-cn-443.webvpn.csuft.edu.cn",
                "https://webvpn.csuft.edu.cn", "test-cas", "https://webvpn.csuft.edu.cn/site-nav/home",
                "https://cas.csuft.edu.cn", "http://jwxt.csuft.edu.cn", "https://jxzlpt.csuft.edu.cn"));
        return session;
    }

    private CookieManager cookies(JwHttpSession session) throws Exception {
        Field field = JwHttpSession.class.getDeclaredField("cookieManager");
        field.setAccessible(true);
        return (CookieManager) field.get(session);
    }

    private void addCookie(JwHttpSession session, String origin, String name, String domain) throws Exception {
        HttpCookie cookie = new HttpCookie(name, "test-value");
        cookie.setDomain(domain);
        cookie.setPath("/");
        cookie.setSecure(true);
        cookie.setVersion(1);
        cookies(session).getCookieStore().add(URI.create(origin), cookie);
    }

    @Test
    void schoolHttpsUpgradeCarriesPortalAndJwCookiesAndUpdatesCachedRoute() throws Exception {
        JwHttpSession session = schoolSession();
        addCookie(session, "https://webvpn.csuft.edu.cn", "webvpn-token", "csuft.edu.cn");
        addCookie(session, HTTP_JW, "JSESSIONID", URI.create(HTTP_JW).getHost());
        addCookie(session, session.getCasBase(), "TGC", URI.create(session.getCasBase()).getHost());
        assertEquals("[]", session.requestCookieSummary(HTTPS_JW));

        URI upgraded = URI.create(HTTPS_JW + "/jsxsd/sso.jsp?ticket=ST%2Fa%2Bb%26c#part%201");
        assertEquals(upgraded, session.rewriteWebvpnRedirect(upgraded));
        assertEquals(HTTPS_JW, session.getJwglBase());
        assertEquals("https://jwxt.csuft.edu.cn", session.getRoute().directJwglBase());
        assertEquals("[JSESSIONID, webvpn-token]", session.requestCookieSummary(HTTPS_JW));
        assertFalse(session.requestCookieSummary(HTTPS_JW).contains("TGC"));
        assertEquals("https://https-jxzlpt-csuft-edu-cn-443.webvpn.csuft.edu.cn", session.getQualityBase());
    }

    @Test
    void legacyCasCallbacksStayOnSecureProxyAndKeepEncodedTicketIntact() {
        JwHttpSession session = schoolSession();
        session.rewriteWebvpnRedirect(URI.create(HTTPS_JW + "/jsxsd/sso.jsp"));
        for (String base : new String[]{"http://jwxt.csuft.edu.cn", HTTP_JW}) {
            assertEquals(URI.create(HTTPS_JW + "/jsxsd/sso.jsp?ticket=ST%2Fabc%26def&service=a%3Fb%3Dc%26d%3De"),
                    session.rewriteWebvpnRedirect(URI.create(base + "/jsxsd/sso.jsp?ticket=ST%2Fabc%26def&service=a%3Fb%3Dc%26d%3De")));
        }
        assertEquals(HTTPS_JW, session.getJwglBase());
    }

    @Test
    void rawSchoolHttpsRedirectIsProxiedInsteadOfConnectingToCampusOnlyDns() {
        JwHttpSession session = schoolSession();
        assertEquals(URI.create(HTTPS_JW + "/jsxsd/sso.jsp?ticket=ST%2B123"),
                session.rewriteWebvpnRedirect(URI.create("https://jwxt.csuft.edu.cn/jsxsd/sso.jsp?ticket=ST%2B123")));
        assertEquals(HTTPS_JW, session.getJwglBase());
    }

    @Test
    void unrelatedHostsPortsAndUnencryptedProxyNeverReceiveMirroredCookies() throws Exception {
        JwHttpSession session = schoolSession();
        addCookie(session, "https://webvpn.csuft.edu.cn", "webvpn-token", "csuft.edu.cn");
        for (String target : new String[]{
                "https://https-other-csuft-edu-cn-443.webvpn.csuft.edu.cn/",
                HTTPS_JW + ":9443/", HTTPS_JW + ".example.test/",
                HTTPS_JW.replace("https://", "http://") + "/"}) {
            URI redirect = URI.create(target);
            assertEquals(redirect, session.rewriteWebvpnRedirect(redirect));
            assertEquals(HTTP_JW, session.getJwglBase());
        }
        assertEquals("[]", session.requestCookieSummary(HTTPS_JW));
    }

    @Test
    void expiredPortalCookieIsNotRevivedDuringUpgrade() throws Exception {
        JwHttpSession session = schoolSession();
        addCookie(session, "https://webvpn.csuft.edu.cn", "webvpn-token", "csuft.edu.cn");
        cookies(session).getCookieStore().getCookies().forEach(cookie -> cookie.setMaxAge(0));
        session.rewriteWebvpnRedirect(URI.create(HTTPS_JW + "/jsxsd/sso.jsp"));
        assertEquals("[]", session.requestCookieSummary(HTTPS_JW));
    }

    @Test
    void ordinaryConfiguredOriginRewritePreservesNestedEncodedParameters() {
        JwHttpSession session = schoolSession();
        URI cas = URI.create("https://cas.csuft.edu.cn/cas/login?service=http%3A%2F%2Fjwxt.csuft.edu.cn%2Fa%3Fx%3D1%26y%3D2");
        URI rewritten = session.rewriteWebvpnRedirect(cas);
        assertEquals(cas.getRawQuery(), rewritten.getRawQuery());
        assertEquals(URI.create(session.getCasBase()).getHost(), rewritten.getHost());
    }

    @Test
    void mirrorsVersionOneParentCookieToDeepWebvpnProxyHost() throws Exception {
        JwHttpSession session = new JwHttpSession(3);
        Field field = JwHttpSession.class.getDeclaredField("cookieManager");
        field.setAccessible(true);
        CookieManager manager = (CookieManager) field.get(session);

        HttpCookie token = new HttpCookie("webvpn-token", "test-token");
        token.setDomain("csuft.edu.cn");
        token.setPath("/");
        token.setSecure(true);
        token.setVersion(1);
        manager.getCookieStore().add(URI.create("https://webvpn.csuft.edu.cn"), token);

        String proxy = "https://http-jwgl-csuft-edu-cn-80.webvpn.csuft.edu.cn";
        assertEquals("[]", session.requestCookieSummary(proxy));

        assertTrue(session.mirrorCookieToHost("webvpn-token", proxy));
        assertEquals("[webvpn-token]", session.requestCookieSummary(proxy));
    }

    @Test
    void mirrorsAllPortalCookiesButNeverCasCookiesToTheProxyHost() throws Exception {
        JwHttpSession session = new JwHttpSession(3);
        Field field = JwHttpSession.class.getDeclaredField("cookieManager");
        field.setAccessible(true);
        CookieManager manager = (CookieManager) field.get(session);

        HttpCookie token = new HttpCookie("webvpn-token", "test-token");
        // WebVPN 实际可能以不带前导点的父域保存 Version=1 token；浏览器仍会把它发送给
        // webvpn.csuft.edu.cn，JDK 的 domainMatches 却不会，故这里覆盖该兼容分支。
        token.setDomain("csuft.edu.cn");
        token.setPath("/");
        token.setVersion(1);
        manager.getCookieStore().add(URI.create("https://webvpn.csuft.edu.cn"), token);
        HttpCookie route = new HttpCookie("SERVERID", "route-a");
        route.setDomain("webvpn.csuft.edu.cn");
        route.setPath("/");
        manager.getCookieStore().add(URI.create("https://webvpn.csuft.edu.cn"), route);
        HttpCookie cas = new HttpCookie("CASSESSIONID", "cas-only");
        cas.setDomain("cas.csuft.edu.cn");
        cas.setPath("/");
        manager.getCookieStore().add(URI.create("https://cas.csuft.edu.cn"), cas);

        String proxy = "https://http-jwxt-csuft-edu-cn-80.webvpn.csuft.edu.cn";
        assertEquals(2, session.mirrorPortalCookiesToHost("https://webvpn.csuft.edu.cn", proxy));
        assertEquals("[SERVERID, webvpn-token]", session.requestCookieSummary(proxy));
    }
}
