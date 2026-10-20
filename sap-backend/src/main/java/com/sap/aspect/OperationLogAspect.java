package com.sap.aspect;

import com.sap.annotation.OperationLog;
import com.sap.entity.SysLog;
import com.sap.entity.User;
import com.sap.mapper.SysLogMapper;
import com.sap.mapper.UserMapper;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Objects;
import java.time.LocalDateTime;

@Aspect
@Component
public class OperationLogAspect {
    public static final String LOGGED="sap.audit.logged", CODE="sap.audit.code", ACTOR="sap.audit.actor", DESCRIPTION="sap.audit.description";

    @Autowired
    private SysLogMapper sysLogMapper;
    @Autowired
    private com.sap.service.LogDailyStatsService dailyStats;
    @Autowired
    private com.sap.service.ApiLogExclusionService exclusions;
    @Autowired
    private UserMapper userMapper;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint point, OperationLog operationLog) throws Throwable {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                && exclusions.excludes(attrs.getRequest())) return point.proceed();
        long start = System.currentTimeMillis();
        Long actor = null;
        try { if (StpUtil.isLogin()) actor = StpUtil.getLoginIdAsLong(); } catch (Exception ignored) { }
        Object result = null;
        Throwable failure = null;
        try {
            result = point.proceed();
            return result;
        } catch (Throwable error) {
            failure = error;
            throw error;
        } finally {
            try { saveLog(operationLog, System.currentTimeMillis() - start, result, failure, actor); }
            catch (Exception ignored) { /* 日志不能影响业务 */ }
        }
    }

    @Around("@within(org.springframework.web.bind.annotation.RestController) && !@annotation(com.sap.annotation.OperationLog)")
    public Object unannotated(ProceedingJoinPoint point) throws Throwable {
        return around(point, null);
    }

    public void recordRequest(HttpServletRequest request,long duration,int code) {
        try {saveRequest(request, null, duration, code, (Long)request.getAttribute(ACTOR));}
        catch(Exception ignored) { /* Audit failures must not fail requests. */ }
    }
    private void saveLog(OperationLog annotation, long duration, Object result, Throwable failure, Long actor) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return;
        if (actor == null && attrs.getRequest().getAttribute(ACTOR) instanceof Long verifiedActor) actor = verifiedActor;
        saveRequest(attrs.getRequest(),annotation,duration,failure instanceof com.sap.common.BusinessException e ? e.getCode() : failure != null ? 500 : result instanceof com.sap.common.Result<?> r ? r.getCode() : 200,actor);
    }
    private void saveRequest(HttpServletRequest request,OperationLog annotation,long duration,int code,Long actor) {
        if(exclusions.excludes(request))return;
        if(Boolean.TRUE.equals(request.getAttribute(LOGGED)))return;

        String httpMethod = request.getMethod();
        String path = request.getRequestURI();
        String ip = getIpAddr(request);
        String description = annotation == null ? Objects.toString(request.getAttribute(DESCRIPTION),"接口调用") : annotation.value();
        String operationType = resolveOperationType(httpMethod);

        // 获取当前用户
        Long userId = actor;
        String userName = "匿名";
        try {
            if (userId == null && StpUtil.isLogin()) userId = StpUtil.getLoginIdAsLong();
            if (userId != null) {
                User user = userMapper.selectById(userId);
                if (user != null) {
                    userName = user.getName() != null ? user.getName() : user.getStudentId();
                }
            }
        } catch (Exception e) {}

        // 保存日志
        SysLog log = new SysLog();
        log.setUserId(userId);
        log.setUserName(userName);
        log.setIp(ip);
        log.setHttpMethod(httpMethod);
        log.setPath(path);
        log.setOperationType(operationType);
        log.setDescription(description);
        log.setDuration(duration);
        log.setRequestTime(LocalDateTime.now());
        Object pattern = request.getAttribute(org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        log.setEndpoint(pattern == null ? com.sap.service.LogAnalyticsService.normalizeEndpoint(path) : pattern.toString());
        log.setSource(com.sap.service.LogAnalyticsService.classify(path, request.getHeader("X-SAP-Client")));
        log.setResultCode(code);
        sysLogMapper.insert(log);
        request.setAttribute(LOGGED,true);

        dailyStats.record(log);
    }

    private String resolveOperationType(String httpMethod) {
        return switch (httpMethod.toUpperCase()) {
            case "POST" -> "新增";
            case "PUT", "PATCH" -> "修改";
            case "DELETE" -> "删除";
            default -> "查询";
        };
    }

    private String getIpAddr(HttpServletRequest request) {
        return com.sap.util.IpUtil.clientIp(request);
    }
}
