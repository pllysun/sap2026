package com.sap.jw.client;

/**
 * 账号开启了「安全手机」短信二次验证（深澜 MFA mfaTypeSecurePhone）。
 * 抛出前已 init + 发出短信，携带挂起的 CAS 上下文与掩码手机号，供 App 输码续登闭环。
 */
public class MfaRequiredException extends RuntimeException {

    private final transient PendingCas pending;
    private final String phone; // 掩码手机号，如 138****8888

    public MfaRequiredException(PendingCas pending, String phone) {
        super("需要短信二次验证");
        this.pending = pending;
        this.phone = phone;
    }

    public PendingCas getPending() {
        return pending;
    }

    public String getPhone() {
        return phone;
    }
}
