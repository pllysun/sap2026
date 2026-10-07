package com.sap.controller.judger;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.service.judger.OjSolutionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/oj") @SaCheckLogin @RequiredArgsConstructor
public class OjSolutionController {
    private final OjSolutionService solutions;
    private void privateResponse(HttpServletResponse response){response.setHeader("Cache-Control","private, no-store");response.setHeader("Vary","sap-token");}
    @GetMapping("/problems/{id}/solution") @OperationLog("算法题库：查看题解")
    public Result<?> library(@PathVariable Long id,HttpServletResponse response){privateResponse(response);return Result.ok(solutions.library(id,StpUtil.getLoginIdAsLong()));}
    @GetMapping("/sets/{setId}/items/{itemId}/solution") @OperationLog("题单：查看题解")
    public Result<?> inSet(@PathVariable Long setId,@PathVariable Long itemId,HttpServletResponse response){privateResponse(response);return Result.ok(solutions.inSet(setId,itemId,StpUtil.getLoginIdAsLong()));}
}
