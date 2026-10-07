package com.sap.controller.judger;

import cn.dev33.satoken.annotation.*;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.dto.judger.ProblemSolutionDocument;
import com.sap.service.judger.OjSolutionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/admin/oj") @RequiredArgsConstructor
@SaCheckRole(value={"0","1","2"},mode=SaMode.OR)
public class OjSolutionAdminController {
    private final OjSolutionService solutions;
    @GetMapping("/problems/{id}/solution") @OperationLog("算法题库：管理题解")
    public Result<?> detail(@PathVariable Long id,HttpServletResponse response){response.setHeader("Cache-Control","private, no-store");return Result.ok(solutions.admin(id));}
    @PutMapping("/problems/{id}/solution") @OperationLog("算法题库：保存题解")
    public Result<?> save(@PathVariable Long id,@RequestBody ProblemSolutionDocument d){return Result.ok(solutions.save(id,d));}
    @PostMapping("/solutions/import") @OperationLog("算法题库：批量导入题解")
    public Result<?> batch(@RequestBody List<ProblemSolutionDocument> documents){return Result.ok(solutions.importDocuments(documents));}
}
