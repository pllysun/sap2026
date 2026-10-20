package com.sap.service.judger;

import com.sap.common.BusinessException;
import com.sap.dto.judger.ProblemPack;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProblemSourceSecurityTest {
    @Test void originalProblemsAlsoRejectActiveContentUrls() {
        var service = mock(OjService.class, CALLS_REAL_METHODS);
        for (String platform : new String[]{"原创", "ORIGINAL", "LeetCode"}) {
            for (String url : new String[]{"javascript:alert(1)", "data:text/html,test", "//evil.test", "https://user@evil.test/", "https://example.com\n.evil.test"}) {
                var pack = pack(platform,url);
                var e = assertThrows(BusinessException.class,()->ReflectionTestUtils.invokeMethod(service,"validatePack",pack));
                assertTrue(e.getMessage().contains("来源链接"),platform+" "+url);
            }
        }
    }
    @Test void validHttpsAndEmptyOriginalSourceReachNormalPackValidation() {
        var service = mock(OjService.class, CALLS_REAL_METHODS);
        for (var pack : new ProblemPack[]{pack("原创",""),pack("原创",null),pack("LeetCode","https://example.com/problem")}) {
            var e=assertThrows(BusinessException.class,()->ReflectionTestUtils.invokeMethod(service,"validatePack",pack));
            assertEquals("做题模式或默认模式无效",e.getMessage());
        }
    }
    private ProblemPack pack(String platform,String url) {
        var p=new ProblemPack();p.setSlug("example");p.setTitle("测试题");p.setDescription("题面");p.setDifficulty("EASY");p.setSourcePlatform(platform);p.setSourceNote("来源说明");p.setSourceUrl(url);p.setModes(null);return p;
    }
}
