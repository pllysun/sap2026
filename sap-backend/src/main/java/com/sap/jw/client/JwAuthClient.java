package com.sap.jw.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sap.common.BusinessException;
import com.sap.jw.config.JwProperties;
import com.sap.jw.util.JwErrorMessages;
import com.sap.jw.util.RsaUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 教务登录链路客户端：优先 WebVPN，认证入口不可用时回退直连 CAS；两条链路共用验证码/MFA 处理。
 * <pre>
 * 1. WebVPN authentication/list 动态发现 CAS externalId，auth/start 取得登录地址（失败则直连）
 * 2. GET  /cas/login?service=...            取 execution
 * 3. GET  /cas/jwt/publicKey + RSA 加密密码
 * 4. POST /cas/mfa/detect                   判断是否需要 MFA（实测不校验密码，错密码也返回 code:0）
 * 5. POST /cas/login?service=...            提交并获取 ST-ticket（需验证码时走 OCR/人工）
 * 6. WebVPN auth/finish 或直连 ticket callback，再进入强智 SSO
 * </pre>
 * 验证码：深澜 CAS 仅在客户端上报 {@code failN >= captchaSkipN}(默认3) 时才校验验证码
 * （图片 {@code /cas/captcha.jpg}，字段 {@code captcha}）。本客户端首次提交带 failN=0，
 * 故正常情况下深澜不要求验证码、错误账密会直接返回 401；仅当深澜确实判定需要验证码
 * （内嵌错误含“验证码”）才走自动 OCR（ddddocr 边车），用尽抛 {@link CaptchaRequiredException} 转人工。
 *
 * <h3>失败响应的判别（实测，非常关键）</h3>
 * 深澜 cas/login 提交后的结果只有三种、且必须靠下面信号区分（页面文案全是 {@code \\uXXXX} 转义，
 * 直接子串匹配中文永远匹配不到，这是“密码错被误报成输入验证码”的根因）：
 * <ul>
 *   <li>成功：HTTP 302，{@code Location} 带 {@code ticket=ST-...}</li>
 *   <li>账号或密码错误：HTTP 401，页面内嵌 {@code var errors = ["登录失败。"]}（统一笼统文案，不会写“密码错误”）</li>
 *   <li>需要/验证码错误：HTTP 200，页面内嵌 {@code var errors = ["图片验证码错误。"]}（仅 failN>=3 时出现）</li>
 * </ul>
 * 因此判别一律以「HTTP 状态码 + 内嵌 {@code var errors} 解码后的文案」为准。
 */
@Component
public class JwAuthClient {

    private static final Logger log = LoggerFactory.getLogger(JwAuthClient.class);
    private static final Pattern EXECUTION = Pattern.compile("name=\"execution\" value=\"([^\"]+)\"");
    private static final Pattern TICKET = Pattern.compile("ticket=([^&\\s]+)");
    /** 深澜登录页内嵌的服务端错误：{@code var errors = ["...(unicode转义)..."]}，判定失败原因的权威来源。 */
    private static final Pattern CAS_ERRORS = Pattern.compile("var errors = \\[\"([^\"]*)\"");
    private static final Pattern UNICODE_ESC = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
    /** 强智新版单点中转页由浏览器执行同源脚本跳转，HTTP 客户端需安全地复刻这一跳。 */
    private static final Pattern META_REFRESH_URL = Pattern.compile(
            "(?is)<meta[^>]+http-equiv=[\\\"']?refresh[\\\"']?[^>]+content=[\\\"'][^\\\"']*?url\\s*=\\s*([^\\\"'>\\s]+)");
    private static final Pattern CLIENT_LOCATION_URL = Pattern.compile(
            "(?is)(?:(?:window\\.)?(?:top\\.)?location(?:\\.href)?\\s*=|(?:window\\.)?(?:top\\.)?location\\.replace\\s*\\()\\s*[\\\"']([^\\\"']+)[\\\"']");
    private static final char[] WEBVPN_NAV_PLACEHOLDER_ALPHABET =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final SecureRandom WEBVPN_NAV_RANDOM = new SecureRandom();
    /** 提交验证码时携带的 failN：深澜仅在 failN>=captchaSkipN(默认3) 时才校验验证码，故置足够大值。 */
    private static final int CAPTCHA_FAIL_N = 9;
    /**
     * 账号或密码错误的兜底文案匹配（仅作 {@link #classify} 的补充信号；
     * 实测深澜统一返回笼统的“登录失败。”，主判据是 HTTP 401 + 内嵌 errors）。
     */
    private static final String[] PASSWORD_ERROR_HINTS = {
            "用户名或密码错误", "密码错误", "账号或密码", "帐号或密码", "用户名或者密码",
            "账户或密码", "帐户或密码", "登录失败", "username or password", "bad credentials"
    };
    /** cas/login 响应分类。 */
    private enum CasResult { SUCCESS, PASSWORD_ERROR, CAPTCHA, UNKNOWN }

    private record LoginBootstrap(JwHttpSession session, String execution) {}

    /** 融合门户跳转上下文；令牌仅在本次登录内存中传递，绝不写日志或持久化。 */
    private record PortalBridge(String base, String idToken) {}

    private final JwProperties props;
    private final OcrClient ocr;

    public JwAuthClient(JwProperties props, OcrClient ocr) {
        this.props = props;
        this.ocr = ocr;
    }

    /**
     * 用学校账号密码登录，返回已直登强智的会话。需验证码时自动 OCR；用尽则抛 {@link CaptchaRequiredException}。
     *
     * @throws BusinessException        账密错误 / 需要 MFA / 登录失败
     * @throws CaptchaRequiredException 自动 OCR 用尽，需人工输入验证码
     */
    public JwHttpSession login(String account, String password) {
        try {
            LoginBootstrap bootstrap = bootstrapLogin();
            JwHttpSession s = bootstrap.session();
            String execution = bootstrap.execution();

            // RSA 加密密码
            String pem = s.get(s.getCasBase() + "/cas/jwt/publicKey").body();
            String pwField = "__RSA__" + RsaUtil.encryptByPublicKeyPem(pem, password);
            // 深澜以 fpVisitorId 识别可信客户端。随机值会让每次服务端登录都像一台新设备，
            // 即使用户勾选过“设为可信客户端”也会反复触发短信。
            String fpId = stableFingerprint(account);

            // mfa/detect —— 仅判断是否需要二次验证；真正的账密判定在 cas/login。
            JSONObject detect = JSON.parseObject(s.postForm(s.getCasBase() + "/cas/mfa/detect", Map.of(
                    "username", account, "password", pwField, "fpVisitorId", fpId)).body());
            Integer code = detect.getInteger("code");
            if (code == null || code != 0) throw new BusinessException("学校账号或密码错误");
            JSONObject dd = detect.getJSONObject("data");
            String mfaState = dd == null ? "" : dd.getString("state");
            PendingCas pending = new PendingCas(s, account, pwField, mfaState, fpId, execution);

            // 二次验证(MFA)：仅支持「安全手机」短信，做成闭环(发码→用户输码→校验→继续登录)；其它方式暂不支持
            if (dd != null && Boolean.TRUE.equals(dd.getBoolean("need"))) {
                if (Boolean.TRUE.equals(dd.getBoolean("mfaTypeSecurePhone"))) {
                    throw new MfaRequiredException(pending, initAndSendSms(pending));
                }
                throw new BusinessException("该账号开启了二次验证，但暂不支持此方式，请在统一身份认证里改用短信验证或关闭");
            }

            // cas/login（先不带验证码）
            // 【临时调试】JW_FORCE_CAPTCHA=1 时强制走人工验证码分支，用真实验证码图验证闭环
            if ("1".equals(System.getenv("JW_FORCE_CAPTCHA"))) {
                byte[] img = fetchCaptcha(s);
                log.warn("[FORCE-CAPTCHA] imgBytes={} ocr='{}'", img.length, ocr.recognize(img));
                throw new CaptchaRequiredException(pending, img);
            }
            HttpResponse<String> resp = submitCas(pending, "", 0);
            return resolve(pending, resp);
        } catch (CaptchaRequiredException | MfaRequiredException | BusinessException e) {
            // MfaRequiredException 必须原样上抛：绑定路径由 JwController.bind 接住返回 {needMfa,challengeId,phone}，
            // 拉取路径由 JwSessionManager.getSession 转 JwMfaPendingException(428)。若被下面的泛化 catch 包成
            // BusinessException，会丢掉 challengeId/手机号，导致 App 只看到「教务登录异常：需要短信二次验证」而无验证码输入框。
            throw e;
        } catch (Exception e) {
            log.error("教务登录异常 account={}", account, e);
            throw new BusinessException("教务登录异常：" + JwErrorMessages.userDetail(
                    e, "学校登录流程暂时异常，请稍后重试"));
        }
    }

