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
