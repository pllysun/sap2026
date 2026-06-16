package com.sap.jw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 教务(jw)模块配置
 * <p>学校 WebVPN(深澜) + CAS + 强智教务 的接入参数。默认值对应中南林业科技大学，
 * 学校升级导致 externalId / 代理域名变化时改这里即可。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "jw")
public class JwProperties {

    /** 深澜 WebVPN 门户根地址 */
    private String webvpnBase = "https://webvpn.csuft.edu.cn";

    /** CAS(融合门户) 经 WebVPN 代理后的地址 */
    private String casBase = "https://https-cas-csuft-edu-cn-443.webvpn.csuft.edu.cn";

    /** 强智教务 经 WebVPN 代理后的地址 */
    private String jwglBase = "https://http-jwgl-csuft-edu-cn-80.webvpn.csuft.edu.cn";

    /** WebVPN CAS 验证方式 externalId（取自 /api/access/authentication/list） */
    private String externalId = "eAF0IG5N";

    /** 强智 SSO 直登入口（深澜应用代登录，带 CAS 会话自动登进强智） */
    private String jwSsoPath = "/Logon.do?method=logonByZnlkd";

    /** CAS 认证回调地址（auth/start、auth/finish 用） */
    private String callbackUrl = "https://webvpn.csuft.edu.cn/";

    /** AES 密钥：用于加密存储学校账号密码。生产务必通过环境变量 JW_AES_KEY 覆盖。 */
    private String aesKey = "change-me-in-prod-please-32bytes!";

    /** 登录会话内存缓存有效期(分钟)。学校 WebVPN 会话约 30 分钟，留余量取 25。 */
    private int sessionTtlMinutes = 25;

    /** 单次 HTTP 请求超时(秒) */
    private int httpTimeoutSeconds = 25;

    /** OCR 边车地址（ddddocr，POST /ocr 图片字节 → {code}）。 */
    private String ocrUrl = "http://127.0.0.1:9000";

    /** 深澜 CAS 验证码图片路径（相对 casBase）。 */
    private String captchaPath = "/cas/captcha.jpg";

    /** 验证码自动 OCR 最大重试次数；超过则转人工。 */
    private int captchaMaxOcr = 4;
}
