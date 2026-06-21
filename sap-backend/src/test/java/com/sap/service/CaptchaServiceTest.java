package com.sap.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 注册风控验证码逻辑单测：默认不触发、超宽松阈值才触发、验证码一次性消费、应急开关、过期清理。
 * 纯内存逻辑，无 Spring/DB 依赖。
 */
class CaptchaServiceTest {

    private CaptchaService svc(boolean enabled, int freeLimit, int windowHours, int ttlSeconds) {
        CaptchaService s = new CaptchaService();
        ReflectionTestUtils.setField(s, "enabled", enabled);
        ReflectionTestUtils.setField(s, "freeLimit", freeLimit);
        ReflectionTestUtils.setField(s, "windowHours", windowHours);
        ReflectionTestUtils.setField(s, "captchaTtlSeconds", ttlSeconds);
        return s;
    }

    private String answerOf(CaptchaService s, String captchaId) {
        @SuppressWarnings("unchecked")
        Map<String, Object> captchas = (Map<String, Object>) ReflectionTestUtils.getField(s, "captchas");
        Object captcha = captchas.get(captchaId);
        return (String) ReflectionTestUtils.getField(captcha, "answer");
    }

    @Test
    void 全新IP默认不要求验证码() {
        assertFalse(svc(true, 5, 24, 180).captchaRequired("1.1.1.1"));
        // ip 为 null 也不应抛错且不要求
        assertFalse(svc(true, 5, 24, 180).captchaRequired(null));
    }

    @Test
    void 仅当超过宽松阈值后才要求验证码() {
        CaptchaService s = svc(true, 5, 24, 180);
        String ip = "2.2.2.2";
        for (int i = 0; i < 5; i++) {
            assertFalse(s.captchaRequired(ip), "第" + (i + 1) + "次注册前不应要求验证码");
            s.recordRegister(ip);
        }
        assertTrue(s.captchaRequired(ip), "达到阈值(5)后应要求验证码");
    }

    @Test
    void 总开关关闭则永不要求验证码() {
        CaptchaService s = svc(false, 5, 24, 180);
        String ip = "3.3.3.3";
        for (int i = 0; i < 10; i++) s.recordRegister(ip);
        assertFalse(s.captchaRequired(ip));
    }

    @Test
    void 窗口过期后计数重置() {
        // windowHours=0 → 每条记录的窗口立即过期，captchaRequired/recordRegister 都走"重新计窗"分支
        CaptchaService s = svc(true, 5, 0, 180);
        String ip = "4.4.4.4";
        for (int i = 0; i < 10; i++) s.recordRegister(ip);
        assertFalse(s.captchaRequired(ip), "窗口已过期，计数应视为重置，不要求验证码");
    }

    @Test
    void 验证码大小写不敏感且一次性() {
        CaptchaService s = svc(true, 5, 24, 180);
        Map<String, Object> data = s.generate();
        String id = (String) data.get("captchaId");
        assertNotNull(id);
        assertTrue(((String) data.get("image")).startsWith("data:image"));
        String answer = answerOf(s, id);
        assertTrue(s.verify(id, answer.toUpperCase()), "大小写不敏感应通过");
        assertFalse(s.verify(id, answer), "一次性：第二次应失效");
    }

    @Test
    void 验证码错误或空或过期均不通过() {
        CaptchaService s = svc(true, 5, 24, 180);
        String id = (String) s.generate().get("captchaId");
        assertFalse(s.verify(id, "wrong"));
        assertFalse(s.verify(null, "x"));
        assertFalse(s.verify("x", null));
        assertFalse(s.verify(id, ""));
        // 过期：ttl 设为负数，生成即过期
        CaptchaService expired = svc(true, 5, 24, -10);
        String eid = (String) expired.generate().get("captchaId");
        assertFalse(expired.verify(eid, answerOf2(expired, eid)), "过期验证码不通过");
    }

    private String answerOf2(CaptchaService s, String id) {
        // 过期项仍在 map 里(尚未 sweep)，可读出答案模拟"答对但已过期"
        @SuppressWarnings("unchecked")
        Map<String, Object> captchas = (Map<String, Object>) ReflectionTestUtils.getField(s, "captchas");
        Object c = captchas.get(id);
        return c == null ? "" : (String) ReflectionTestUtils.getField(c, "answer");
    }

    @Test
    void 定时清理移除过期项() {
        CaptchaService s = svc(true, 5, 0, -10); // 验证码与IP窗口都立即过期
        s.generate();
        s.recordRegister("5.5.5.5");
        s.sweep();
        @SuppressWarnings("unchecked")
        Map<String, Object> captchas = (Map<String, Object>) ReflectionTestUtils.getField(s, "captchas");
        @SuppressWarnings("unchecked")
        Map<String, Object> ipStats = (Map<String, Object>) ReflectionTestUtils.getField(s, "ipStats");
        assertTrue(captchas.isEmpty(), "过期验证码应被清理");
        assertTrue(ipStats.isEmpty(), "过期IP计数应被清理");
    }
}
