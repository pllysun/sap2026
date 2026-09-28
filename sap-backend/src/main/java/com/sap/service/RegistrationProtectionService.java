package com.sap.service;

import com.sap.common.BusinessException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/** 注册前预占额度，不等待数据库写入成功，避免并发请求一起穿过检查。 */
@Service
public class RegistrationProtectionService {
    private final RateLimiterService limiter;
    private final RegistrationProtectionSettingsService settings;
    // 只保存执行中的请求；本实例最多 maxConcurrent 项，在 finally 中释放。
    private final Map<String, Integer> inFlightIps = new HashMap<>();
    private int inFlight;

    public RegistrationProtectionService(RateLimiterService limiter, RegistrationProtectionSettingsService settings) {
        this.limiter = limiter;
        this.settings = settings;
    }

    public Permit acquire(String ip, String qq) {
        var policy = settings.current().quotas();
        if (!policy.enabled()) return new Permit(null, false);
        synchronized (inFlightIps) {
            if (inFlightIps.getOrDefault(ip, 0) >= policy.maxConcurrentPerIp()) {
                throw limited("当前网络有注册正在处理，请稍后再试");
            }
            if (inFlight >= policy.maxConcurrent()) {
                throw limited("注册人数较多，请稍后再试");
            }
            inFlightIps.merge(ip, 1, Integer::sum);
            inFlight++;
        }
        Permit permit = new Permit(ip, true);
        try {
            // 每条规则在 Redis / 内存中原子扣减。后续检查失败不退还，限制重复失败重试。
            check("ip:cooldown:" + ip, 1, policy.cooldownSeconds(), "注册操作过于频繁，请稍后再试");
            check("ip:hour:" + ip, policy.ipHourlyLimit(), 3600, "当前网络本小时注册次数过多，请稍后再试");
            check("ip:day:" + ip, policy.ipDailyLimit(), 86400, "当前网络24小时内注册次数过多，请稍后再试");
            check("qq:day:" + qq, policy.qqDailyLimit(), 86400, "该QQ号24小时内注册次数过多，请稍后再试");
            check("global:minute", policy.globalMinuteLimit(), 60, "注册人数较多，请稍后再试");
            return permit;
        } catch (RuntimeException e) {
            permit.close();
            throw e;
        }
    }

    private void check(String key, int limit, int seconds, String message) {
        if (!limiter.tryAcquireWindowEnforced("rl:registration:" + key, limit, seconds)) {
            throw limited(message);
        }
    }

    private BusinessException limited(String message) {
        return new BusinessException(429, message);
    }

    public final class Permit implements AutoCloseable {
        private final String ip;
        private final boolean counted;
        private boolean closed;

        private Permit(String ip, boolean counted) {
            this.ip = ip;
            this.counted = counted;
        }

        @Override
        public void close() {
            synchronized (inFlightIps) {
                if (!closed) {
                    if (counted) {
                        inFlightIps.computeIfPresent(ip, (key, count) -> count == 1 ? null : count - 1);
                        inFlight--;
                    }
                    closed = true;
                }
            }
        }
    }
}
