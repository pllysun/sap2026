package com.sap.service;

import com.wf.captcha.SpecCaptcha;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 注册风控验证码（单实例内存存储，无 Redis 依赖；进程重启即重置，对风控可接受）。
 * <p><b>默认不弹验证码</b>：仅当某 IP 在窗口内注册数超过宽松阈值 {@code free-limit} 后，
 * 才对该 IP 后续注册要求验证码——正常用户零打扰，批量养号脚本越过阈值后被验证码挡住。</p>
 * <p>校园网 NAT 下大量学生共用一个出口 IP，故阈值给得宽松；越过阈值也只是多一步图形验证码，
 * 真人可解、脚本难解。应急可用 {@code app.register.captcha-enabled=false} 一键关闭。</p>
 */
@Service
public class CaptchaService {

    /** 总开关：关闭后任何注册都不要求验证码（应急回滚用）。 */
    @Value("${app.register.captcha-enabled:true}")
    private boolean enabled;

    /** 单 IP 在窗口内可「免验证码」注册的次数（宽松，默认 5）。超过后该 IP 后续注册才要验证码。 */
    @Value("${app.register.free-limit:5}")
    private int freeLimit;

    /** 风控计数窗口(小时，默认 24)。窗口过后该 IP 计数清零。 */
    @Value("${app.register.window-hours:24}")
    private int windowHours;

    /** 验证码答案有效期(秒，默认 180)。 */
    @Value("${app.register.captcha-ttl-seconds:180}")
    private int captchaTtlSeconds;

    private record Captcha(String answer, long expireAt) {}
    private record IpStat(int count, long resetAt) {}

    private final Map<String, Captcha> captchas = new ConcurrentHashMap<>();
    private final Map<String, IpStat> ipStats = new ConcurrentHashMap<>();

    /** 生成图形验证码，返回 {captchaId, image(base64 dataURL)}；答案存内存(一次性、带过期)。 */
    public Map<String, Object> generate() {
        SpecCaptcha captcha = new SpecCaptcha(120, 40, 4);
        String answer = captcha.text();
        String id = UUID.randomUUID().toString().replace("-", "");
        captchas.put(id, new Captcha(answer, System.currentTimeMillis() + captchaTtlSeconds * 1000L));
        Map<String, Object> data = new HashMap<>();
        data.put("captchaId", id);
        data.put("image", captcha.toBase64());
        return data;
    }

    /** 校验并消费验证码（取出即删，防重放）；不区分大小写。 */
    public boolean verify(String id, String input) {
        if (id == null || input == null || input.isBlank()) return false;
        Captcha c = captchas.remove(id);
        return c != null && c.expireAt() > System.currentTimeMillis()
                && c.answer().equalsIgnoreCase(input.trim());
    }

    /** 该 IP 当前注册是否需要验证码（默认否；仅窗口内注册数超过宽松阈值才为是）。 */
    public boolean captchaRequired(String ip) {
        if (!enabled || ip == null) return false;
        IpStat s = ipStats.get(ip);
        if (s == null || s.resetAt() <= System.currentTimeMillis()) return false;
        return s.count() >= freeLimit;
    }

    /** 注册成功后对该 IP 计数 +1（驱动风控阈值；窗口过期则重新计窗）。 */
    public void recordRegister(String ip) {
        if (ip == null) return;
        long now = System.currentTimeMillis();
        long reset = now + windowHours * 3600_000L;
        ipStats.compute(ip, (k, old) ->
                (old == null || old.resetAt() <= now)
                        ? new IpStat(1, reset)
                        : new IpStat(old.count() + 1, old.resetAt()));
    }

    /** 定时清理过期项，防内存无界增长。 */
    @Scheduled(fixedDelay = 300_000L)
    public void sweep() {
        long now = System.currentTimeMillis();
        captchas.entrySet().removeIf(e -> e.getValue().expireAt() <= now);
        ipStats.entrySet().removeIf(e -> e.getValue().resetAt() <= now);
    }
}
