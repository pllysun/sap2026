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

    // 从首次请求开始计窗，拒绝请求不会延长锁定。计数与过期在同一脚本中完成。
    private static final RedisScript<Long> WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local count = tonumber(redis.call('GET', KEYS[1]) or '0')
            if count >= tonumber(ARGV[1]) then return 0 end
            count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
            return 1
            """, Long.class);

    private static final long REDIS_COOLDOWN_MS = 30_000L;

    @Autowired
    private RateLimitProperties props;

    /** dev/local 无 Redis 时该 bean 不存在 → 走内存限流。 */
    @Autowired(required = false)
    private StringRedisTemplate redis;

    /** Redis 故障冷却截止(毫秒)：期间直接走内存，避免对挂掉的 Redis 反复重试。 */
    private final AtomicLong redisDownUntil = new AtomicLong(0L);

    private record Bucket(double tokens, long touchedAt, double reclaimAt) {}
    /** 不可变桶值，清理时若有新请求更新了该桶，ConcurrentHashMap 不会误删新值。 */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    private record Window(int count, long expiresAt) {}
    private final Map<String, Window> windows = new java.util.HashMap<>();
    private static final int MAX_MEMORY_WINDOWS = 100_000;

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
        return tryAcquireEnforced(key, capacity, refillPerSec);
    }

    /** 注册策略由数据库开关控制，不被旧的全局 YAML 开关意外关闭。 */
    public boolean tryAcquireEnforced(String key, int capacity, double refillPerSec) {
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

    /** 固定时长窗口硬上限，用于注册间隔和小时/日额度；不受令牌桶的空闲清理影响。 */
    public boolean tryAcquireWindow(String key, int limit, int windowSeconds) {
        if (limit < 1 || windowSeconds < 1) throw new IllegalArgumentException("窗口与额度必须为正数");
        if (!props.isEnabled()) return true;
        return tryAcquireWindowEnforced(key, limit, windowSeconds);
    }

    public boolean tryAcquireWindowEnforced(String key, int limit, int windowSeconds) {
        if (limit < 1 || windowSeconds < 1) throw new IllegalArgumentException("窗口与额度必须为正数");
        long now = clock.getAsLong();
        if (props.isUseRedis() && redis != null && now >= redisDownUntil.get()) {
            try {
                Long result = redis.execute(WINDOW_SCRIPT, Collections.singletonList(key),
                        String.valueOf(limit), String.valueOf(windowSeconds * 1000L));
                if (result != null) return result == 1L;
            } catch (Exception e) {
                log.warn("[注册限流] Redis 不可用，降级为本实例限流：{}", e.toString());
            }
            redisDownUntil.set(now + REDIS_COOLDOWN_MS);
        }
        synchronized (windows) {
            Window window = windows.get(key);
            if (window == null || window.expiresAt() <= now) {
                if (window == null && windows.size() >= MAX_MEMORY_WINDOWS) {
                    windows.values().removeIf(w -> w.expiresAt() <= now);
                    if (windows.size() >= MAX_MEMORY_WINDOWS) return false;
                }
                windows.put(key, new Window(1, now + windowSeconds * 1000L));
                return true;
            }
            if (window.count() >= limit) return false;
            windows.put(key, new Window(window.count() + 1, window.expiresAt()));
            return true;
        }
    }

    private boolean tryAcquireMemory(String key, int capacity, double refillPerSec, long now) {
        // 在桶级原子完成「读-补充-扣减-回写」，每次返回新值以避免清理删除刚更新的桶。
        boolean[] allowed = new boolean[1];
        buckets.compute(key, (k, b) -> {
            double elapsed = b == null ? 0 : Math.max(0, now - b.touchedAt()) / 1000.0;
            double tokens = b == null ? capacity : Math.min(capacity, b.tokens() + elapsed * refillPerSec);
            allowed[0] = tokens >= 1;
            if (allowed[0]) tokens -= 1;
            // 动态配置允许低恢复速率：尚未补满的桶不能因空闲五分钟就恢复全部额度。
            double fullAt = refillPerSec > 0 ? now + Math.ceil((capacity - tokens) * 1000 / refillPerSec)
                    : Double.POSITIVE_INFINITY;
            return new Bucket(tokens, now, Math.max(now + 300_000L, fullAt));
        });
        return allowed[0];
    }

    /** 仅回收已补满且空闲五分钟的桶，重新访问时满桶重建不会增加额度。 */
    @Scheduled(fixedDelay = 600_000L)
    public void sweep() {
        long now = clock.getAsLong();
        buckets.entrySet().removeIf(e -> now >= e.getValue().reclaimAt());
        synchronized (windows) {
            windows.values().removeIf(w -> w.expiresAt() <= now);
        }
    }
}
