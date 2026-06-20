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

    /** 默认占位 AES 密钥（公开常量，绝不能用于生产，否则存库教务密码可被任何拿到源码者解密）。 */
    public static final String DEFAULT_AES_KEY = "change-me-in-prod-please-32bytes!";

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment env;

    /**
     * 启动期安全校验：生产/容器环境若仍使用默认占位 AES 密钥则 fail-fast，强制通过环境变量 JW_AES_KEY 注入。
     * dev 环境仅告警放行，便于本地调试。
     */
    @jakarta.annotation.PostConstruct
    public void validateAesKey() {
        if (!DEFAULT_AES_KEY.equals(aesKey)) return;
        java.util.List<String> profiles = java.util.Arrays.asList(env.getActiveProfiles());
        boolean prodLike = profiles.contains("prod") || profiles.contains("docker");
        if (prodLike) {
            throw new IllegalStateException("[安全] jw.aes-key 仍为默认占位值且当前为生产/容器环境" + profiles
                    + "：请通过环境变量 JW_AES_KEY 注入 32 字节强随机密钥后再启动，否则存库教务密码加密形同明文。");
        }
        org.slf4j.LoggerFactory.getLogger(JwProperties.class)
                .warn("[安全] jw.aes-key 仍为默认占位值(仅 dev 放行)。生产务必用环境变量 JW_AES_KEY 覆盖！");
    }
}
