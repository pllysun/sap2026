package com.sap.jw.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sap.common.BusinessException;
import com.sap.jw.config.JwProperties;
import com.sap.jw.util.RsaUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 教务登录链路客户端：深澜CAS → 强智SSO 全流程，含验证码处理。
 * <pre>
 * 1. POST /api/access/auth/start            取 CAS 登录地址
 * 2. GET  /cas/login                        取 execution
 * 3. GET  /cas/jwt/publicKey + RSA 加密密码
 * 4. POST /cas/mfa/detect                   判断是否需要 MFA（实测不校验密码，错密码也返回 code:0；真正判账密在第5步）
 * 5. POST /cas/login                        提交，拿 ST-ticket（需验证码时走 OCR/人工）
 * 6. POST /api/access/auth/finish           建立 WebVPN 会话
 * 7. GET  /Logon.do?method=logonByZnlkd     CAS OAuth2 直登强智
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
        JwHttpSession s = new JwHttpSession(props.getHttpTimeoutSeconds());
        try {
            // 1. start -> CAS 登录地址
            JSONObject startInner = new JSONObject().fluentPut("callbackUrl", props.getCallbackUrl());
            JSONObject startBody = new JSONObject()
                    .fluentPut("externalId", props.getExternalId())
                    .fluentPut("data", startInner.toJSONString());
            JSONObject start = JSON.parseObject(s.postJson(props.getWebvpnBase() + "/api/access/auth/start",
                    startBody.toJSONString()).body());
            String loginUrl = start.getJSONObject("data").getJSONObject("action").getString("login_url");

            // 2. execution
            String casPage = s.get(loginUrl).body();
            String execution = find(EXECUTION, casPage);
            if (execution == null) throw new BusinessException("教务登录失败：无法解析 CAS 登录页");

            // 3. RSA 加密密码
            String pem = s.get(props.getCasBase() + "/cas/jwt/publicKey").body();
            String pwField = "__RSA__" + RsaUtil.encryptByPublicKeyPem(pem, password);
            String fpId = "fp" + UUID.randomUUID().toString().replace("-", "");

            // 4. mfa/detect —— 仅判断是否需要二次验证；实测不校验密码（错密码也返回 code:0）。
            // 故这里的 code!=0 基本只对应账号不存在/请求异常；真正的账密判定在第 5 步 cas/login。
            JSONObject detect = JSON.parseObject(s.postForm(props.getCasBase() + "/cas/mfa/detect", Map.of(
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

            // 5. cas/login（先不带验证码）
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
            throw new BusinessException("教务登录异常：" + e.getMessage());
        }
    }

    /**
     * 人工输入验证码后继续登录。成功返回会话；验证码仍错则抛 {@link CaptchaRequiredException}（含新图）。
     */
    public JwHttpSession continueWithCaptcha(PendingCas pending, String captcha) {
        try {
            HttpResponse<String> resp = submitCas(pending, captcha, CAPTCHA_FAIL_N);
            String ticket = ticketOf(resp);
            if (ticket != null) return finishLogin(pending.session, ticket);
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
            throw new BusinessException("教务登录异常：" + e.getMessage());
        }
    }

    /**
     * 安全手机短信 MFA 初始化：GET initByType/securephone 拿 attestServerUrl/gid，并发出短信。返回掩码手机号。
     */
    private String initAndSendSms(PendingCas p) throws Exception {
        String state = p.mfaState == null ? "" : p.mfaState;
        String initUrl = props.getCasBase() + "/cas/mfa/initByType/securephone?state="
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
            throw new BusinessException("短信发送失败：" + e.getMessage());
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
            // 校验通过 → 提交 CAS 登录(携带已通过 MFA 的 mfaState)
            HttpResponse<String> resp = submitCas(p, "", 0);
            return resolve(p, resp);
        } catch (CaptchaRequiredException | BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("MFA 续登异常 account={}", p.account, e);
            throw new BusinessException("二次验证续登异常：" + e.getMessage());
        }
    }

    /** 处理 cas/login 响应：有票据则收尾；账密错误立即终止；确需验证码才自动 OCR 重试，用尽转人工。 */
    private JwHttpSession resolve(PendingCas p, HttpResponse<String> resp) throws Exception {
        String ticket = ticketOf(resp);
        if (ticket != null) return finishLogin(p.session, ticket);

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
            ticket = ticketOf(resp);
            if (ticket != null) return finishLogin(p.session, ticket);
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
        form.put("currentMenu", "");
        // 深澜仅在 failN>=captchaSkipN(默认3) 时校验验证码：首次(无码)传0让其直接判账密、错则返回401；
        // 带验证码重试时传足够大的值让其真正校验验证码。
        form.put("failN", String.valueOf(failN));
        form.put("mfaState", p.mfaState == null ? "" : p.mfaState);
        form.put("execution", p.execution);
        form.put("_eventId", "submit");
        form.put("geolocation", "");
        form.put("fpVisitorId", p.fpId);
        form.put("trustAgent", "");
        return p.session.postForm(props.getCasBase() + "/cas/login", form);
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
        String url = props.getCasBase() + props.getCaptchaPath() + "?r=" + System.currentTimeMillis();
        return s.getFollow(url, 2).body();
    }

    private String ticketOf(HttpResponse<String> resp) {
        return find(TICKET, resp.headers().firstValue("location").orElse(""));
    }

    /** 步骤 6-7：建立 WebVPN 会话并直登强智。 */
    private JwHttpSession finishLogin(JwHttpSession s, String ticket) throws Exception {
        JSONObject finInner = new JSONObject()
                .fluentPut("callbackUrl", props.getCallbackUrl())
                .fluentPut("ticket", ticket)
                .fluentPut("deviceId", "sap-app-" + UUID.randomUUID());
        JSONObject finBody = new JSONObject()
                .fluentPut("externalId", props.getExternalId())
                .fluentPut("data", finInner.toJSONString());
        JSONObject fin = JSON.parseObject(s.postJson(props.getWebvpnBase() + "/api/access/auth/finish",
                finBody.toJSONString()).body());
        if (fin.getInteger("code") == null || fin.getInteger("code") != 0) {
            throw new BusinessException("教务登录失败：WebVPN 会话建立失败");
        }
        HttpResponse<byte[]> sso = s.getFollow(props.getJwglBase() + props.getJwSsoPath(), 12);
        if (sso.statusCode() != 200 || !sso.uri().toString().contains("xsMain")) {
            throw new BusinessException("教务登录失败：强智单点登录未完成");
        }
        return s;
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
