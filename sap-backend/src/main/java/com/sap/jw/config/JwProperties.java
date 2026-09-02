package com.sap.jw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 教务(jw)模块配置。
 * <p>优先使用学校 WebVPN（校外访问稳定且可复用门户单点登录），WebVPN 启动失败时自动回退
 * 直连 CAS。所有地址均可通过 Spring 配置覆盖。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "jw")
public class JwProperties {

    /** WebVPN 恢复后优先走代理；认证发现/启动不可用时仍会自动回退直连 CAS。 */
    private boolean webvpnPreferred = true;

    /** WebVPN 门户根地址。 */
    private String webvpnBase = "https://webvpn.csuft.edu.cn";

    /** CAS 经 WebVPN 改写后的地址。 */
    private String webvpnCasBase = "https://https-cas-csuft-edu-cn-443.webvpn.csuft.edu.cn";

    /**
     * 融合门户经 WebVPN 改写后的地址。
     *
     * <p>新版强智教务要求先加载融合门户，再由门户新开教务页；服务端同步必须复刻这条
     * 单点登录桥接链路，不能从 WebVPN 根页直接进入教务。</p>
     */
    private String webvpnPortalBase = "https://https-portal-csuft-edu-cn-443.webvpn.csuft.edu.cn";

    /**
     * 当前校方新版强智教务主入口经 WebVPN 改写后的地址。
     * 2026 年迁移后，学生端首页与课表、成绩均由 jwxt 提供；已下线的 jwgl 仅作兼容回退。
     */
    private String webvpnJwglBase = "https://http-jwxt-csuft-edu-cn-80.webvpn.csuft.edu.cn";

    /** 旧 jwgl 入口，仅在新版入口暂时不可用时回退。 */
    private String webvpnLegacyJwglBase = "https://http-jwgl-csuft-edu-cn-80.webvpn.csuft.edu.cn";

    /** 新版 jwxt 的单点登录入口位于 /jsxsd/。 */
    private String webvpnJwSsoPath = "/jsxsd/Logon.do?method=logonByZnlkd";

    /** 旧 jwgl 的单点登录入口仍在站点根路径。 */
    private String webvpnLegacyJwSsoPath = "/Logon.do?method=logonByZnlkd";

    /** 教学质量保障系统经 WebVPN 改写后的地址。 */
    private String webvpnQualityBase = "https://https-jxzlpt-csuft-edu-cn-443.webvpn.csuft.edu.cn";

    /**
     * WebVPN CAS 认证方式 ID。留空时每次新建会话从 authentication/list 动态发现，
     * 避免学校升级后 ID 从旧值切换导致 auth/start 重定向失败。
     */
    private String webvpnExternalId = "";

    /**
     * WebVPN 认证启动后的目标页，仅传给 auth/start。
     * auth/finish 的 callbackUrl 必须使用 CAS 实际回跳的 /callback/cas/{externalId}，由客户端动态提取。
     */
    private String webvpnCallbackUrl = "https://webvpn.csuft.edu.cn/site-nav/home";

    /**
     * 用户完成短信二次验证后，将当前服务器标记为 CAS 可信客户端。
     * 指纹按「服务端密钥 + 教务账号」稳定派生，不跨账号复用；可通过配置关闭。
     */
    private boolean trustAgentAfterMfa = true;

    /** 直连回退：学校融合门户 CAS 根地址。 */
    private String casBase = "https://cas.csuft.edu.cn";

    /**
     * 新版强智教务的直连地址。学校 CAS 服务注册表登记的是 HTTP 回调；不能改成 HTTPS，
     * 否则 CAS 会返回 Unauthorized Service Access。
     */
    private String jwglBase = "http://jwxt.csuft.edu.cn";

    /** 旧 jwgl 地址，供 WebVPN 重定向改写与兼容回退使用。 */
    private String legacyJwglBase = "http://jwgl.csuft.edu.cn";

    /** 新版强智 CAS 单点登录回调。 */
    private String jwSsoPath = "/jsxsd/Logon.do?method=logonByZnlkd";

    /** 直连回退：教学质量保障系统地址。 */
    private String qualityBase = "https://jxzlpt.csuft.edu.cn";

    /** 教学质量保障系统的 CAS 单点登录入口。 */
    private String qualitySsoPath = "/cas/toUrl?type=pc";

    /** 教学质量保障系统 bearer 会话缓存有效期（分钟）。 */
    private int qualitySessionTtlMinutes = 20;

    /** AES 密钥：用于加密存储学校账号密码。生产务必通过环境变量 JW_AES_KEY 覆盖。 */
    private String aesKey = "change-me-in-prod-please-32bytes!";

    /** 登录会话内存缓存有效期(分钟)。CAS/教务会话留余量取 25。 */
    private int sessionTtlMinutes = 25;

    /** 单次 HTTP 请求超时(秒) */
    private int httpTimeoutSeconds = 25;

    /** OCR 边车地址（ddddocr，POST /ocr 图片字节 → {code}）。 */
    private String ocrUrl = "http://127.0.0.1:9000";

    /** 深澜 CAS 验证码图片路径（相对 casBase）。 */
    private String captchaPath = "/cas/captcha.jpg";

    /** 验证码自动 OCR 最大重试次数；超过则转人工。 */
    private int captchaMaxOcr = 4;

    /** CAS 登录地址，携带与学校服务注册表完全一致的强智回调。 */
    public String getCasLoginUrl() {
        return trimTrailingSlash(casBase) + "/cas/login?service="
                + URLEncoder.encode(getJwServiceUrl(), StandardCharsets.UTF_8);
    }

    /** 强智教务完整 SSO 地址。 */
    public String getJwServiceUrl() {
        return trimTrailingSlash(jwglBase) + ensureLeadingSlash(jwSsoPath);
    }

    /**
     * WebVPN 下可尝试的强智入口，顺序为新版 jwxt、旧 jwgl。
     * 去重允许部署环境显式将两者指向同一地址，避免一次登录重复请求同一源站。
     */
    public List<JwEndpoint> getWebvpnJwEndpoints() {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<JwEndpoint> endpoints = new ArrayList<>();
        addEndpoint(endpoints, seen, webvpnJwglBase, jwglBase, webvpnJwSsoPath);
        addEndpoint(endpoints, seen, webvpnLegacyJwglBase, legacyJwglBase, webvpnLegacyJwSsoPath);
        return endpoints;
    }

    private static void addEndpoint(List<JwEndpoint> endpoints, LinkedHashSet<String> seen,
                                    String webvpnBase, String directBase, String ssoPath) {
        String proxy = trimTrailingSlash(webvpnBase);
        if (proxy.isBlank() || !seen.add(proxy)) return;
        endpoints.add(new JwEndpoint(proxy, trimTrailingSlash(directBase), ensureLeadingSlash(ssoPath)));
    }

    /** 单个强智入口的 WebVPN 与校内源站地址对。 */
    public record JwEndpoint(String webvpnBase, String directBase, String ssoPath) {}

    private static String trimTrailingSlash(String value) {
        if (value == null) return "";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String ensureLeadingSlash(String value) {
        if (value == null || value.isBlank()) return "/";
        return value.startsWith("/") ? value : "/" + value;
    }

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
