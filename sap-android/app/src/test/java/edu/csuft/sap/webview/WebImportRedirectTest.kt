package edu.csuft.sap.webview

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebImportRedirectTest {

    @Test
    fun recognizesDirectAndWebvpnCasLoginPages() {
        assertTrue(isWebImportLoginUrl("https://cas.csuft.edu.cn/cas/login?service=x"))
        assertTrue(isWebImportLoginUrl(
            "https://https-cas-csuft-edu-cn-443.webvpn.csuft.edu.cn/cas/login?service=x",
        ))
        assertFalse(isWebImportLoginUrl("https://webvpn.csuft.edu.cn/site-nav/home"))
    }

    @Test
    fun onlyTreatsExplicitSiteNavPathAsWebvpnHome() {
        assertTrue(isWebvpnHomeUrl("https://webvpn.csuft.edu.cn/site-nav/home"))
        assertFalse(isWebvpnHomeUrl("https://webvpn.csuft.edu.cn/"))
        assertFalse(isWebvpnHomeUrl(
            "https://https-jxzlpt-csuft-edu-cn-443.webvpn.csuft.edu.cn/xssy",
        ))
    }

    @Test
    fun recognizesFusionPortalAndNewJwFrameworkEntry() {
        assertTrue(isWebvpnPortalUrl(
            "https://https-portal-csuft-edu-cn-443.webvpn.csuft.edu.cn/main.html#/Index",
        ))
        assertFalse(isWebvpnPortalUrl("https://webvpn.csuft.edu.cn/site-nav/home"))
        assertTrue(isJwFrameworkUrl(
            "https://http-jwxt-csuft-edu-cn-80.webvpn.csuft.edu.cn/jsxsd/framework/xsMainV.htmlx",
        ))
        assertFalse(isJwFrameworkUrl(
            "https://http-jwxt-csuft-edu-cn-80.webvpn.csuft.edu.cn/jsxsd/xskb/xskb_list.do",
        ))
    }

    @Test
    fun recoversQualityLoginLandingButNeverInterruptsCasOrJwPages() {
        assertTrue(isUnexpectedWebvpnProxyLanding(
            "https://https-jxzlpt-csuft-edu-cn-443.webvpn.csuft.edu.cn/login_znly",
        ))
        assertFalse(isUnexpectedWebvpnProxyLanding(
            "https://https-cas-csuft-edu-cn-443.webvpn.csuft.edu.cn/cas/login",
        ))
        assertTrue(isUnexpectedWebvpnProxyLanding(
            "https://http-jwgl-csuft-edu-cn-80.webvpn.csuft.edu.cn/jsxsd/framework/xsMain.jsp",
        ))
        assertFalse(isUnexpectedWebvpnProxyLanding(
            "https://http-jwxt-csuft-edu-cn-80.webvpn.csuft.edu.cn/jsxsd/framework/xsMainV.htmlx",
        ))
    }

    @Test
    fun onlyAcceptsTheConcreteStrongZhiScheduleRouteForImport() {
        assertFalse(isScheduleUrl(
            "https://http-jwgl-csuft-edu-cn-80.webvpn.csuft.edu.cn/jsxsd/xskb/xskb_list.do?viweType=0&xnxq01id=2026-2027-1",
        ))
        assertTrue(isScheduleUrl(
            "https://http-jwxt-csuft-edu-cn-80.webvpn.csuft.edu.cn/jsxsd/xskb/xskb_list.do",
        ))
        assertFalse(isScheduleUrl(
            "https://http-jwgl-csuft-edu-cn-80.webvpn.csuft.edu.cn/jsxsd/framework/xsMainV.htmlx",
        ))
        assertFalse(isScheduleUrl(
            "https://http-jwgl-csuft-edu-cn-80.webvpn.csuft.edu.cn/jsxsd/pyfajh/kclbcx.do",
        ))
    }
}
