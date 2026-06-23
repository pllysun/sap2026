package com.sap.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 限流配置（前缀 app.ratelimit）。各类别为令牌桶：capacity=瞬时突发上限，refillPerMinute=持续速率。
 * <p>默认值偏宽松，目标是挡住异常刷量而非打扰正常用户/管理端/App。可在 application*.yml 覆盖。</p>
 * <ul>
 *   <li>enabled：总开关，关掉则完全不限流（应急回滚）。</li>
 *   <li>dryRun：灰度模式，只记日志不真正拦截（先观测会拦哪些，确认不误伤再关掉灰度）。</li>
 *   <li>useRedis：有 Redis 时用 Redis 令牌桶（跨重启持久）；无 Redis/故障自动降级内存限流。</li>
 * </ul>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ratelimit")
public class RateLimitProperties {

    private boolean enabled = true;
    private boolean dryRun = false;
    private boolean useRedis = true;

    /**
     * 登录（/api/auth/login|admin/login|app/login），按 IP。容量给足突发：校园网 NAT 下大量学生共用
     * 出口 IP，高峰一起登录不应被误限；按账号的暴力破解防护由 AuthService 的失败锁定(账号+IP)负责，
     * 此处仅作粗粒度洪泛闸。
     */
    private Rule login = new Rule(30, 30);
    /** 注册（/api/auth/register），按 IP（与风控验证码叠加，给足突发以兼容校园网共用 IP）。 */
    private Rule register = new Rule(20, 20);
    /** 教务代抓（/api/jw/**），按用户——遏制线程池 DoS，同时容下 App 启动一次正常同步。 */
    private Rule jw = new Rule(30, 30);
    /** 写接口（POST/PUT/DELETE/PATCH 于 /api/**），按用户/IP。 */
    private Rule write = new Rule(60, 60);
    /** PDF 生成（/api/note/&#123;id&#125;/pdf），按用户——PDF 较贵，限得紧些。 */
    private Rule pdf = new Rule(10, 10);
    /** 下载中转（/api/file/download|go），按用户/IP。 */
    private Rule download = new Rule(60, 60);

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Rule {
        /** 令牌桶容量（瞬时可突发的请求数）。 */
        private int capacity;
        /** 每分钟补充的令牌数（长期可持续的速率）。 */
        private int refillPerMinute;
    }
}
