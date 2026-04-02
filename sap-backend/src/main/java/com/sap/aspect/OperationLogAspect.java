package com.sap.aspect;

import com.sap.annotation.OperationLog;
import com.sap.entity.LogStats;
import com.sap.entity.SysLog;
import com.sap.entity.User;
import com.sap.mapper.LogStatsMapper;
import com.sap.mapper.SysLogMapper;
import com.sap.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Aspect
@Component
public class OperationLogAspect {

    @Autowired
    private SysLogMapper sysLogMapper;
    @Autowired
    private LogStatsMapper logStatsMapper;
    @Autowired
    private UserMapper userMapper;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint point, OperationLog operationLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = point.proceed();
        long duration = System.currentTimeMillis() - start;

        try {
            saveLog(operationLog, duration);
        } catch (Exception e) {
            // 日志记录不应影响正常业务
        }
        return result;
    }

    private void saveLog(OperationLog annotation, long duration) {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return;
        HttpServletRequest request = attrs.getRequest();

        String httpMethod = request.getMethod();
        String path = request.getRequestURI();
        String ip = getIpAddr(request);
        String description = annotation.value();
        String operationType = resolveOperationType(httpMethod);

        // 获取当前用户
        Long userId = null;
        String userName = "匿名";
        try {
            if (StpUtil.isLogin()) {
                userId = StpUtil.getLoginIdAsLong();
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
        sysLogMapper.insert(log);

        // 更新统计表
        updateStats(operationType, httpMethod);
    }

    private void updateStats(String operationType, String httpMethod) {
        LocalDate today = LocalDate.now();
        LogStats existing = logStatsMapper.selectOne(
                new LambdaQueryWrapper<LogStats>()
                        .eq(LogStats::getStatDate, today)
                        .eq(LogStats::getOperationType, operationType)
                        .eq(LogStats::getHttpMethod, httpMethod)
        );
        if (existing != null) {
            existing.setCount(existing.getCount() + 1);
            logStatsMapper.updateById(existing);
        } else {
            LogStats stats = new LogStats();
            stats.setStatDate(today);
            stats.setOperationType(operationType);
            stats.setHttpMethod(httpMethod);
            stats.setCount(1);
            logStatsMapper.insert(stats);
        }
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
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
