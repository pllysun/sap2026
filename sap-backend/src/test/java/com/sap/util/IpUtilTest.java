package com.sap.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

/** 客户端 IP 解析单测：X-Real-IP 优先、XFF 取最后一跳(防伪造首段)、RemoteAddr 兜底、null/空白。 */
class IpUtilTest {

    @Test
    void null请求返回unknown() {
        assertEquals("unknown", IpUtil.clientIp(null));
    }

    @Test
    void XRealIP优先于XFF和RemoteAddr() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addHeader("X-Real-IP", "9.9.9.9");
        r.addHeader("X-Forwarded-For", "1.1.1.1, 2.2.2.2"); // 客户端伪造的首段不被采信
        r.setRemoteAddr("8.8.8.8");
        assertEquals("9.9.9.9", IpUtil.clientIp(r));
    }

    @Test
    void 无XRealIP时XFF取最后一跳() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        // 首段 1.1.1.1 是客户端可伪造的；最后一跳 2.2.2.2 才是可信反代追加的真实对端
        r.addHeader("X-Forwarded-For", "1.1.1.1, 2.2.2.2");
        assertEquals("2.2.2.2", IpUtil.clientIp(r));
    }

    @Test
    void XFF最后一跳含空格被trim() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addHeader("X-Forwarded-For", "1.1.1.1 ,  2.2.2.2  ");
        assertEquals("2.2.2.2", IpUtil.clientIp(r));
    }

    @Test
    void 无代理头走RemoteAddr() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr("3.3.3.3");
        assertEquals("3.3.3.3", IpUtil.clientIp(r));
    }

    @Test
    void 空白头回退RemoteAddr() {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addHeader("X-Real-IP", "  ");
        r.addHeader("X-Forwarded-For", "   ");
        r.setRemoteAddr("4.4.4.4");
        assertEquals("4.4.4.4", IpUtil.clientIp(r));
    }
}
