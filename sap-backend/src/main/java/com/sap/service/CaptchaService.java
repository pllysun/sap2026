package com.sap.service;

import com.wf.captcha.SpecCaptcha;
import com.sap.common.BusinessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 一次性图形验证码。默认每次注册均需验证，答案仅存本实例内存并绑定来源 IP。
 * 多实例部署须使用粘性会话；进程重启后旧图片失效，客户端重新获取即可。
 */
@Service
public class CaptchaService {

    private final RegistrationProtectionSettingsService settings;

    public CaptchaService(RegistrationProtectionSettingsService settings) {
        this.settings = settings;
    }

    // 已签发的验证码保留签发时的有效期及最短作答时间，避免保存配置导致正在填写的图片失效。
    private record Captcha(String answer, String ip, long verifyAfter, long expireAt) {}
    private record IpStat(int count, long resetAt) {}

    private final Map<String, Captcha> captchas = new ConcurrentHashMap<>();
    private final Map<String, IpStat> ipStats = new ConcurrentHashMap<>();
    private LongSupplier clock = System::currentTimeMillis;
    private static final int MAX_ENTRIES = 10_000;

    /** 生成图形验证码，返回 {captchaId, image(base64 dataURL)}；答案存内存(一次性、带过期)。 */
    public Map<String, Object> generate(String ip) {
        var policy = settings.current().captcha();
        SpecCaptcha captcha = new SpecCaptcha(160, 50, policy.length());
        String answer = captcha.text();
        String image = captcha.toBase64();
        String id = UUID.randomUUID().toString().replace("-", "");
        long now = clock.getAsLong();
        synchronized (captchas) {
            if (captchas.size() >= MAX_ENTRIES) {
                captchas.values().removeIf(c -> c.expireAt() <= now);
                if (captchas.size() >= MAX_ENTRIES) throw new BusinessException(429, "验证码请求较多，请稍后再试");
            }
            captchas.put(id, new Captcha(answer, ip, now + policy.minSolveSeconds() * 1000L,
                    now + policy.ttlSeconds() * 1000L));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("captchaId", id);
        data.put("image", image);
        return data;
    }

    /** 校验并消费验证码（取出即删，防重放）；不区分大小写。 */
    public boolean verify(String id, String input, String ip) {
        if (id == null) return false;
        Captcha c = captchas.remove(id);
        long now = clock.getAsLong();
        return c != null && input != null && !input.isBlank() && ip != null && ip.equals(c.ip())
                && c.expireAt() > now && now >= c.verifyAfter()
                && c.answer().equalsIgnoreCase(input.trim());
    }

    /** 原子决定本次请求是否需验证，同时预占可选豁免，不能在注册成功后才计数。 */
    public boolean requiresCaptchaForAttempt(String ip) {
        var policy = settings.current().captcha();
        if (!policy.enabled()) return false;
        if (policy.freeLimit() <= 0 || ip == null) return true;
        long now = clock.getAsLong();
        synchronized (ipStats) {
            IpStat stat = ipStats.get(ip);
            if (stat == null || stat.resetAt() <= now) {
                if (stat == null && ipStats.size() >= MAX_ENTRIES) {
                    ipStats.values().removeIf(s -> s.resetAt() <= now);
                    if (ipStats.size() >= MAX_ENTRIES) return true;
                }
                stat = new IpStat(0, now + policy.freeWindowHours() * 3600_000L);
            }
            if (stat.count() >= policy.freeLimit()) return true;
            ipStats.put(ip, new IpStat(stat.count() + 1, stat.resetAt()));
            return false;
        }
    }

    /** 定时清理过期项，防内存无界增长。 */
    @Scheduled(fixedDelay = 300_000L)
    public void sweep() {
        long now = clock.getAsLong();
        captchas.entrySet().removeIf(e -> e.getValue().expireAt() <= now);
        synchronized (ipStats) {
            ipStats.entrySet().removeIf(e -> e.getValue().resetAt() <= now);
        }
    }
}