    /**
     * 初始化一条尚未提交账号密码的登录链路。WebVPN 的认证 ID 由接口动态发现；发现/启动/登录页
     * 读取失败时才安全回退直连 CAS，避免重复提交密码或重复触发短信。
     */
    private LoginBootstrap bootstrapLogin() throws Exception {
        if (props.isWebvpnPreferred()) {
            JwHttpSession webvpn = new JwHttpSession(props.getHttpTimeoutSeconds());
            try {
                configureWebvpnRoute(webvpn);
                String page = webvpn.get(webvpn.getCasLoginUrl()).body();
                String execution = find(EXECUTION, page);
                if (execution == null) throw new IllegalStateException("WebVPN CAS 登录页缺少 execution");
                log.info("[教务登录] 使用 WebVPN 链路 externalId={}", webvpn.getRoute().externalId());
                return new LoginBootstrap(webvpn, execution);
            } catch (Exception e) {
                log.warn("[教务登录] WebVPN 启动失败，回退直连 CAS: {}", JwErrorMessages.userDetail(
                        e, "WebVPN 暂时不可用"));
            }
        }

        JwHttpSession direct = new JwHttpSession(props.getHttpTimeoutSeconds());
        direct.configureRoute(new JwHttpSession.Route(
                false,
                trimSlash(props.getCasBase()),
                props.getCasLoginUrl(),
                trimSlash(props.getJwglBase()),
                trimSlash(props.getQualityBase()),
                "", "", "",
                trimSlash(props.getCasBase()),
                trimSlash(props.getJwglBase()),
                trimSlash(props.getQualityBase())));
        String page = direct.get(direct.getCasLoginUrl()).body();
        String execution = find(EXECUTION, page);
        if (execution == null) throw new BusinessException("教务登录失败：无法解析 CAS 登录页");
        log.info("[教务登录] 使用直连 CAS 回退链路");
        return new LoginBootstrap(direct, execution);
    }

