package com.sap.service;

import com.sap.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 令牌桶限流核心单测：内存桶补充/上限、Redis 放行拒绝、null/异常降级、冷却、清理、总开关。 */
class RateLimiterServiceTest {

    private RateLimiterService svc;
    private RateLimitProperties props;
    private long[] now;

    @BeforeEach
    void setup() {
        svc = new RateLimiterService();
        props = new RateLimitProperties();
        props.setEnabled(true);
        props.setUseRedis(false); // 默认走内存路径
        ReflectionTestUtils.setField(svc, "props", props);
        now = new long[]{1_000_000L};
        LongSupplier clk = () -> now[0];
        ReflectionTestUtils.setField(svc, "clock", clk);
    }

    private void advance(long ms) { now[0] += ms; }

    @SuppressWarnings("unchecked")
    private StringRedisTemplate wireRedisMock() {
        props.setUseRedis(true);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ReflectionTestUtils.setField(svc, "redis", redis);
        return redis;
    }

    @Test
    void 总开关关闭则恒放行() {
        props.setEnabled(false);
        for (int i = 0; i < 100; i++) assertTrue(svc.tryAcquire("k", 1, 0.0));
    }

    @Test
    void 内存桶_扣空后按时间补充() {
        assertTrue(svc.tryAcquire("k", 3, 1.0));
        assertTrue(svc.tryAcquire("k", 3, 1.0));
        assertTrue(svc.tryAcquire("k", 3, 1.0));
        assertFalse(svc.tryAcquire("k", 3, 1.0)); // 桶空
        advance(1000);                            // 过 1 秒补 1 个
        assertTrue(svc.tryAcquire("k", 3, 1.0));
        assertFalse(svc.tryAcquire("k", 3, 1.0));
    }

    @Test
    void 内存桶_补充不超过容量() {
        svc.tryAcquire("k", 2, 1.0);
        svc.tryAcquire("k", 2, 1.0);
        assertFalse(svc.tryAcquire("k", 2, 1.0));
        advance(100_000); // 等很久
        assertTrue(svc.tryAcquire("k", 2, 1.0));
        assertTrue(svc.tryAcquire("k", 2, 1.0));
        assertFalse(svc.tryAcquire("k", 2, 1.0)); // 仍只补到容量 2
    }

    @Test
    void redis路径_放行与拒绝() {
        StringRedisTemplate redis = wireRedisMock();
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenReturn(1L, 0L);
        assertTrue(svc.tryAcquire("k", 5, 1.0));
        assertFalse(svc.tryAcquire("k", 5, 1.0));
    }

    @Test
    void redis返回null_降级内存() {
        StringRedisTemplate redis = wireRedisMock();
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any())).thenReturn(null);
        assertTrue(svc.tryAcquire("k", 1, 0.0));  // 降级内存：容量1 第一次放行
        assertFalse(svc.tryAcquire("k", 1, 0.0)); // 第二次拒
    }

    @Test
    void redis异常_降级内存且进入冷却不再重试() {
        StringRedisTemplate redis = wireRedisMock();
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("conn refused"));
        assertTrue(svc.tryAcquire("k", 1, 0.0)); // 异常→降级内存(放行)
        long downUntil = ((AtomicLong) ReflectionTestUtils.getField(svc, "redisDownUntil")).get();
        assertTrue(downUntil > now[0], "应设置冷却截止");
        assertFalse(svc.tryAcquire("k", 1, 0.0)); // 冷却期内直接内存(桶空→拒)
        verify(redis, times(1)).execute(any(RedisScript.class), anyList(), any(), any(), any(), any());
    }

    @Test
    void redis冷却到期后重新探测() {
        StringRedisTemplate redis = wireRedisMock();
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("down")) // 首次失败→进入冷却
                .thenReturn(1L);                          // 冷却后恢复
        assertTrue(svc.tryAcquire("k", 1, 0.0)); // 触发失败、降级内存
        advance(31_000);                          // 越过 30s 冷却
        assertTrue(svc.tryAcquire("k", 1, 0.0)); // 重新探测 Redis → 放行
        verify(redis, times(2)).execute(any(RedisScript.class), anyList(), any(), any(), any(), any());
    }

    @Test
    void redis路径传参与ttl正确() {
        StringRedisTemplate redis = wireRedisMock();
        when(redis.execute(any(RedisScript.class), anyList(), any(), any(), any(), any())).thenReturn(1L);
        svc.tryAcquire("mykey", 10, 0.5);
        // capacity=10, refillPerSec=0.5, now=1_000_000, ttl=max(60000, 10/0.5*1000*2=40000)=60000
        verify(redis).execute(any(RedisScript.class), eq(Collections.singletonList("mykey")),
                eq("10"), eq("0.5"), eq("1000000"), eq("60000"));
    }

    @Test
    void refillPerSec为0时按容量限流且永不补充() {
        for (int i = 0; i < 4; i++) assertTrue(svc.tryAcquire("k", 4, 0.0));
        assertFalse(svc.tryAcquire("k", 4, 0.0));
        advance(100_000);
        assertFalse(svc.tryAcquire("k", 4, 0.0)); // refill=0 → 等多久都不补
    }

    @Test
    void 内存桶高并发恰好放行容量个() throws InterruptedException {
        int capacity = 10, threads = 40;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger ok = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (svc.tryAcquire("hot", capacity, 0.0)) ok.incrementAndGet();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown(); // 同时放枪
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdownNow();
        // 时钟固定+refill=0 → 容量恰好 10，原子 compute 保证不多放
        assertEquals(capacity, ok.get());
    }

    @Test
    void 清理移除空闲内存桶() {
        svc.tryAcquire("k", 1, 0.0);
        @SuppressWarnings("unchecked")
        Map<String, double[]> buckets = (Map<String, double[]>) ReflectionTestUtils.getField(svc, "buckets");
        assertEquals(1, buckets.size());
        advance(400_000); // > 5 分钟未访问
        svc.sweep();
        assertTrue(buckets.isEmpty());
    }

    @Test
    void 清理保留活跃桶() {
        svc.tryAcquire("k", 1, 1.0);
        advance(1000); // 仍活跃
        svc.sweep();
        @SuppressWarnings("unchecked")
        Map<String, double[]> buckets = (Map<String, double[]>) ReflectionTestUtils.getField(svc, "buckets");
        assertEquals(1, buckets.size());
    }
}
