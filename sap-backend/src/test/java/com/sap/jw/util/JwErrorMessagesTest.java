package com.sap.jw.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;

class JwErrorMessagesTest {

    @Test
    void connectFailureWithoutMessageNeverLeaksNull() {
        String message = JwErrorMessages.userDetail(new ConnectException(), "登录流程异常");

        assertEquals("学校认证或教务系统暂时无法连接，请稍后重试", message);
        assertFalse(message.contains("null"));
    }

    @Test
    void findsNestedTlsFailure() {
        RuntimeException wrapped = new RuntimeException(new SSLHandshakeException(null));

        assertEquals("学校认证或教务系统暂时无法连接，请稍后重试",
                JwErrorMessages.userDetail(wrapped, "登录流程异常"));
    }

    @Test
    void classifiesTimeoutDnsAndGenericIoSeparately() {
        assertEquals("连接学校系统超时，请稍后重试",
                JwErrorMessages.userDetail(new HttpTimeoutException(null), "兜底"));
        assertEquals("暂时无法解析学校系统地址，请稍后重试",
                JwErrorMessages.userDetail(new UnknownHostException(), "兜底"));
        assertEquals("学校系统网络异常，请稍后重试",
                JwErrorMessages.userDetail(new IOException(), "兜底"));
    }

    @Test
    void usesStableFallbackForUnknownOrMissingErrors() {
        assertEquals("登录流程异常", JwErrorMessages.userDetail(new RuntimeException(), "登录流程异常"));
        assertEquals("学校系统请求异常，请稍后重试", JwErrorMessages.userDetail(null, null));
        assertEquals("学校系统请求异常，请稍后重试",
                JwErrorMessages.userDetail(new RuntimeException(), "null"));
    }
}
