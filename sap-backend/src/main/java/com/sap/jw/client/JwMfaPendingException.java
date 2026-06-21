package com.sap.jw.client;

/**
 * 拉取教务数据（课表/成绩/考试等）时，重新登录触发了短信二次验证(MFA)且短信已发出。
 * 与绑定时的 {@link MfaRequiredException} 不同：此处已由 {@code JwSessionManager} 登记好待验证会话
 * （PendingLogin），仅携带 challengeId + 掩码手机号抛出，交由全局异常处理返回 428，让 App 弹短信输入框，
 * 用户输码后调 {@code /api/jw/bind/mfa} 续登并缓存会话，再重试原请求。
 */
public class JwMfaPendingException extends RuntimeException {
    private final String challengeId;
    private final String phone;

    public JwMfaPendingException(String challengeId, String phone) {
        super("教务登录需短信二次验证");
        this.challengeId = challengeId;
        this.phone = phone;
    }

    public String getChallengeId() { return challengeId; }
    public String getPhone() { return phone; }
}
