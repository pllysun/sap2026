package com.sap.controller.judger;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.dto.judger.LearningAction;
import com.sap.service.judger.OjLearningService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/oj/learning") @SaCheckLogin @RequiredArgsConstructor
public class OjLearningController {
    private final OjLearningService learning;
    private void privateResponse(HttpServletResponse response){response.setHeader("Cache-Control","private, no-store");response.setHeader("Vary","sap-token");}
    @GetMapping
    public Result<?> overview(HttpServletResponse response){privateResponse(response);return Result.ok(learning.overview(StpUtil.getLoginIdAsLong()));}
    @PostMapping("/state") @OperationLog("算法训练：更新学习档案")
    public Result<?> update(@Valid @RequestBody LearningAction action,HttpServletResponse response){privateResponse(response);return Result.ok(learning.update(StpUtil.getLoginIdAsLong(),action));}
}
