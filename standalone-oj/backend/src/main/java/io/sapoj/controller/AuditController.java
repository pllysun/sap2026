package io.sapoj.controller;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.sapoj.common.Result;
import io.sapoj.entity.AuditEvent;
import io.sapoj.mapper.AuditEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/audit") @RequiredArgsConstructor
@SaCheckRole(value={"0","1","2"},mode=SaMode.OR)
public class AuditController {
    private final AuditEventMapper events;
    @GetMapping public Result<?> list(@RequestParam(defaultValue="1") int page){
        return Result.ok(events.selectPage(new Page<>(Math.max(1,page),30),new LambdaQueryWrapper<AuditEvent>().orderByDesc(AuditEvent::getId)));
    }
}
