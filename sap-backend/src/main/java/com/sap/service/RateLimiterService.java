package com.sap.service;

import com.sap.config.RateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * 令牌桶限流核心。优先用 Redis（跨重启持久、可多实例共享），原子性由 Lua 保证；
 * 无 Redis（dev）或 Redis 故障时<b>降级为单实例内存限流</b>——绝不因 Redis 抖动阻断站点。
 * <p>本服务只负责"取令牌"，分类/取键/拦截响应由 {@code RateLimitInterceptor} 负责。</p>
 */
@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    /**
     * 原子令牌桶 Lua：读 tokens/ts → 按经过秒数补充(不超容量) → 够则扣 1 → 回写并设过期。
     * 返回 1=放行，0=超限拒绝。KEYS[1]=桶键；ARGV=capacity, refillPerSec, nowMillis, ttlMillis。
     */
    private static final String LUA =
            "local cap = tonumber(ARGV[1])\n" +
            "local refill = tonumber(ARGV[2])\n" +
            "local now = tonumber(ARGV[3])\n" +
            "local ttl = tonumber(ARGV[4])\n" +
            "local d = redis.call('HMGET', KEYS[1], 'tokens', 'ts')\n" +
            "local tokens = tonumber(d[1])\n" +
            "local ts = tonumber(d[2])\n" +
            "if tokens == nil then tokens = cap; ts = now end\n" +
            "local elapsed = (now - ts) / 1000.0\n" +
            "if elapsed < 0 then elapsed = 0 end\n" +
            "tokens = math.min(cap, tokens + elapsed * refill)\n" +
            "local allowed = 0\n" +
            "if tokens >= 1 then tokens = tokens - 1; allowed = 1 end\n" +
            "redis.call('HMSET', KEYS[1], 'tokens', tokens, 'ts', now)\n" +
            "redis.call('PEXPIRE', KEYS[1], ttl)\n" +
            "return allowed";

    private static final RedisScript<Long> SCRIPT = new DefaultRedisScript<>(LUA, Long.class);

    private static final long REDIS_COOLDOWN_MS = 30_000L;

    @Autowired
    private RateLimitProperties props;

    /** dev/local 无 Redis 时该 bean 不存在 → 走内存限流。 */
    @Autowired(required = false)
    private StringRedisTemplate redis;

    /** Redis 故障冷却截止(毫秒)：期间直接走内存，避免对挂掉的 Redis 反复重试。 */
    private final AtomicLong redisDownUntil = new AtomicLong(0L);

    /** 内存桶：key → [tokens, lastTsMillis]。单实例下完全正确。 */
    private final Map<String, double[]> buckets = new ConcurrentHashMap<>();

    /** 可注入时钟（测试用），默认系统时钟。 */
    private LongSupplier clock = System::currentTimeMillis;

    /**
     * 尝试为某个桶取一个令牌。
     *
     * @param key          桶键（如 rl:login:ip:1.2.3.4）
     * @param capacity     桶容量（瞬时突发上限）
     * @param refillPerSec 每秒补充令牌数（长期速率）
     * @return true=放行；false=超限。任何 Redis 异常都降级内存限流，绝不阻断。
     */
    public boolean tryAcquire(String key, int capacity, double refillPerSec) {
        if (!props.isEnabled()) return true;
        long now = clock.getAsLong();
        if (props.isUseRedis() && redis != null && now >= redisDownUntil.get()) {
            try {
                long ttlMs = Math.max(60_000L, (long) (capacity / Math.max(refillPerSec, 0.0001) * 1000.0) * 2);
                Long r = redis.execute(SCRIPT, Collections.singletonList(key),
                        String.valueOf(capacity), String.valueOf(refillPerSec),
                        String.valueOf(now), String.valueOf(ttlMs));
                if (r != null) return r == 1L;
                // r==null（脚本异常/连接问题）→ 落到内存兜底
            } catch (Exception e) {
                redisDownUntil.set(now + REDIS_COOLDOWN_MS);
                log.warn("[限流] Redis 不可用，降级内存限流 {}ms：{}", REDIS_COOLDOWN_MS, e.toString());
            }
        }
        return tryAcquireMemory(key, capacity, refillPerSec, now);
    }

    private boolean tryAcquireMemory(String key, int capacity, double refillPerSec, long now) {
        // 用 ConcurrentHashMap.compute 在桶级原子完成「读-补充-扣减-回写」，
        // 与 sweep 的 removeIf 串行化，杜绝「取到引用后被清理→重建出两个桶使限额翻倍」的竞态。
        boolean[] allowed = new boolean[1];
        buckets.compute(key, (k, b) -> {
            if (b == null) b = new double[]{capacity, now};
            double elapsed = Math.max(0, now - b[1]) / 1000.0;
            double tokens = Math.min(capacity, b[0] + elapsed * refillPerSec);
            allowed[0] = tokens >= 1;
            if (allowed[0]) tokens -= 1;
            b[0] = tokens;
            b[1] = now;
            return b;
        });
        return allowed[0];
    }

    /** 定时清理超过 5 分钟未访问的内存桶，防无界增长（重新访问会以满桶重建）。 */
    @Scheduled(fixedDelay = 600_000L)
    public void sweep() {
        long now = clock.getAsLong();
        buckets.entrySet().removeIf(e -> now - e.getValue()[1] > 300_000L);
    }
}
