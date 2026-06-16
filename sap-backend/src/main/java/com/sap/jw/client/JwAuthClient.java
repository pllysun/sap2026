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
 * 4. POST /cas/mfa/detect                   校验账密 + 判断 MFA
 * 5. POST /cas/login                        提交，拿 ST-ticket（需验证码时走 OCR/人工）
 * 6. POST /api/access/auth/finish           建立 WebVPN 会话
 * 7. GET  /Logon.do?method=logonByZnlkd     CAS OAuth2 直登强智
 * </pre>
 * 验证码：深澜 CAS 在频率/失败超阈时要求验证码（图片 {@code /cas/captcha.jpg}，字段 {@code captcha}）。
 * 优先自动 OCR（ddddocr 边车）重试，用尽则抛 {@link CaptchaRequiredException} 转人工。
 */
@Component
public class JwAuthClient {

    private static final Logger log = LoggerFactory.getLogger(JwAuthClient.class);
    private static final Pattern EXECUTION = Pattern.compile("name=\"execution\" value=\"([^\"]+)\"");
    private static final Pattern TICKET = Pattern.compile("ticket=([^&\\s]+)");
    /**
     * CAS/强智 登录失败页“账号或密码错误”类文案（与“验证码错误”是不同分支）。
     * 命中即判定账密错误：立即停止验证码重试，不再无意义地刷验证码到超时。
     */
    private static final String[] PASSWORD_ERROR_HINTS = {
            "用户名或密码错误", "密码错误", "账号或密码", "帐号或密码", "用户名或者密码",
            "账户或密码", "帐户或密码", "username or password", "bad credentials"
    };

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

            // 4. mfa/detect
            JSONObject detect = JSON.parseObject(s.postForm(props.getCasBase() + "/cas/mfa/detect", Map.of(
                    "username", account, "password", pwField, "fpVisitorId", fpId)).body());
            Integer code = detect.getInteger("code");
            if (code == null || code != 0) throw new BusinessException("学校账号或密码错误");
            JSONObject dd = detect.getJSONObject("data");
            if (dd != null && Boolean.TRUE.equals(dd.getBoolean("need"))) {
                throw new BusinessException("该账号开启了二次验证(MFA)，暂不支持自动登录");
            }
            String mfaState = dd == null ? "" : dd.getString("state");

            // 5. cas/login（先不带验证码）
            PendingCas pending = new PendingCas(s, account, pwField, mfaState, fpId, execution);
            // 【临时调试】JW_FORCE_CAPTCHA=1 时强制走人工验证码分支，用真实验证码图验证闭环
            if ("1".equals(System.getenv("JW_FORCE_CAPTCHA"))) {
                byte[] img = fetchCaptcha(s);
                log.warn("[FORCE-CAPTCHA] imgBytes={} ocr='{}'", img.length, ocr.recognize(img));
                throw new CaptchaRequiredException(pending, img);
            }
            HttpResponse<String> resp = submitCas(pending, "");
            return resolve(pending, resp);
        } catch (CaptchaRequiredException | BusinessException e) {
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
            HttpResponse<String> resp = submitCas(pending, captcha);
            String ticket = ticketOf(resp);
            if (ticket != null) return finishLogin(pending.session, ticket);
            String body = resp.body();
            // 账密错误：直接终止，不再要求重输验证码
            if (isPasswordError(body)) {
                throw new BusinessException("教务账号或密码错误");
            }
            if (body != null && body.contains("验证码")) {
                refreshExecution(pending, body);
                throw new CaptchaRequiredException(pending, fetchCaptcha(pending.session));
            }
            throw new BusinessException("学校账号或密码错误，或验证码已失效");
        } catch (CaptchaRequiredException | BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("验证码续登异常 account={}", pending.account, e);
            throw new BusinessException("教务登录异常：" + e.getMessage());
        }
    }

    /** 处理 cas/login 响应：有票据则收尾；需验证码则自动 OCR 重试，用尽转人工。 */
    private JwHttpSession resolve(PendingCas p, HttpResponse<String> resp) throws Exception {
        String ticket = ticketOf(resp);
        if (ticket != null) return finishLogin(p.session, ticket);

        String body = resp.body();
        // 账密错误：立即终止，不要进入验证码重试循环（否则会一直刷验证码到超时）
        if (isPasswordError(body)) {
            throw new BusinessException("教务账号或密码错误");
        }
        if (body == null || !body.contains("验证码")) {
            throw new BusinessException("教务登录失败：未获取到票据（账密可能有误）");
        }

        for (int i = 0; i < props.getCaptchaMaxOcr(); i++) {
            refreshExecution(p, body);
            byte[] img = fetchCaptcha(p.session);
            String captcha = ocr.recognize(img);
            log.info("验证码自动识别 第{}次 account={} code='{}'", i + 1, p.account, captcha);
            resp = submitCas(p, captcha);
            ticket = ticketOf(resp);
            if (ticket != null) return finishLogin(p.session, ticket);
            body = resp.body();
            // 账密错误：立即终止重试（与“验证码错误”区分开，后者才继续 OCR 重试）
            if (isPasswordError(body)) {
                throw new BusinessException("教务账号或密码错误");
            }
            if (body == null || !body.contains("验证码")) {
                throw new BusinessException("教务登录失败：账密或验证码错误");
            }
        }
        // OCR 用尽 → 人工
        refreshExecution(p, body);
        throw new CaptchaRequiredException(p, fetchCaptcha(p.session));
    }

    private HttpResponse<String> submitCas(PendingCas p, String captcha) throws Exception {
        LinkedHashMap<String, String> form = new LinkedHashMap<>();
        form.put("username", p.account);
        form.put("password", p.pwField);
        form.put("captcha", captcha == null ? "" : captcha);
        form.put("rememberMe", "false");
        form.put("currentMenu", "");
        form.put("failN", "0");
        form.put("mfaState", p.mfaState == null ? "" : p.mfaState);
        form.put("execution", p.execution);
        form.put("_eventId", "submit");
        form.put("geolocation", "");
        form.put("fpVisitorId", p.fpId);
        form.put("trustAgent", "");
        return p.session.postForm(props.getCasBase() + "/cas/login", form);
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
