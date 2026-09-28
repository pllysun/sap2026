package com.sap.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/** 不可变策略快照；默认值只用于首次建库，运行时从 sys_setting 读取。 */
public record RegistrationProtectionConfig(
        @NotNull @Valid Captcha captcha,
        @NotNull @Valid Quotas quotas,
        @NotNull @Valid Requests requests,
        @NotNull @Size(max = 500, message = "可信代理列表不能超过500个字符") String trustedProxies) {

    public record Captcha(
            @NotNull Boolean enabled,
            @NotNull @Min(0) @Max(1000) Integer freeLimit,
            @NotNull @Min(1) @Max(168) Integer freeWindowHours,
            @NotNull @Min(4) @Max(6) Integer length,
            @NotNull @Min(30) @Max(600) Integer ttlSeconds,
            @NotNull @Min(0) @Max(30) Integer minSolveSeconds) {
        @JsonIgnore
        @AssertTrue(message = "最短作答时间必须小于验证码有效期")
        public boolean isSolveWindowValid() { return minSolveSeconds == null || ttlSeconds == null || minSolveSeconds < ttlSeconds; }
    }

    public record Quotas(
            @NotNull Boolean enabled,
            @NotNull @Min(1) @Max(3600) Integer cooldownSeconds,
            @NotNull @Min(1) @Max(10000) Integer ipHourlyLimit,
            @NotNull @Min(1) @Max(50000) Integer ipDailyLimit,
            @NotNull @Min(1) @Max(100) Integer qqDailyLimit,
            @NotNull @Min(1) @Max(10000) Integer globalMinuteLimit,
            @NotNull @Min(1) @Max(128) Integer maxConcurrent,
            @NotNull @Min(1) @Max(128) Integer maxConcurrentPerIp) {
        @JsonIgnore
        @AssertTrue(message = "同IP并发上限不能超过实例总并发上限")
        public boolean isConcurrencyValid() { return maxConcurrentPerIp == null || maxConcurrent == null || maxConcurrentPerIp <= maxConcurrent; }
    }

    public record Requests(
            @NotNull Boolean enabled,
            @NotNull @Min(1) @Max(1000) Integer registerCapacity,
            @NotNull @Min(1) @Max(1000) Integer registerPerMinute,
            @NotNull @Min(1) @Max(1000) Integer captchaCapacity,
            @NotNull @Min(1) @Max(1000) Integer captchaPerMinute,
            @NotNull @Min(1) @Max(5000) Integer captchaGlobalCapacity,
            @NotNull @Min(1) @Max(5000) Integer captchaGlobalPerMinute) {}

    public static RegistrationProtectionConfig defaults() {
        return new RegistrationProtectionConfig(
                new Captcha(true, 0, 24, 6, 180, 1),
                new Quotas(true, 10, 20, 50, 3, 30, 4, 1),
                new Requests(true, 20, 20, 10, 10, 60, 60),
                "127.0.0.1,::1");
    }
}
