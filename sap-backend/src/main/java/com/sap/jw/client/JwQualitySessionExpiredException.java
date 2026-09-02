package com.sap.jw.client;

/** 教学质量保障系统的 bearer 登录态失效；上层应重走 CAS 单点登录后重试一次。 */
public class JwQualitySessionExpiredException extends RuntimeException {
    public JwQualitySessionExpiredException(String message) {
        super(message);
    }
}
