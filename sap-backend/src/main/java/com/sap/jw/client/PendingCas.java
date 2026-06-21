package com.sap.jw.client;

/**
 * 验证码人工回退时挂起的 CAS 登录上下文。
 * 持有已带 cookie 的会话与登录表单要素；execution 随重载页刷新。
 */
public class PendingCas {

    public final JwHttpSession session;
    public final String account;
    public final String pwField;
    public final String mfaState;
    public final String fpId;
    public volatile String execution;
    /** 安全手机短信 MFA：initByType 拿到的鉴权服务器地址与组 id（发码/校验都要带）。 */
    public volatile String attestServerUrl;
    public volatile String gid;

    public PendingCas(JwHttpSession session, String account, String pwField,
                      String mfaState, String fpId, String execution) {
        this.session = session;
        this.account = account;
        this.pwField = pwField;
        this.mfaState = mfaState;
        this.fpId = fpId;
        this.execution = execution;
    }
}
