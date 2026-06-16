package com.sap.jw.client;

/**
 * 自动 OCR 用尽仍需验证码 → 转人工。携带挂起的 CAS 上下文与当前验证码图片字节。
 */
public class CaptchaRequiredException extends RuntimeException {

    private final transient PendingCas pending;
    private final byte[] captchaImage;

    public CaptchaRequiredException(PendingCas pending, byte[] captchaImage) {
        super("需要图形验证码");
        this.pending = pending;
        this.captchaImage = captchaImage;
    }

    public PendingCas getPending() {
        return pending;
    }

    public byte[] getCaptchaImage() {
        return captchaImage;
    }
}
