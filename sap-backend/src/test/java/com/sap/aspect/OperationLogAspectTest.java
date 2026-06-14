package com.sap.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.BaseUnitTest;
import com.sap.annotation.OperationLog;
import com.sap.entity.LogStats;
import com.sap.entity.User;
import com.sap.mapper.LogStatsMapper;
import com.sap.mapper.SysLogMapper;
import com.sap.mapper.UserMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OperationLogAspect 单元测试。
 * 注解实例直接从带 @OperationLog 的真实方法上反射获取。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OperationLogAspectTest extends BaseUnitTest {

    @Mock SysLogMapper sysLogMapper;
    @Mock LogStatsMapper logStatsMapper;
    @Mock UserMapper userMapper;

    @InjectMocks OperationLogAspect aspect;

    /** 携带 @OperationLog 注解的样例方法，用于反射拿到注解实例。 */
    @OperationLog("测试操作")
    public void annotatedSample() {
    }

    private OperationLog annotation() throws NoSuchMethodException {
        return OperationLogAspectTest.class.getMethod("annotatedSample").getAnnotation(OperationLog.class);
    }

    private void bindRequest(String method, String uri, String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(method);
        request.setRequestURI(uri);
        if (forwardedFor != null) {
            request.addHeader("X-Forwarded-For", forwardedFor);
        }
        request.setRemoteAddr("10.0.0.5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void around_returnsProceedResult_andSavesLog_whenNotLogin() throws Throwable {
        bindRequest("POST", "/api/test", null);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            when(logStatsMapper.selectOne(any())).thenReturn(null);

            Object result = aspect.around(pjp, annotation());

            assertEquals("ok", result);
            verify(sysLogMapper).insert(any());
            // 当天无统计记录 -> 新插入
            verify(logStatsMapper).insert(any(LogStats.class));
        }
    }

    @Test
    void around_savesLogWithLoginUser_andIncrementsExistingStats() throws Throwable {
        bindRequest("GET", "/api/query", "1.2.3.4, 5.6.7.8");
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn(42);

        User user = new User();
        user.setName("张三");
        LogStats existing = new LogStats();
        existing.setCount(3);

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(7L);
            when(userMapper.selectById(7L)).thenReturn(user);
            when(logStatsMapper.selectOne(any())).thenReturn(existing);

            Object result = aspect.around(pjp, annotation());

            assertEquals(42, result);
            verify(sysLogMapper).insert(any());
            // 已有统计 -> 计数 +1 并更新
            assertEquals(4, existing.getCount());
            verify(logStatsMapper).updateById(existing);
            verify(logStatsMapper, never()).insert(any());
        }
    }

    @Test
    void around_loginUserWithoutName_fallsBackToStudentId() throws Throwable {
        bindRequest("DELETE", "/api/del", null);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn(null);

        User user = new User();
        user.setName(null);
        user.setStudentId("2021001");

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(true);
            stp.when(StpUtil::getLoginIdAsLong).thenReturn(9L);
            when(userMapper.selectById(9L)).thenReturn(user);
            when(logStatsMapper.selectOne(any())).thenReturn(null);

            aspect.around(pjp, annotation());

            verify(sysLogMapper).insert(any());
        }
    }

    @Test
    void around_propagatesProceedException_andDoesNotSaveLog() throws Throwable {
        bindRequest("PUT", "/api/update", null);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        RuntimeException boom = new RuntimeException("业务失败");
        when(pjp.proceed()).thenThrow(boom);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> aspect.around(pjp, annotation()));
        assertSame(boom, thrown);
        // proceed 抛异常时，after 段不会执行，故不写日志
        verify(sysLogMapper, never()).insert(any());
    }

    @Test
    void around_swallowsSaveLogException_andStillReturnsResult() throws Throwable {
        bindRequest("PATCH", "/api/patch", null);
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("done");

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::isLogin).thenReturn(false);
            // saveLog 内部抛异常应被 catch 吞掉，不影响返回
            when(logStatsMapper.selectOne(any())).thenReturn(null);
            doThrow(new RuntimeException("db down")).when(sysLogMapper).insert(any());

            Object result = aspect.around(pjp, annotation());
            assertEquals("done", result);
        }
    }

    @Test
    void around_savesLog_whenNoRequestContext() throws Throwable {
        // 无请求上下文时 saveLog 提前 return，不应抛异常
        RequestContextHolder.resetRequestAttributes();
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("noctx");

        Object result = aspect.around(pjp, annotation());

        assertEquals("noctx", result);
        verify(sysLogMapper, never()).insert(any());
    }
}
