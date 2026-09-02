package com.sap.jw.util;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import javax.net.ssl.SSLException;

/** 将学校系统底层网络异常归一化为稳定、可操作且不会出现 {@code null} 的用户提示。 */
public final class JwErrorMessages {

    private static final int MAX_CAUSE_DEPTH = 12;

    private JwErrorMessages() {}

    /**
     * 返回适合展示给用户的异常详情。技术堆栈仍由调用方记录到服务端日志，不向客户端泄露。
     *
     * @param error 原始异常（允许为 null）
     * @param fallback 非网络异常或无法识别时的业务兜底文案
     */
    public static String userDetail(Throwable error, String fallback) {
        String safeFallback = nonBlank(fallback, "学校系统请求异常，请稍后重试");
        if (error == null) return safeFallback;

        boolean ioFailure = false;
        Throwable current = error;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return "连接学校系统超时，请稍后重试";
            }
            if (current instanceof UnknownHostException) {
                return "暂时无法解析学校系统地址，请稍后重试";
            }
            if (current instanceof SSLException || current instanceof EOFException
                    || current instanceof ConnectException) {
                return "学校认证或教务系统暂时无法连接，请稍后重试";
            }
            if (current instanceof IOException) ioFailure = true;

            Throwable next = current.getCause();
            if (next == current) break;
            current = next;
        }
        if (ioFailure) return "学校系统网络异常，请稍后重试";
        return safeFallback;
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())
                ? fallback : value.trim();
    }
}
