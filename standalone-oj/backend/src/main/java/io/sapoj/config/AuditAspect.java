package io.sapoj.config;
import cn.dev33.satoken.stp.StpUtil;
import io.sapoj.annotation.OperationLog;
import io.sapoj.entity.AuditEvent;
import io.sapoj.mapper.AuditEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.time.LocalDateTime;

/** Never log bodies, query strings, source code, node tokens or passwords. */
@Aspect @Component @RequiredArgsConstructor @Slf4j
public class AuditAspect {
    private final AuditEventMapper events;
    @Around("@annotation(operation)") public Object audit(ProceedingJoinPoint point,OperationLog operation)throws Throwable{
        boolean success=false;
        try{Object result=point.proceed();success=true;return result;}
        finally{
            var attrs=RequestContextHolder.getRequestAttributes();
            if(attrs instanceof ServletRequestAttributes web)try{
                var e=new AuditEvent();e.setAction(operation.value());e.setSuccess(success);e.setCreatedAt(LocalDateTime.now());
                if(StpUtil.isLogin())e.setUserId(StpUtil.getLoginIdAsLong());
                e.setMethod(web.getRequest().getMethod());e.setPath(web.getRequest().getRequestURI());events.insert(e);
            }catch(Exception ignored){log.warn("Operation audit could not be persisted");}
        }
    }
}
