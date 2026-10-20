package com.sap.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class AuthenticationBodyLimitFilterTest {
    @Test void blocksDeclaredAndChunkedOversizeBeforeBodyParsing() throws Exception {
        var filter = new AuthenticationBodyLimitFilter();
        for (String path : new String[]{"/api/auth/login", "/api/auth/register/email-code", "/api/oj-nodes/12/heartbeat"}) {
            var request = new MockHttpServletRequest("POST", path) {
                @Override public long getContentLengthLong() { return -1; }
            };
            request.setContent(new byte[16385]);
            var response = new MockHttpServletResponse();
            var called = new AtomicBoolean();
            filter.doFilter(request,response,(a,b)->called.set(true));
            assertEquals(413,response.getStatus()); assertFalse(called.get());
        }
    }
    @Test void preservesNormalJsonAndDoesNotRestrictMultipartOrProfile() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setContent("{\"studentId\":\"example\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        new AuthenticationBodyLimitFilter().doFilter(request,new MockHttpServletResponse(),(a,b)->{
            assertEquals("{\"studentId\":\"example\"}", new String(a.getInputStream().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        });
        assertTrue(new AuthenticationBodyLimitFilter().shouldNotFilter(new MockHttpServletRequest("POST", "/api/file/upload")));
        assertTrue(new AuthenticationBodyLimitFilter().shouldNotFilter(new MockHttpServletRequest("PUT", "/api/auth/profile")));
    }
}
