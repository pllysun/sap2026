package com.sap.controller.judger;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.service.judger.OjExecutionEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@cn.dev33.satoken.annotation.SaCheckLogin
@RestController
@RequestMapping("/api/oj/submissions")
@RequiredArgsConstructor
public class OjExecutionController {
    private final OjExecutionEvents events;

    @GetMapping("/{id}/events")
    @OperationLog("算法题库：订阅本人执行进度")
    public ResponseEntity<SseEmitter> stream(@PathVariable Long id) {
        SseEmitter emitter = events.subscribe(id, StpUtil.getLoginIdAsLong());
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-store, no-transform")
            .header("X-Accel-Buffering","no").body(emitter);
    }
}
