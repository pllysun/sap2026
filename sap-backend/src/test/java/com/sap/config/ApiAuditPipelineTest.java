package com.sap.config;

import com.sap.aspect.OperationLogAspect;
import com.sap.common.*;
import com.sap.entity.SysLog;
import com.sap.mapper.*;
import com.sap.annotation.OperationLog;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class ApiAuditPipelineTest {
    @RestController static class Endpoint {
        record Payload(@NotBlank String code){}
        @OperationLog("提交代码") @PostMapping("/api/oj/submit") public Result<?> submit(@Valid @RequestBody Payload value){return Result.ok();}
    }
    @Test void denialsAndValidationAppearExactlyOnceWithoutSourceOrTokens() throws Exception {
        var logs=mock(SysLogMapper.class);var aspect=new OperationLogAspect();
        ReflectionTestUtils.setField(aspect,"sysLogMapper",logs);ReflectionTestUtils.setField(aspect,"logStatsMapper",mock(LogStatsMapper.class));ReflectionTestUtils.setField(aspect,"userMapper",mock(UserMapper.class));
        List<SysLog> captured=new ArrayList<>();when(logs.insert(any(SysLog.class))).thenAnswer(i->{captured.add(i.getArgument(0));return 1;});
        HandlerInterceptor guard=new HandlerInterceptor(){@Override public boolean preHandle(jakarta.servlet.http.HttpServletRequest req,jakarta.servlet.http.HttpServletResponse res,Object handler) throws Exception {
            String outcome=req.getHeader("outcome");
            if("rate".equals(outcome)){req.setAttribute(OperationLogAspect.CODE,429);res.getWriter().write("{\"code\":429}");return false;}
            if("auth".equals(outcome))throw new BusinessException(401,"未登录");if("role".equals(outcome))throw new BusinessException(403,"无权限");return true;
        }};
        var mvc=MockMvcBuilders.standaloneSetup(new Endpoint()).setControllerAdvice(new GlobalExceptionHandler(),new ApiAuditResponseAdvice())
            .addInterceptors(guard).addFilters(new ApiAuditFilter(aspect)).build();
        for(String outcome:List.of("auth","role","rate","validation","success")) {
            String payload=outcome.equals("validation")?"{}":"{\"code\":\"PRIVATE_SOURCE\"}";
            mvc.perform(post("/api/oj/submit").header("outcome",outcome).header("sap-token","PRIVATE_TOKEN").contentType("application/json").content(payload)).andReturn();
        }
        assertEquals(List.of(401,403,429,400,200),captured.stream().map(SysLog::getResultCode).toList());
        assertTrue(captured.stream().allMatch(log->"/api/oj/submit".equals(log.getEndpoint())));
        assertFalse(captured.toString().contains("PRIVATE_"));
    }
    @Test void aspectAndBoundaryFilterDoNotWriteTheSameRequestTwice() throws Throwable {
        var logs=mock(SysLogMapper.class);var aspect=new OperationLogAspect();ReflectionTestUtils.setField(aspect,"sysLogMapper",logs);ReflectionTestUtils.setField(aspect,"logStatsMapper",mock(LogStatsMapper.class));ReflectionTestUtils.setField(aspect,"userMapper",mock(UserMapper.class));
        var request=new org.springframework.mock.web.MockHttpServletRequest("GET","/api/oj/test");var response=new org.springframework.mock.web.MockHttpServletResponse();
        var point=mock(org.aspectj.lang.ProceedingJoinPoint.class);when(point.proceed()).thenReturn(Result.ok());
        new ApiAuditFilter(aspect).doFilter(request,response,(req,res)->{
            org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
            try{aspect.around(point,null);}catch(Throwable e){throw new RuntimeException(e);}finally{org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
        });
        verify(logs,times(1)).insert(any(SysLog.class));
    }
}
