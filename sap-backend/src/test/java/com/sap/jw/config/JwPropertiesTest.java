package com.sap.jw.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class JwPropertiesTest {

    @Test
    void defaultsPreferWebvpnButKeepDirectCasFallback() {
        JwProperties properties = new JwProperties();

        assertEquals(true, properties.isWebvpnPreferred());
        assertEquals("https://webvpn.csuft.edu.cn", properties.getWebvpnBase());
        assertEquals("https://webvpn.csuft.edu.cn/site-nav/home", properties.getWebvpnCallbackUrl());
        assertEquals(true, properties.isTrustAgentAfterMfa());
        assertEquals("", properties.getWebvpnExternalId());
        assertEquals("https://https-cas-csuft-edu-cn-443.webvpn.csuft.edu.cn",
                properties.getWebvpnCasBase());
        assertEquals("https://http-jwxt-csuft-edu-cn-80.webvpn.csuft.edu.cn",
                properties.getWebvpnJwglBase());
        assertEquals("https://http-jwgl-csuft-edu-cn-80.webvpn.csuft.edu.cn",
                properties.getWebvpnLegacyJwglBase());
        assertEquals("https://https-jxzlpt-csuft-edu-cn-443.webvpn.csuft.edu.cn",
                properties.getWebvpnQualityBase());
        assertEquals("https://cas.csuft.edu.cn", properties.getCasBase());
        assertEquals("http://jwxt.csuft.edu.cn", properties.getJwglBase());
        assertEquals("http://jwgl.csuft.edu.cn", properties.getLegacyJwglBase());
        assertEquals("http://jwxt.csuft.edu.cn/jsxsd/Logon.do?method=logonByZnlkd",
                properties.getJwServiceUrl());
        assertEquals(
                "https://cas.csuft.edu.cn/cas/login?service="
                        + "http%3A%2F%2Fjwxt.csuft.edu.cn%2Fjsxsd%2FLogon.do%3Fmethod%3DlogonByZnlkd",
                properties.getCasLoginUrl());
        assertEquals("https://jxzlpt.csuft.edu.cn", properties.getQualityBase());
    }

    @Test
    void exposesNewAndLegacyWebvpnEndpointsInOrder() {
        JwProperties properties = new JwProperties();

        assertEquals(List.of(
                        new JwProperties.JwEndpoint(
                                "https://http-jwxt-csuft-edu-cn-80.webvpn.csuft.edu.cn", "http://jwxt.csuft.edu.cn",
                                "/jsxsd/Logon.do?method=logonByZnlkd"),
                        new JwProperties.JwEndpoint(
                                "https://http-jwgl-csuft-edu-cn-80.webvpn.csuft.edu.cn", "http://jwgl.csuft.edu.cn",
                                "/Logon.do?method=logonByZnlkd")),
                properties.getWebvpnJwEndpoints());
    }

    @Test
    void normalizesConfigSlashesWhenBuildingServiceUrl() {
        JwProperties properties = new JwProperties();
        properties.setCasBase("https://cas.example.test/");
        properties.setJwglBase("http://jw.example.test/");
        properties.setJwSsoPath("Logon.do?method=sso");

        assertEquals("http://jw.example.test/Logon.do?method=sso", properties.getJwServiceUrl());
        assertEquals("https://cas.example.test/cas/login?service="
                        + "http%3A%2F%2Fjw.example.test%2FLogon.do%3Fmethod%3Dsso",
                properties.getCasLoginUrl());
    }
}