    private void configureWebvpnRoute(JwHttpSession session) throws Exception {
        String configuredId = props.getWebvpnExternalId();
        String externalId = configuredId == null || configuredId.isBlank()
                ? discoverWebvpnExternalId(session) : configuredId;
        JSONObject start = startWebvpnAuth(session, externalId);
        if (!isSuccessfulWebvpnStart(start) && configuredId != null && !configuredId.isBlank()) {
            // 配置中的旧 ID 失效时动态刷新一次，避免学校升级后必须重新发版。
            externalId = discoverWebvpnExternalId(session);
            start = startWebvpnAuth(session, externalId);
        }
        if (!isSuccessfulWebvpnStart(start)) {
            throw new IllegalStateException(start == null ? "WebVPN auth/start 无响应"
                    : start.getString("message"));
        }
        String loginUrl = start.getJSONObject("data").getJSONObject("action").getString("login_url");
        assertExpectedHost(loginUrl, props.getWebvpnCasBase(), "/cas/login");
        JwProperties.JwEndpoint primary = props.getWebvpnJwEndpoints().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("未配置 WebVPN 教务入口"));
        session.configureRoute(new JwHttpSession.Route(
                true,
                trimSlash(props.getWebvpnCasBase()),
                loginUrl,
                primary.webvpnBase(),
                trimSlash(props.getWebvpnQualityBase()),
                trimSlash(props.getWebvpnBase()),
                externalId,
                props.getWebvpnCallbackUrl(),
                trimSlash(props.getCasBase()),
                primary.directBase(),
                trimSlash(props.getQualityBase())));
    }

    private String discoverWebvpnExternalId(JwHttpSession session) throws Exception {
        HttpResponse<String> response = session.get(trimSlash(props.getWebvpnBase())
                + "/api/access/authentication/list");
        JSONObject root = JSON.parseObject(response.body());
        JSONArray list = root == null || root.getJSONObject("data") == null
                ? null : root.getJSONObject("data").getJSONArray("list");
        if (response.statusCode() != 200 || root == null || root.getInteger("code") == null
                || root.getInteger("code") != 0 || list == null) {
            throw new IllegalStateException("WebVPN 认证方式发现失败");
        }
        for (Object item : list) {
            JSONObject auth = (JSONObject) item;
            if (auth.getInteger("authType") != null && auth.getInteger("authType") == 4
                    && auth.getString("externalId") != null && !auth.getString("externalId").isBlank()) {
                return auth.getString("externalId");
            }
        }
        throw new IllegalStateException("WebVPN 未提供 CAS 认证方式");
    }

    private JSONObject startWebvpnAuth(JwHttpSession session, String externalId) throws Exception {
        JSONObject inner = new JSONObject().fluentPut("callbackUrl", props.getWebvpnCallbackUrl());
        JSONObject body = new JSONObject()
                .fluentPut("externalId", externalId)
                .fluentPut("data", inner.toJSONString());
        HttpResponse<String> response = session.postJson(trimSlash(props.getWebvpnBase())
                + "/api/access/auth/start", body.toJSONString());
        return response.statusCode() == 200 ? JSON.parseObject(response.body()) : null;
    }

    private static boolean isSuccessfulWebvpnStart(JSONObject start) {
        return start != null && start.getInteger("code") != null && start.getInteger("code") == 0
                && start.getJSONObject("data") != null
                && start.getJSONObject("data").getJSONObject("action") != null
                && start.getJSONObject("data").getJSONObject("action").getString("login_url") != null;
    }

    private static void assertExpectedHost(String actualUrl, String expectedBase, String expectedPath) {
        URI actual = URI.create(actualUrl);
        URI expected = URI.create(expectedBase);
        if (actual.getHost() == null || expected.getHost() == null
                || !actual.getHost().equalsIgnoreCase(expected.getHost())
                || actual.getPath() == null || !actual.getPath().startsWith(expectedPath)) {
            throw new IllegalStateException("WebVPN 返回了无效的 CAS 登录地址");
        }
    }

    /**
     * 人工输入验证码后继续登录。成功返回会话；验证码仍错则抛 {@link CaptchaRequiredException}（含新图）。
     */
    public JwHttpSession continueWithCaptcha(PendingCas pending, String captcha) {
        try {
            HttpResponse<String> resp = submitCas(pending, captcha, CAPTCHA_FAIL_N);
            String ticketRedirect = ticketRedirectOf(pending.session, resp);
            if (ticketRedirect != null) return finishLogin(pending, ticketRedirect);
            String body = resp.body();
            switch (classify(resp.statusCode(), body)) {
                case PASSWORD_ERROR:
                    // 账密错误：直接终止，不再要求重输验证码
                    throw new BusinessException("教务账号或密码错误");
                case CAPTCHA:
                    // 验证码错误：换一张图让用户重输
                    refreshExecution(pending, body);
                    throw new CaptchaRequiredException(pending, fetchCaptcha(pending.session));
                default:
                    throw new BusinessException("学校账号或密码错误，或验证码已失效");
            }
        } catch (CaptchaRequiredException | BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("验证码续登异常 account={}", pending.account, e);
            throw new BusinessException("教务登录异常：" + JwErrorMessages.userDetail(
                    e, "验证码登录流程暂时异常，请稍后重试"));
        }
    }

    /**
     * 安全手机短信 MFA 初始化：GET initByType/securephone 拿 attestServerUrl/gid，并发出短信。返回掩码手机号。
     */
    private String initAndSendSms(PendingCas p) throws Exception {
        String state = p.mfaState == null ? "" : p.mfaState;
        String initUrl = p.session.getCasBase() + "/cas/mfa/initByType/securephone?state="
                + java.net.URLEncoder.encode(state, java.nio.charset.StandardCharsets.UTF_8);
        JSONObject init = JSON.parseObject(p.session.get(initUrl).body());
        if (init == null || init.getInteger("code") == null || init.getInteger("code") != 0
                || init.getJSONObject("data") == null) {
            throw new BusinessException("二次验证初始化失败，请稍后重试");
        }
        JSONObject d = init.getJSONObject("data");
        p.attestServerUrl = d.getString("attestServerUrl");
        p.gid = d.getString("gid");
        sendSms(p);
        return d.getString("securePhone");
    }

    /** 发送/重发安全手机短信验证码（需先 init 拿到 attestServerUrl/gid）。 */
    public void sendSms(PendingCas p) {
        if (p.attestServerUrl == null || p.gid == null) {
            throw new BusinessException("二次验证会话已失效，请重新绑定");
        }
        try {
            JSONObject body = new JSONObject().fluentPut("gid", p.gid);
            JSONObject r = JSON.parseObject(
                    p.session.postJson(p.attestServerUrl + "/api/guard/securephone/send", body.toJSONString()).body());
            if (r == null || r.getInteger("code") == null || r.getInteger("code") != 0) {
                throw new BusinessException("短信发送失败，请稍后重试");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MFA 短信发送异常 account={}", p.account, e);
            throw new BusinessException("短信发送失败：" + JwErrorMessages.userDetail(
                    e, "短信服务暂时异常，请稍后重试"));
        }
    }

    /** 用户输入短信验证码后校验；通过(status==2)则继续 CAS 登录拿票据并直登强智。 */
    public JwHttpSession continueWithMfa(PendingCas p, String code) {
        try {
            if (p.attestServerUrl == null || p.gid == null) {
                throw new BusinessException("二次验证会话已失效，请重新绑定");
            }
            JSONObject body = new JSONObject()
                    .fluentPut("gid", p.gid)
                    .fluentPut("code", code == null ? "" : code.trim());
            JSONObject r = JSON.parseObject(
                    p.session.postJson(p.attestServerUrl + "/api/guard/securephone/valid", body.toJSONString()).body());
            Integer rc = r == null ? null : r.getInteger("code");
            JSONObject rd = r == null ? null : r.getJSONObject("data");
            Integer status = rd == null ? null : rd.getInteger("status");
            if (rc == null || rc != 0 || status == null || status != 2) {
                throw new BusinessException("短信验证码错误或已失效，请重新输入");
            }
            // 只有用户实际通过短信验证后才申请可信；普通密码登录不会擅自改变该状态。
            p.trustAgent = props.isTrustAgentAfterMfa();
            // 校验通过 → 提交 CAS 登录(携带已通过 MFA 的 mfaState)
            HttpResponse<String> resp = submitCas(p, "", 0);
            return resolve(p, resp);
        } catch (CaptchaRequiredException | BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MFA 续登异常 account={}", p.account, e);
            throw new BusinessException("二次验证续登异常：" + JwErrorMessages.userDetail(
                    e, "二次验证流程暂时异常，请稍后重试"));
        }
    }

    /** 处理 cas/login 响应：有票据则收尾；账密错误立即终止；确需验证码才自动 OCR 重试，用尽转人工。 */
    private JwHttpSession resolve(PendingCas p, HttpResponse<String> resp) throws Exception {
        String ticketRedirect = ticketRedirectOf(p.session, resp);
        if (ticketRedirect != null) return finishLogin(p, ticketRedirect);

        String body = resp.body();
        switch (classify(resp.statusCode(), body)) {
            case PASSWORD_ERROR:
                // 账密错误：立即终止，绝不进入验证码重试循环（否则会一直刷验证码到超时→误报“输入验证码”）
                throw new BusinessException("教务账号或密码错误");
            case UNKNOWN:
                throw new BusinessException("教务登录失败：未获取到票据（账密可能有误）");
            default:
                break; // CAPTCHA → 落到下面 OCR 重试
        }

        for (int i = 0; i < props.getCaptchaMaxOcr(); i++) {
            refreshExecution(p, body);
            byte[] img = fetchCaptcha(p.session);
            String captcha = ocr.recognize(img);
            log.info("验证码自动识别 第{}次 account={} code='{}'", i + 1, p.account, captcha);
            resp = submitCas(p, captcha, CAPTCHA_FAIL_N);
            ticketRedirect = ticketRedirectOf(p.session, resp);
            if (ticketRedirect != null) return finishLogin(p, ticketRedirect);
            body = resp.body();
            switch (classify(resp.statusCode(), body)) {
                case PASSWORD_ERROR:
                    // 验证码这次对了但仍失败 → 账密错误，立即终止重试
                    throw new BusinessException("教务账号或密码错误");
                case UNKNOWN:
                    throw new BusinessException("教务登录失败：账密或验证码错误");
                default:
                    break; // 仍是 CAPTCHA → 继续下一次 OCR
            }
        }
        // OCR 用尽 → 人工
        refreshExecution(p, body);
        throw new CaptchaRequiredException(p, fetchCaptcha(p.session));
    }

    private HttpResponse<String> submitCas(PendingCas p, String captcha, int failN) throws Exception {
        LinkedHashMap<String, String> form = new LinkedHashMap<>();
        form.put("username", p.account);
        form.put("password", p.pwField);
        form.put("captcha", captcha == null ? "" : captcha);
        form.put("rememberMe", "false");
        form.put("currentMenu", "1");
        // 深澜仅在 failN>=captchaSkipN(默认3) 时校验验证码：首次(无码)传0让其直接判账密、错则返回401；
        // 带验证码重试时传足够大的值让其真正校验验证码。
        form.put("failN", String.valueOf(failN));
        form.put("mfaState", p.mfaState == null ? "" : p.mfaState);
        form.put("execution", p.execution);
        form.put("_eventId", "submit");
        form.put("geolocation", "");
        form.put("fpVisitorId", p.fpId);
        form.put("trustAgent", p.trustAgent ? "true" : "");
        return p.session.postForm(p.session.getCasLoginUrl(), form);
    }

    /**
     * 判别 cas/login 失败响应（无票据时调用）。
     * 主判据：内嵌 {@code var errors} 解码后的文案 + HTTP 状态码。
     * <ul>
     *   <li>errors 含“验证码” → {@link CasResult#CAPTCHA}</li>
     *   <li>HTTP 401 / 命中账密兜底文案 / errors 非空(深澜统一笼统“登录失败。”) → {@link CasResult#PASSWORD_ERROR}</li>
     *   <li>其它 → {@link CasResult#UNKNOWN}</li>
     * </ul>
     */
    private static CasResult classify(int status, String body) {
        String err = extractCasError(body);
        if (err != null && err.contains("验证码")) return CasResult.CAPTCHA;
        // 深澜对错误账密统一返回 401 + 笼统的“登录失败。”（不会明写“密码错误”）；
        // 兜底文案只匹配解码后的简短 err，不扫整页(整页静态模板含“登录失败”会误判)。
        if (status == 401 || isPasswordError(err) || (err != null && !err.isBlank())) {
            return CasResult.PASSWORD_ERROR;
        }
        return CasResult.UNKNOWN;
    }

    /** 解析并解码深澜登录页内嵌的服务端错误文案（{@code var errors=["..."]}）。无则返回 null。 */
    private static String extractCasError(String body) {
        String raw = find(CAS_ERRORS, body);
        return raw == null ? null : decodeUnicodeEscapes(raw);
    }

    /** 将 {@code \\uXXXX} 转义还原为字符（深澜页面里中文均为转义形式）。 */
    private static String decodeUnicodeEscapes(String s) {
        if (s == null || s.indexOf("\\u") < 0) return s;
        Matcher m = UNICODE_ESC.matcher(s);
        StringBuilder b = new StringBuilder();
        int last = 0;
        while (m.find()) {
            b.append(s, last, m.start());
            b.append((char) Integer.parseInt(m.group(1), 16));
            last = m.end();
        }
        return b.append(s.substring(last)).toString();
    }

    private void refreshExecution(PendingCas p, String body) {
        String ex = find(EXECUTION, body);
        if (ex != null) p.execution = ex;
    }

    private byte[] fetchCaptcha(JwHttpSession s) throws Exception {
        String url = s.getCasBase() + props.getCaptchaPath() + "?r=" + System.currentTimeMillis();
        return s.getFollow(url, 2).body();
    }

    private String ticketRedirectOf(JwHttpSession session, HttpResponse<String> resp) {
        String location = resp.headers().firstValue("location").orElse("");
        if (find(TICKET, location) == null) return null;
        return URI.create(session.getCasLoginUrl()).resolve(location).toString();
    }

    /** 用 CAS 返回的 ST-ticket 建立 WebVPN/直连会话，再进入强智。 */
    private JwHttpSession finishLogin(PendingCas pending, String ticketRedirect) throws Exception {
        JwHttpSession s = pending.session;
        if (s.isWebvpn()) {
            String ticket = find(TICKET, ticketRedirect);
            if (ticket == null) throw new BusinessException("教务登录失败：CAS 未返回票据");
            JwHttpSession.Route route = s.getRoute();
            String finishCallbackUrl = webvpnFinishCallbackUrl(ticketRedirect, route);
            JSONObject inner = new JSONObject()
                    .fluentPut("callbackUrl", finishCallbackUrl)
                    .fluentPut("ticket", ticket)
                    // WebVPN 当前前端使用 FingerprintJS visitorId 作为 deviceId；它必须与 CAS
                    // mfa/detect/login 的 fpVisitorId 保持一致。随机 UUID 会导致换票返回成功，
                    // 但门户会话无法继续用于强智资源单点登录。
                    .fluentPut("deviceId", pending.fpId);
            JSONObject body = new JSONObject()
                    .fluentPut("externalId", route.externalId())
                    .fluentPut("data", inner.toJSONString());
            HttpResponse<String> response = s.postJson(route.webvpnBase()
                    + "/api/access/auth/finish", body.toJSONString());
            JSONObject finish = response.statusCode() == 200 ? JSON.parseObject(response.body()) : null;
            if (finish == null || finish.getInteger("code") == null || finish.getInteger("code") != 0) {
                throw new BusinessException("教务登录失败：WebVPN 换票失败");
            }
            // WebVPN 浏览器回调在 auth/finish 后会读取用户信息；这一步既验证门户会话确已建立，
            // 也避免仅凭 finish code=0 就误判登录成功。
            HttpResponse<String> userResponse = s.get(route.webvpnBase() + "/api/access/user/info");
            JSONObject userRoot = userResponse.statusCode() == 200
                    ? JSON.parseObject(userResponse.body()) : null;
            JSONObject userData = userRoot == null ? null : userRoot.getJSONObject("data");
            if (userRoot == null || userRoot.getInteger("code") == null || userRoot.getInteger("code") != 0
                    || userData == null || userData.getLong("userId") == null
                    || userData.getLong("userId") <= 0) {
                log.warn("[教务登录] WebVPN 换票后用户会话无效 finishCode={} userHttp={} userCode={} cookies={}",
                        finish.getInteger("code"), userResponse.statusCode(),
                        userRoot == null ? null : userRoot.getInteger("code"), s.cookieSummary());
                throw new BusinessException("教务登录失败：WebVPN 门户会话未建立");
            }
            // 浏览器不会从 WebVPN 根页直接打开新版强智，而是先进入融合门户，再由门户
            // 新开教务系统。该桥接会建立资源侧需要的单点登录上下文，省略它会被返回登录页。
            PortalBridge portalBridge = openWebvpnPortalBridge(s, route);
            // JDK 不会把 WebVPN 的父域 Version=1 Cookie 发给多级代理主机。新版门户除
            // webvpn-token 外还可能使用路由 Cookie；按浏览器语义为目标主机建立精确域副本。
            s.mirrorPortalCookiesToHost(route.webvpnBase(), s.getQualityBase());
            return completeWebvpnJwSso(s, route, portalBridge);
        }

        URI target = URI.create(ticketRedirect);
        URI expected = URI.create(s.getJwglBase());
        if (target.getHost() == null || expected.getHost() == null
                || !target.getHost().equalsIgnoreCase(expected.getHost())) {
            throw new BusinessException("教务登录失败：CAS 返回了无效的教务回调地址");
        }
        HttpResponse<byte[]> sso = s.getFollow(target.toString(), 12);
        if (!isAuthenticatedJwResponse(sso, s.getJwglBase())) {
            throw new BusinessException("教务登录失败：强智单点登录未完成");
        }
        return s;
    }

    /**
     * 完成融合门户桥接并返回浏览器实际会发送给教务页的 Referer（严格同源策略下仅保留 origin）。
     *
     * <p>这里不读取或持久化门户页面数据；只加载公开入口以接收 WebVPN 为该代理主机下发的
     * 会话 Cookie。门户暂时不可达时保留原有兼容入口继续尝试，避免把单一页面故障扩大成登录失败。</p>
     */
    private PortalBridge openWebvpnPortalBridge(JwHttpSession session, JwHttpSession.Route rootRoute) {
        String portalBase = trimSlash(props.getWebvpnPortalBase());
        if (portalBase.isBlank()) return new PortalBridge(rootRoute.webvpnBase(), null);
        try {
            URI portal = URI.create(portalBase);
            URI root = URI.create(rootRoute.webvpnBase());
            boolean schoolProxyHost = portal.getHost() != null
                    && portal.getHost().toLowerCase(java.util.Locale.ROOT).endsWith(".webvpn.csuft.edu.cn");
            // 相同主机仅用于私有部署/本地契约测试（端口可不同）；生产默认仍限制为 WebVPN 子域。
            boolean sameConfiguredHost = portal.getHost() != null && root.getHost() != null
                    && portal.getHost().equalsIgnoreCase(root.getHost());
            if (portal.getScheme() == null || portal.getHost() == null || root.getHost() == null
                    || (!schoolProxyHost && !sameConfiguredHost)) {
                throw new IllegalArgumentException("融合门户地址不属于学校 WebVPN");
            }
            session.mirrorPortalCookiesToHost(rootRoute.webvpnBase(), portalBase);
            // 必须经 WebVPN 网站导航打开融合门户。导航项会先申请 imufe code，再由 WebVPN
            // 跳转到携带 portal ticket 的地址；直接请求 main.html 只能取得匿名门户壳页面。
            HttpResponse<byte[]> response = openWebvpnPortalNavigationEntry(session, rootRoute.webvpnBase(), portalBase);
            if (response == null) response = session.getFollow(portalBase + "/main.html#/Index", 8);
            // 若 WebVPN 导航未带 portal ticket，融合门户前端会使用已有 CAS TGC 对门户本身
            // 进行一次无交互单点登录。后端复刻该回跳，避免把匿名服务列表误当成已登录状态。
            if (portalIdTokenFromTicket(response == null ? null : response.uri()) == null) {
                HttpResponse<byte[]> casResponse = openPortalCasSso(session, rootRoute, portalBase);
                if (casResponse != null && casResponse.statusCode() < 400) response = casResponse;
            }
            if (response == null || response.statusCode() >= 400) {
                log.warn("[教务登录] 融合门户桥接响应异常 status={} uri={}",
                        response == null ? null : response.statusCode(),
                        response == null ? null : safeUri(response.uri()));
            } else {
                String idToken = portalIdTokenFromTicket(response.uri());
                log.info("[教务登录] 已完成融合门户桥接 status={} ticketPresent={} tokenReady={}",
                        response.statusCode(), hasQueryParameter(response.uri(), "ticket"), idToken != null);
                return new PortalBridge(portalBase, idToken);
            }
        } catch (Exception e) {
            log.warn("[教务登录] 融合门户桥接不可用，继续兼容入口: {}",
                    JwErrorMessages.userDetail(e, "请求失败"));
        }
        // 实测融合门户在 HTTPS 同级跳转时会向强智保留 main.html（fragment 不会出现在 Referer）。
        return new PortalBridge(portalBase, null);
    }

    /** 复刻融合门户未持有 token 时跳转 CAS 的 service 参数格式。 */
    private HttpResponse<byte[]> openPortalCasSso(JwHttpSession session, JwHttpSession.Route route, String portalBase) {
        try {
            String portalPage = trimSlash(portalBase) + "/main.html#/Index";
            String service = trimSlash(portalBase) + "/?path="
                    + URLEncoder.encode(portalPage, StandardCharsets.UTF_8);
            String url = trimSlash(route.casBase()) + "/cas/login?service="
                    + URLEncoder.encode(service, StandardCharsets.UTF_8);
            return session.getFollow(url, 12);
        } catch (Exception e) {
            log.info("[教务登录] 融合门户 CAS 单点登录不可用 reason={}",
                    JwErrorMessages.userDetail(e, "请求失败"));
            return null;
        }
    }

    private HttpResponse<byte[]> openWebvpnPortalNavigationEntry(JwHttpSession session,
                                                                   String webvpnBase,
                                                                   String portalBase) {
        try {
            HttpResponse<String> response = session.get(trimSlash(webvpnBase) + "/api/access/nav/site-list");
            JSONObject root = response.statusCode() == 200 ? JSON.parseObject(response.body()) : null;
            Object data = root == null ? null : root.get("data");
            if (root == null || root.getInteger("code") == null || root.getInteger("code") != 0) return null;
            java.util.List<String> urls = new java.util.ArrayList<>();
            collectNavigationUrls(data, portalBase, urls);
            log.info("[教务登录] WebVPN 融合门户导航项已匹配 count={}", urls.size());
            for (String url : urls) {
                String target = materializeWebvpnNavigationUrl(session, webvpnBase, url);
                if (target == null) continue;
                HttpResponse<byte[]> entry = session.getFollow(target, 12);
                if (entry != null && entry.statusCode() < 400) return entry;
            }
        } catch (Exception e) {
            log.info("[教务登录] WebVPN 融合门户导航不可用 reason={}",
                    JwErrorMessages.userDetail(e, "请求失败"));
        }
        return null;
    }

    /** 与融合门户 window.open 教务卡片时等价的导航头。 */
    private static Map<String, String> navigationHeaders(String portalBase) {
        if (portalBase == null || portalBase.isBlank()) return Map.of();
        return Map.ofEntries(
                Map.entry("Referer", trimSlash(portalBase) + "/main.html"),
                Map.entry("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8"),
                Map.entry("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8"),
                Map.entry("Upgrade-Insecure-Requests", "1"),
                Map.entry("Sec-CH-UA", "\"Chromium\";v=\"124\", \"Not-A.Brand\";v=\"99\""),
                Map.entry("Sec-CH-UA-Mobile", "?0"),
                Map.entry("Sec-CH-UA-Platform", "\"Windows\""),
                Map.entry("Sec-Fetch-Dest", "document"),
                Map.entry("Sec-Fetch-Mode", "navigate"),
                Map.entry("Sec-Fetch-Site", "cross-site"),
                Map.entry("Sec-Fetch-User", "?1"));
    }

    /**
     * 使用已完成的 WebVPN 门户会话登录当前 jwxt，不再尝试旧教务源站。
     */
    private JwHttpSession completeWebvpnJwSso(JwHttpSession s, JwHttpSession.Route portalRoute,
                                              PortalBridge portalBridge) throws Exception {
        String portalReferrer = portalBridge.base();
        HttpResponse<byte[]> last = null;
        String lastSsoUrl = null;
        for (JwProperties.JwEndpoint endpoint : props.getWebvpnJwEndpoints()) {
            int mirrored = s.mirrorPortalCookiesToHost(portalRoute.webvpnBase(), endpoint.webvpnBase());
            mirrored += s.mirrorPortalCookiesToHost(portalReferrer, endpoint.webvpnBase());
            if (mirrored == 0) {
                throw new BusinessException("教务登录失败：WebVPN 门户票据缺失");
            }
            configureJwEndpoint(s, endpoint);
            // WebVPN 根导航只包含“融合门户”，新版教务系统卡片实际由融合门户的服务列表下发。
            // 先按门户 API 提供的资源 URL 打开，避免只尝试过时的 Logon.do。
            HttpResponse<byte[]> portalServiceEntry = openWebvpnPortalServiceEntry(s, portalBridge, endpoint);
            if (isAuthenticatedJwResponse(portalServiceEntry, s.getJwglBase())) {
                log.info("[教务登录] 通过融合门户服务入口完成强智 SSO endpoint={}", s.getJwglBase());
                return s;
            }
            // WebVPN 导航页中的资源链接可能携带 ${CODE} 占位符。浏览器前端会先申请
            // imufe code，再带 code 打开该链接；直接猜 Logon.do 会被新版强智返回登录页。
            HttpResponse<byte[]> navEntry = openWebvpnNavigationEntry(s, portalRoute.webvpnBase(), endpoint,
                    portalReferrer);
            if (isAuthenticatedJwResponse(navEntry, s.getJwglBase())) {
                log.info("[教务登录] 通过 WebVPN 导航资源入口完成强智 SSO endpoint={}", s.getJwglBase());
                return s;
            }
            for (String path : webvpnEntryPaths(endpoint)) {
                String jwSsoUrl = s.getJwglBase() + path;
                try {
                    HttpResponse<byte[]> sso = s.getFollow(jwSsoUrl, 12, navigationHeaders(portalReferrer));
                    // WebVPN 首次资源请求有时会先回门户根页；与浏览器前端一致地再进入原目标。
                    String returnUrl = trustedWebvpnReturnUrl(sso, portalRoute.webvpnBase(), jwSsoUrl);
                    if (returnUrl != null) sso = s.getFollow(returnUrl, 12, navigationHeaders(portalReferrer));
                    sso = followTrustedClientRedirect(s, sso, s.getJwglBase(),
                            navigationHeaders(portalReferrer), s.getCasBase());
                    if (isAuthenticatedJwResponse(sso, s.getJwglBase())) {
                        log.info("[教务登录] WebVPN 强智 SSO 完成 endpoint={} path={}",
                                s.getJwglBase(), path);
                        return s;
                    }
                    last = sso;
                    lastSsoUrl = jwSsoUrl;
                    log.warn("[教务登录] 当前强智入口未建立会话 endpoint={} path={} status={} uri={}",
                            endpoint.webvpnBase(), path, sso == null ? null : sso.statusCode(),
                            sso == null ? null : safeUri(sso.uri()));
                } catch (Exception e) {
                    lastSsoUrl = jwSsoUrl;
                    log.warn("[教务登录] 当前强智入口不可用 endpoint={} path={} reason={}",
                            endpoint.webvpnBase(), path, JwErrorMessages.userDetail(e, "请求失败"));
                }
            }
        }
        log.warn("[教务登录] WebVPN 强智 SSO 未完成 status={} uri={} cookies={} targetCookies={} portalCookies={}",
                last == null ? null : last.statusCode(), last == null ? null : safeUri(last.uri()), s.cookieSummary(),
                lastSsoUrl == null ? "[]" : s.requestCookieSummary(lastSsoUrl),
                s.requestCookieSummary(portalRoute.webvpnBase()));
        throw new BusinessException("教务登录失败：WebVPN 强智单点登录未完成");
    }

    /**
     * 打开融合门户下发的服务入口。
     *
     * <p>学校新版路径为 WebVPN → 融合门户 → 教务系统。WebVPN 的 site-list 只会列出前两级，
     * 教务系统卡片由 portal-api 返回；浏览器点击卡片时只是导航到该卡片的 serviceUrl。这里不读取
     * 用户资料或持久化门户数据，只请求当前已认证用户可见的服务清单并严格限制目标代理主机。</p>
     */
    private HttpResponse<byte[]> openWebvpnPortalServiceEntry(JwHttpSession session,
                                                               PortalBridge portalBridge,
                                                               JwProperties.JwEndpoint endpoint) {
        String portal = trimSlash(portalBridge.base());
        if (portal.isBlank()) return null;
        try {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Referer", portal + "/main.html");
            headers.put("Accept", "application/json, text/plain, */*");
            headers.put("Accept-Language", "zh-CN,zh;q=0.9");
            headers.put("X-Device-Info", "PC");
            headers.put("X-Terminal-Info", "PC");
            if (portalBridge.idToken() != null) headers.put("X-Id-Token", portalBridge.idToken());
            // “常用系统”位于最近使用列表，教务系统卡片即由此渲染；推荐服务仅作为
            // 新用户尚无最近使用记录时的兼容回退。按身份分发的系统列表也可能承载该卡片，
            // 因学校门户会按校内角色动态配置首页模块。
            java.util.List<String> apis = java.util.List.of(
                    portal + "/portal-api/v2/service/getListByIdentify?serviceType=4&keyWord=",
                    portal + "/portal-api/v1/service/lastUse/list?serviceType=1,2,3,4,5",
                    portal + "/portal-api/v1/service/recommend/month/list?showPublic=false&type=1,2,3,4,5");
            for (String api : apis) {
                HttpResponse<String> response = session.get(api, headers);
                JSONObject root = response.statusCode() == 200 ? JSON.parseObject(response.body()) : null;
                Object data = root == null ? null : root.get("data");
                if (root == null || root.getInteger("code") == null || root.getInteger("code") != 0) {
                    log.info("[教务登录] 融合门户服务清单不可用 endpoint={} status={} code={}",
                            endpoint.webvpnBase(), response.statusCode(), root == null ? null : root.getInteger("code"));
                    continue;
                }
                java.util.List<String> urls = new java.util.ArrayList<>();
                collectNavigationUrls(data, endpoint.webvpnBase(), urls);
                log.info("[教务登录] 融合门户服务项已匹配 endpoint={} count={}", endpoint.webvpnBase(), urls.size());
                if (urls.isEmpty()) {
                    log.info("[教务登录] 融合门户服务项结构 endpoint={} keys={}", endpoint.webvpnBase(),
                            jsonKeySummary(data, 24));
                }
                for (String url : urls) {
                    URI serviceUri = URI.create(url);
                    log.info("[教务登录] 打开融合门户服务入口 uri={} queryKeys={}", safeUri(serviceUri),
                            queryParameterNames(serviceUri));
                    HttpResponse<byte[]> result = session.getFollow(url, 12, navigationHeaders(portal));
                    result = followTrustedClientRedirect(session, result, session.getJwglBase(),
                            navigationHeaders(portal), session.getCasBase());
                    if (isAuthenticatedJwResponse(result, session.getJwglBase())) return result;
                }
            }
        } catch (Exception e) {
            log.info("[教务登录] 融合门户服务入口不可用 endpoint={} reason={}", endpoint.webvpnBase(),
                    JwErrorMessages.userDetail(e, "请求失败"));
        }
        return null;
    }

    /**
     * 复刻 WebVPN 网站导航的资源打开逻辑。
     *
     * <p>新版门户为部分校内系统生成一次性 {@code code}，并写入导航配置 URL 的
     * {@code ${CODE}} 占位符。该步骤不是强智的账号密码登录；它只使用已经完成的门户会话。
     * 找不到导航项时返回 {@code null}，由后续历史入口兼容逻辑继续处理。</p>
     */
    private HttpResponse<byte[]> openWebvpnNavigationEntry(JwHttpSession session,
                                                             String portalBase,
                                                             JwProperties.JwEndpoint endpoint,
                                                             String portalReferrer) {
        try {
            HttpResponse<String> response = session.get(trimSlash(portalBase) + "/api/access/nav/site-list");
            JSONObject root = response.statusCode() == 200 ? JSON.parseObject(response.body()) : null;
            JSONObject data = root == null ? null : root.getJSONObject("data");
            if (root == null || root.getInteger("code") == null || root.getInteger("code") != 0 || data == null) {
                return null;
            }
            java.util.List<String> urls = new java.util.ArrayList<>();
            collectNavigationUrls(data, endpoint.webvpnBase(), urls);
            log.info("[教务登录] WebVPN 导航项已匹配 endpoint={} count={}", endpoint.webvpnBase(), urls.size());
            for (String url : urls) {
                String target = url;
                if (target.contains("${CODE}")) {
                    target = materializeWebvpnNavigationUrl(session, portalBase, target);
                    if (target == null) continue;
                }
                HttpResponse<byte[]> result = session.getFollow(target, 12, navigationHeaders(portalReferrer));
                result = followTrustedClientRedirect(session, result, session.getJwglBase(),
                        navigationHeaders(portalReferrer), session.getCasBase());
                if (isAuthenticatedJwResponse(result, session.getJwglBase())) return result;
            }
        } catch (Exception e) {
            log.info("[教务登录] WebVPN 导航资源入口不可用 endpoint={} reason={}", endpoint.webvpnBase(),
                    JwErrorMessages.userDetail(e, "请求失败"));
        }
        return null;
    }

    /** 将 WebVPN 导航配置中的 ${CODE} 按前端真实规则实体化，不记录一次性 code。 */
    private static String materializeWebvpnNavigationUrl(JwHttpSession session, String webvpnBase, String url)
            throws Exception {
        if (url == null || url.isBlank()) return null;
        if (!url.contains("${CODE}")) return url;
        HttpResponse<String> codeResponse = session.post(trimSlash(webvpnBase) + "/api/access/imufe/code", Map.of());
        JSONObject codeRoot = codeResponse.statusCode() == 200 ? JSON.parseObject(codeResponse.body()) : null;
        String code = codeRoot == null || codeRoot.getJSONObject("data") == null
                ? null : codeRoot.getJSONObject("data").getString("code");
        if (code == null || code.isBlank()) return null;
        // WebVPN 前端不是把 ${CODE} 直接替换为一次性 code：它先写入六位随机占位值，再通过
        // URLSearchParams 单独设置 code 查询参数。两者合并会改变导航链接原始参数的语义。
        String target = url.replace("${CODE}", webvpnNavigationPlaceholder());
        return replaceOrAppendQueryParameter(target, "code", code);
    }

    /** 与 WebVPN 导航页 je(6) 对齐：仅用于保留原始 URL 占位参数的形状。 */
    private static String webvpnNavigationPlaceholder() {
        char[] value = new char[6];
        for (int i = 0; i < value.length; i++) {
            value[i] = WEBVPN_NAV_PLACEHOLDER_ALPHABET[WEBVPN_NAV_RANDOM.nextInt(WEBVPN_NAV_PLACEHOLDER_ALPHABET.length)];
        }
        return new String(value);
    }

    /**
     * 等价于浏览器的 {@code new URL(url).searchParams.set(name, value)}；不记录或持久化 code。
     */
    private static String replaceOrAppendQueryParameter(String url, String name, String value) throws Exception {
        URI uri = URI.create(url);
        java.util.List<String> pairs = new java.util.ArrayList<>();
        boolean replaced = false;
        String query = uri.getRawQuery();
        if (query != null && !query.isBlank()) {
            for (String item : query.split("&", -1)) {
                int split = item.indexOf('=');
                String rawName = split < 0 ? item : item.substring(0, split);
                String decodedName = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
                if (name.equals(decodedName)) {
                    if (!replaced) {
                        pairs.add(URLEncoder.encode(name, StandardCharsets.UTF_8) + "="
                                + URLEncoder.encode(value, StandardCharsets.UTF_8));
                        replaced = true;
                    }
                } else {
                    pairs.add(item);
                }
            }
        }
        if (!replaced) {
            pairs.add(URLEncoder.encode(name, StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
        return new URI(uri.getScheme(), uri.getRawAuthority(), uri.getRawPath(),
                String.join("&", pairs), uri.getRawFragment()).toString();
    }

    /**
     * 融合门户前端会解码 URL ticket 的 JWT payload，并读取其中的 idToken 作为 X-Id-Token。
     * 这里只返回该字段本身；票据、负载和令牌不写入日志或数据库。
     */
    private static String portalIdTokenFromTicket(URI uri) {
        String ticket = queryParameter(uri, "ticket");
        if (ticket == null || ticket.isBlank()) return null;
        try {
            String[] parts = ticket.split("\\.");
            if (parts.length < 2) return null;
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64Url(parts[1]));
            JSONObject payload = JSON.parseObject(new String(decoded, StandardCharsets.UTF_8));
            String token = payload == null ? null : payload.getString("idToken");
            return token == null || token.isBlank() ? null : token;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean hasQueryParameter(URI uri, String name) {
        return queryParameter(uri, name) != null;
    }

    private static String queryParameter(URI uri, String name) {
        if (uri == null || uri.getRawQuery() == null || name == null) return null;
        for (String pair : uri.getRawQuery().split("&", -1)) {
            int split = pair.indexOf('=');
            String rawName = split < 0 ? pair : pair.substring(0, split);
            if (!name.equals(URLDecoder.decode(rawName, StandardCharsets.UTF_8))) continue;
            return split < 0 ? "" : URLDecoder.decode(pair.substring(split + 1), StandardCharsets.UTF_8);
        }
        return null;
    }

    private static String padBase64Url(String value) {
        int missing = (4 - value.length() % 4) % 4;
        return value + "=".repeat(missing);
    }

    /** 仅从门户导航响应中收集目标代理主机的 URL，绝不跟随任意外部链接。 */
    private static void collectNavigationUrls(Object node, String endpointBase, java.util.List<String> urls) {
        if (node instanceof JSONObject object) {
            for (String key : java.util.List.of("url", "rawURL", "serviceUrl", "service_url")) {
                String value = object.getString(key);
                if (isExpectedWebvpnResourceUrl(value, endpointBase) && !urls.contains(value)) urls.add(value);
            }
            for (Object value : object.values()) collectNavigationUrls(value, endpointBase, urls);
        } else if (node instanceof JSONArray array) {
            for (Object value : array) collectNavigationUrls(value, endpointBase, urls);
        }
    }

    /** 仅用于兼容诊断：记录 JSON 字段名，绝不记录服务名、用户数据或字段值。 */
    private static java.util.List<String> jsonKeySummary(Object node, int limit) {
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        collectJsonKeys(node, keys, limit);
        return new java.util.ArrayList<>(keys);
    }

    private static void collectJsonKeys(Object node, java.util.Set<String> keys, int limit) {
        if (node instanceof JSONObject object) {
            for (String key : object.keySet()) {
                if (keys.size() >= limit) return;
                keys.add(key);
            }
            for (Object value : object.values()) {
                if (keys.size() >= limit) return;
                collectJsonKeys(value, keys, limit);
            }
        } else if (node instanceof JSONArray array) {
            for (Object value : array) {
                if (keys.size() >= limit) return;
                collectJsonKeys(value, keys, limit);
            }
        }
    }

    /** 仅记录 URL 的 origin、path 与查询参数名，避免日志泄露票据或一次性 code。 */
    private static java.util.List<String> jsonUrlShapeSummary(Object node, int limit) {
        java.util.LinkedHashSet<String> shapes = new java.util.LinkedHashSet<>();
        collectJsonUrlShapes(node, shapes, limit);
        return new java.util.ArrayList<>(shapes);
    }

    private static void collectJsonUrlShapes(Object node, java.util.Set<String> shapes, int limit) {
        if (node instanceof JSONObject object) {
            for (Object value : object.values()) {
                if (shapes.size() >= limit) return;
                collectJsonUrlShapes(value, shapes, limit);
            }
        } else if (node instanceof JSONArray array) {
            for (Object value : array) {
                if (shapes.size() >= limit) return;
                collectJsonUrlShapes(value, shapes, limit);
            }
        } else if (node instanceof String value) {
            try {
                URI uri = URI.create(value);
                if (uri.getScheme() == null || uri.getHost() == null) return;
                java.util.List<String> queryNames = uri.getRawQuery() == null ? java.util.List.of()
                        : java.util.Arrays.stream(uri.getRawQuery().split("&", -1))
                        .map(pair -> pair.substring(0, Math.max(0, pair.indexOf('='))))
                        .map(name -> URLDecoder.decode(name, StandardCharsets.UTF_8))
                        .distinct().sorted().toList();
                shapes.add(uri.getScheme() + "://" + uri.getHost()
                        + (uri.getPort() < 0 ? "" : ":" + uri.getPort())
                        + (uri.getPath() == null ? "" : uri.getPath()) + "?" + queryNames);
            } catch (Exception ignored) {
                // 普通文本、加密字段或非 URL 字符串不纳入诊断。
            }
        }
    }

    private static boolean isExpectedWebvpnResourceUrl(String value, String endpointBase) {
        if (value == null || value.isBlank()) return false;
        try {
            // ${CODE} 是 WebVPN 前端支持的占位符，不是合法 URI 字符；只为做同源校验时
            // 以普通文字替代，实际请求前仍会由 openWebvpnNavigationEntry 写入一次性 code。
            URI actual = URI.create(value.replace("${CODE}", "placeholder"));
            URI expected = URI.create(endpointBase);
            return actual.getScheme() != null && actual.getHost() != null && expected.getHost() != null
                    && actual.getScheme().equalsIgnoreCase(expected.getScheme())
                    && actual.getHost().equalsIgnoreCase(expected.getHost())
                    && actual.getPort() == expected.getPort();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    /**
     * 当前 jwxt 的门户导航链接直达 xsMainV；同时保留配置中的新版 CAS 回调。
     */
    private static String[] webvpnEntryPaths(JwProperties.JwEndpoint endpoint) {
        String ssoPath = endpoint.ssoPath();
        if (endpoint.webvpnBase().contains("-jwxt-")) {
            return new String[]{"/jsxsd/framework/xsMainV.htmlx", ssoPath};
        }
        return new String[]{ssoPath};
    }

    private static void configureJwEndpoint(JwHttpSession session, JwProperties.JwEndpoint endpoint) {
        JwHttpSession.Route current = session.getRoute();
        session.configureRoute(new JwHttpSession.Route(
                current.webvpn(), current.casBase(), current.casLoginUrl(), endpoint.webvpnBase(),
                current.qualityBase(), current.webvpnBase(), current.externalId(), current.callbackUrl(),
                current.directCasBase(), endpoint.directBase(), current.directQualityBase()));
    }

    /**
     * WebVPN 前端会把 CAS 实际回跳页（去掉 query）传给 auth/finish，而不是 auth/start 的目标页。
     * 两者混用时 auth/finish 虽可能返回 code=0，但不会建立可供后续资源单点登录使用的门户会话。
     */
    private static String webvpnFinishCallbackUrl(String ticketRedirect, JwHttpSession.Route route) throws Exception {
        URI callback = URI.create(ticketRedirect);
        URI webvpn = URI.create(route.webvpnBase());
        URI configuredCallback = URI.create(route.callbackUrl());
        String casCallbackPath = "/callback/cas/" + route.externalId();
        boolean allowedPath = casCallbackPath.equals(callback.getPath())
                || sameOrigin(webvpn, configuredCallback)
                && configuredCallback.getPath() != null
                && configuredCallback.getPath().equals(callback.getPath());
        if (callback.getScheme() == null || webvpn.getScheme() == null
                || !callback.getScheme().equalsIgnoreCase(webvpn.getScheme())
                || callback.getHost() == null || webvpn.getHost() == null
                || !callback.getHost().equalsIgnoreCase(webvpn.getHost())
                || callback.getPort() != webvpn.getPort()
                || !allowedPath) {
            // 仅记录不含 ticket/query 的地址要素，便于学校升级后安全定位协议变化。
            log.warn("[教务登录] WebVPN CAS 回调校验失败 actual={}://{}:{}{} expectedOrigin={}://{}:{} allowedPaths=[{},{}]",
                    callback.getScheme(), callback.getHost(), callback.getPort(), callback.getPath(),
                    webvpn.getScheme(), webvpn.getHost(), webvpn.getPort(), casCallbackPath,
                    configuredCallback.getPath());
            throw new BusinessException("教务登录失败：WebVPN CAS 返回了无效的回调地址");
        }
        return new URI(callback.getScheme(), null, callback.getHost(), callback.getPort(),
                callback.getPath(), null, null).toString();
    }

    private static boolean sameOrigin(URI first, URI second) {
        return first.getScheme() != null && second.getScheme() != null
                && first.getScheme().equalsIgnoreCase(second.getScheme())
                && first.getHost() != null && second.getHost() != null
                && first.getHost().equalsIgnoreCase(second.getHost())
                && first.getPort() == second.getPort();
    }

    /** 日志中的地址只保留源与路径，剔除 CAS/强智一次性票据所在的 query 和 fragment。 */
    private static String safeUri(URI uri) {
        if (uri == null) return null;
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null)
                    .toString();
        } catch (Exception ignored) {
            return uri.getPath();
        }
    }

    /**
     * 提取 WebVPN 门户根页的受信任 returnUrl。只接受与本次请求完全相同的目标，
     * 防止把学校返回的任意地址当作跳转目标而形成 SSRF。
     */
    private static String trustedWebvpnReturnUrl(HttpResponse<byte[]> response,
                                                  String webvpnBase,
                                                  String expectedTarget) {
        if (response == null || response.uri() == null) return null;
        URI actual = response.uri();
        URI portal = URI.create(webvpnBase);
        String path = actual.getPath();
        if (!sameOrigin(actual, portal) || path != null && !path.isBlank() && !"/".equals(path)) {
            return null;
        }
        String rawQuery = actual.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) return null;
        for (String pair : rawQuery.split("&")) {
            int split = pair.indexOf('=');
            String rawName = split < 0 ? pair : pair.substring(0, split);
            if (!"returnUrl".equals(URLDecoder.decode(rawName, StandardCharsets.UTF_8))) continue;
            String value = split < 0 ? "" : URLDecoder.decode(pair.substring(split + 1), StandardCharsets.UTF_8);
            return expectedTarget.equals(value) ? value : null;
        }
        return null;
    }

    /**
     * 跟随新版强智 SSO 中转页中的客户端跳转。只接受解析后与目标教务代理同源的地址，
     * 不执行脚本、不接受外部主机，避免把页面内容变成任意请求跳板。
     */
    private static HttpResponse<byte[]> followTrustedClientRedirect(JwHttpSession session,
                                                                      HttpResponse<byte[]> response,
                                                                      String expectedBase,
                                                                      Map<String, String> headers,
                                                                      String... additionalBases) {
        HttpResponse<byte[]> current = response;
        for (int i = 0; i < 4; i++) {
            if (current == null || current.statusCode() != 200 || current.uri() == null
                    || current.body() == null || current.body().length == 0) return current;
            String candidate = trustedClientSideRedirect(current, expectedBase, additionalBases);
            if (candidate == null) return current;
            try {
                URI target = URI.create(candidate);
                log.info("[教务登录] 跟随强智中转页客户端跳转 uri={} queryKeys={}", safeUri(target),
                        queryParameterNames(target));
                // window.location 导航会把中转页作为 Referer 发送；沿用门户 Referer 会被新版
                // 强智网关当成非浏览器链路，从而重新返回登录页。
                Map<String, String> redirectHeaders = new LinkedHashMap<>(headers == null ? Map.of() : headers);
                redirectHeaders.put("Referer", current.uri().toString());
                HttpResponse<byte[]> next = session.getFollow(candidate, 12, redirectHeaders);
                if (next == null || next.uri() == null || next.uri().equals(current.uri())) return next;
                current = next;
            } catch (Exception e) {
                log.info("[教务登录] 强智中转页跳转失败 reason={}", JwErrorMessages.userDetail(e, "请求失败"));
                return current;
            }
        }
        return current;
    }

    private static String trustedClientSideRedirect(HttpResponse<byte[]> response, String expectedBase,
                                                    String... additionalBases) {
        if (expectedBase == null || expectedBase.isBlank()) return null;
        String page = new String(response.body(), StandardCharsets.UTF_8);
        String raw = null;
        Matcher meta = META_REFRESH_URL.matcher(page);
        if (meta.find()) raw = meta.group(1);
        if (raw == null || raw.isBlank()) {
            Matcher script = CLIENT_LOCATION_URL.matcher(page);
            if (script.find()) raw = script.group(1);
        }
        if (raw == null || raw.isBlank()) return null;
        try {
            URI resolved = response.uri().resolve(htmlUnescape(raw.trim()));
            if (sameOrigin(resolved, URI.create(expectedBase))) return resolved.toString();
            if (additionalBases != null) {
                for (String base : additionalBases) {
                    if (base != null && !base.isBlank() && sameOrigin(resolved, URI.create(base))) {
                        return resolved.toString();
                    }
                }
            }
            return null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String htmlUnescape(String value) {
        return value.replace("&amp;", "&").replace("&#x3D;", "=").replace("&#61;", "=");
    }

    private static java.util.List<String> queryParameterNames(URI uri) {
        if (uri == null || uri.getRawQuery() == null || uri.getRawQuery().isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(uri.getRawQuery().split("&", -1))
                .map(pair -> pair.substring(0, Math.max(0, pair.indexOf('='))))
                .map(name -> URLDecoder.decode(name, StandardCharsets.UTF_8))
                .distinct().sorted().toList();
    }

    /**
     * 验证强智会话是否真正建立。
     * <p>新版强智在未登录时同样会以 {@code 200} 返回，地址也保留在 {@code /jsxsd/}；
     * 不能再仅靠 URL 前缀判断，否则会把 LoginToXk 登录页当成已登录页并缓存坏会话。</p>
     */
    private static boolean isAuthenticatedJwResponse(HttpResponse<byte[]> response, String expectedBase) {
        if (response == null || response.statusCode() != 200) return false;
        URI actual = response.uri();
        URI expected = URI.create(expectedBase);
        if (actual.getHost() == null || expected.getHost() == null
                || !actual.getHost().equalsIgnoreCase(expected.getHost())
                || actual.getPort() != expected.getPort()
                || actual.getPath() == null || !actual.getPath().startsWith("/jsxsd/")) {
            return false;
        }

        String page = new String(response.body(), StandardCharsets.UTF_8);
        String lower = page.toLowerCase(java.util.Locale.ROOT);
        // 2026 新版登录页的稳定标识；旧版登录页也常保留 loginForm。
        String formAction = htmlFormAction(page).toLowerCase(java.util.Locale.ROOT);
        if (formAction.contains("/jsxsd/xk/logintoxk") || lower.contains("name=\"loginform\"")
                && !lower.contains("退出系统") && !lower.contains("注销")) {
            log.info("[教务登录] 强智页面仍为登录页 uri={} title={} bytes={} formAction={} scripts={}",
                    safeUri(actual), htmlTitle(page), response.body().length, htmlFormAction(page),
                    htmlScriptPaths(page));
            return false;
        }
        // 成功页会落到新版 xsMainV、旧版 xsMain 或其它已登录的 /jsxsd/ 页面。
        boolean authenticated = actual.getPath().toLowerCase(java.util.Locale.ROOT).contains("xsmain")
                || lower.contains("xsmain") || lower.contains("退出系统") || lower.contains("注销")
                || lower.contains("个人中心") || lower.contains("学生个人中心");
        if (!authenticated) {
            log.info("[教务登录] 强智页面未识别为已登录 uri={} title={} bytes={} markers=[xsmain:{},logout:{}]",
                    safeUri(actual), htmlTitle(page), response.body().length,
                    lower.contains("xsmain"), lower.contains("退出系统") || lower.contains("注销"));
        }
        return authenticated;
    }

    /** 仅提取页面标题用于诊断，不记录页面正文、账号或任意票据。 */
    private static String htmlTitle(String page) {
        if (page == null) return "";
        Matcher title = Pattern.compile("(?is)<title[^>]*>\\s*(.*?)\\s*</title>").matcher(page);
        if (!title.find()) return "";
        return title.group(1).replaceAll("\\s+", " ").trim().substring(0,
                Math.min(80, title.group(1).replaceAll("\\s+", " ").trim().length()));
    }

    private static String htmlFormAction(String page) {
        if (page == null) return "";
        Matcher form = Pattern.compile("(?is)<form[^>]*\\baction=[\\\"']([^\\\"']+)").matcher(page);
        return form.find() ? safePath(form.group(1)) : "";
    }

    private static java.util.List<String> htmlScriptPaths(String page) {
        if (page == null) return java.util.List.of();
        Matcher scripts = Pattern.compile("(?is)<script[^>]*\\bsrc=[\\\"']([^\\\"']+)").matcher(page);
        java.util.List<String> result = new java.util.ArrayList<>();
        while (scripts.find() && result.size() < 8) result.add(safePath(scripts.group(1)));
        return result;
    }

    /** 去除 script/form URL 中可能携带的 code、ticket 等 query，仅保留路径。 */
    private static String safePath(String value) {
        try {
            URI uri = URI.create(value);
            return uri.getPath() == null ? "" : uri.getPath();
        } catch (Exception ignored) {
            int query = value == null ? -1 : value.indexOf('?');
            return query < 0 ? value : value.substring(0, query);
        }
    }

    /**
     * 为每个教务账号稳定派生 FingerprintJS 同形的 20 位伪匿名设备指纹；不保存、也不暴露原账号。
     * FingerprintJS 的 visitorId 固定为 20 位十六进制，WebVPN 会把它关联到资源侧单点登录上下文。
     */
    private String stableFingerprint(String account) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(props.getAesKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(("sap-jw-trusted-agent:" + (account == null ? "" : account))
                .getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest).substring(0, 20);
    }

    private static String trimSlash(String value) {
        if (value == null || value.isBlank()) return "";
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String find(Pattern p, String s) {
        if (s == null) return null;
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }

    /** 登录响应是否为“账号或密码错误”页（大小写无关、包含匹配）。 */
    private static boolean isPasswordError(String body) {
        if (body == null || body.isEmpty()) return false;
        String lower = body.toLowerCase();
        for (String hint : PASSWORD_ERROR_HINTS) {
            if (lower.contains(hint.toLowerCase())) return true;
        }
        return false;
    }
}
