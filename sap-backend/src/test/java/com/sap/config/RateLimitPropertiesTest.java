package com.sap.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 限流配置默认值/读写/Rule 单测。 */
class RateLimitPropertiesTest {

    @Test
    void 默认值合理() {
        RateLimitProperties p = new RateLimitProperties();
        assertTrue(p.isEnabled());
        assertFalse(p.isDryRun());
        assertTrue(p.isUseRedis());
        assertEquals(30, p.getLogin().getCapacity());
        assertEquals(20, p.getRegister().getRefillPerMinute());
        assertEquals(30, p.getJw().getCapacity());
        assertEquals(60, p.getWrite().getCapacity());
        assertEquals(10, p.getPdf().getCapacity());
        assertEquals(60, p.getDownload().getRefillPerMinute());
    }

    @Test
    void 读写各字段() {
        RateLimitProperties p = new RateLimitProperties();
        p.setEnabled(false);
        p.setDryRun(true);
        p.setUseRedis(false);
        RateLimitProperties.Rule r = new RateLimitProperties.Rule(99, 88);
        p.setLogin(r);
        p.setRegister(r);
        p.setJw(r);
        p.setWrite(r);
        p.setPdf(r);
        p.setDownload(r);
        assertFalse(p.isEnabled());
        assertTrue(p.isDryRun());
        assertFalse(p.isUseRedis());
        assertEquals(99, p.getDownload().getCapacity());

        RateLimitProperties.Rule r2 = new RateLimitProperties.Rule();
        r2.setCapacity(7);
        r2.setRefillPerMinute(6);
        assertEquals(7, r2.getCapacity());
        assertEquals(6, r2.getRefillPerMinute());
    }
}
