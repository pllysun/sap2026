package com.sap.jw.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import org.junit.jupiter.api.Test;

class JwHttpSessionCookieTest {

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
