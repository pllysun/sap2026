package com.sap.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommonClassesTest {

    @Test
    void result_okVariants() {
        Result<Object> ok = Result.ok();
        assertEquals(200, ok.getCode());
        assertEquals("success", ok.getMessage());
        assertNull(ok.getData());

        Result<String> okData = Result.ok("payload");
        assertEquals(200, okData.getCode());
        assertEquals("payload", okData.getData());

        Result<String> okMsg = Result.ok("msg", "payload");
        assertEquals("msg", okMsg.getMessage());
        assertEquals("payload", okMsg.getData());
    }

    @Test
    void result_errorVariants() {
        Result<Object> e1 = Result.error("boom");
        assertEquals(500, e1.getCode());
        assertEquals("boom", e1.getMessage());

        Result<Object> e2 = Result.error(403, "forbidden");
        assertEquals(403, e2.getCode());
        assertEquals("forbidden", e2.getMessage());
    }

    @Test
    void businessException_defaultAndCustomCode() {
        BusinessException d = new BusinessException("oops");
        assertEquals(500, d.getCode());
        assertEquals("oops", d.getMessage());

        BusinessException c = new BusinessException(401, "no");
        assertEquals(401, c.getCode());
        assertEquals("no", c.getMessage());
    }

    @Test
    void pageResult_of() {
        PageResult<String> p = PageResult.of(List.of("a", "b"), 2L, 1L, 10L);
        assertEquals(2, p.getRecords().size());
        assertEquals(2L, p.getTotal());
        assertEquals(1L, p.getCurrent());
        assertEquals(10L, p.getSize());
    }

    @Test
    void result_lombokAccessorsAndEquality() {
        Result<String> a = new Result<>();
        a.setCode(200);
        a.setMessage("ok");
        a.setData("d");
        Result<String> b = new Result<>();
        b.setCode(200);
        b.setMessage("ok");
        b.setData("d");

        assertEquals(200, a.getCode());
        assertEquals("ok", a.getMessage());
        assertEquals("d", a.getData());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotNull(a.toString());

        b.setCode(500);
        assertNotEquals(a, b);
        assertNotEquals(a, null);
        assertNotEquals(a, "string");
        assertEquals(a, a);
    }

    @Test
    void pageResult_lombokAccessorsAndEquality() {
        PageResult<String> a = new PageResult<>();
        a.setRecords(List.of("x"));
        a.setTotal(1L);
        a.setCurrent(1L);
        a.setSize(10L);
        PageResult<String> b = new PageResult<>();
        b.setRecords(List.of("x"));
        b.setTotal(1L);
        b.setCurrent(1L);
        b.setSize(10L);

        assertEquals(List.of("x"), a.getRecords());
        assertEquals(1L, a.getTotal());
        assertEquals(1L, a.getCurrent());
        assertEquals(10L, a.getSize());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotNull(a.toString());

        b.setTotal(99L);
        assertNotEquals(a, b);
        assertNotEquals(a, null);
        assertEquals(a, a);
    }
}
