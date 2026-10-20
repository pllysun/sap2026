package io.sapoj.controller.judger;

import cn.dev33.satoken.annotation.*;
import cn.dev33.satoken.stp.StpUtil;
import io.sapoj.common.Result;
import io.sapoj.dto.judger.ProblemPack;
import io.sapoj.dto.judger.UpdateProblemRequest;
import io.sapoj.entity.judger.OjLanguage;
import io.sapoj.service.judger.OjService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/admin/oj") @RequiredArgsConstructor
@SaCheckRole(value={"0","1","2"},mode=SaMode.OR)
public class OjAdminController {
    private final OjService service;
    private final io.sapoj.service.judger.OjInsightsService insights;
    @GetMapping("/monitor/{id}/code") @io.sapoj.annotation.OperationLog("算法题库：查看运行记录提交代码")
    public Result<?> submittedCode(@PathVariable Long id) { var job=service.job(id,StpUtil.getLoginIdAsLong(),true);return Result.ok(Map.of("code",job.get("code"),"language",job.get("language"),"mode",job.get("mode"))); }
    @GetMapping("/order") @io.sapoj.annotation.OperationLog("算法题库：查看题目顺序") public Result<?> order() { return Result.ok(service.order()); }
    @PutMapping("/order") @io.sapoj.annotation.OperationLog("算法题库：调整题目顺序") public Result<?> order(@RequestBody io.sapoj.dto.judger.OrderRequest r) { service.reorder(r.getExpected(),r.getIds()); return Result.ok(); }
    @GetMapping("/monitor") @io.sapoj.annotation.OperationLog("算法题库：查看判题运行记录") public Result<?> monitor(@RequestParam(defaultValue="1") int page) { return Result.ok(insights.monitor(page)); }
    @GetMapping("/monitor/{id}") @io.sapoj.annotation.OperationLog("算法题库：查看任务运行过程") public Result<?> monitorJob(@PathVariable Long id) { return Result.ok(insights.monitorJob(id)); }
    @GetMapping("/health") @io.sapoj.annotation.OperationLog("算法题库：查看判题服务健康") public Result<?> health() { return Result.ok(service.health()); }
    @GetMapping("/languages") @io.sapoj.annotation.OperationLog("算法题库：查看语言配置") public Result<?> languages() { return Result.ok(service.languageList(true)); }
    @PutMapping("/languages") @io.sapoj.annotation.OperationLog("算法题库：保存语言配置") public Result<?> languages(@RequestBody List<OjLanguage> values) { service.saveLanguages(values); return Result.ok(); }
    @GetMapping("/problems") @io.sapoj.annotation.OperationLog("算法题库：管理题目列表") public Result<?> list(@RequestParam(required=false) String keyword,
        @RequestParam(required=false) String difficulty,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return Result.ok(service.list(true,keyword,difficulty,page,size));
    }
    @GetMapping("/problems/{id}") @io.sapoj.annotation.OperationLog("算法题库：查看题目编辑内容") public Result<?> detail(@PathVariable Long id) { return Result.ok(service.detail(id,true)); }
    @PostMapping("/problems") @io.sapoj.annotation.OperationLog("算法题库：创建题目") public Result<?> create(@RequestBody ProblemPack pack) { var p=service.save(null,pack,null); return Result.ok(Map.of("id",p.getId(),"revision",p.getRevision())); }
    @PutMapping("/problems/{id}") @io.sapoj.annotation.OperationLog("算法题库：更新题目") public Result<?> update(@PathVariable Long id,@RequestBody UpdateProblemRequest request) {
        var p=service.save(id,request.getPack(),request.getRevision()); return Result.ok(Map.of("id",p.getId(),"revision",p.getRevision()));
    }
    @PutMapping("/problems/{id}/status") @io.sapoj.annotation.OperationLog("算法题库：修改题目发布状态") public Result<?> status(@PathVariable Long id,@RequestBody Map<String,String> request) { service.status(id,request.get("status")); return Result.ok(); }
    @PostMapping("/problems/{id}/validate") @io.sapoj.annotation.OperationLog("算法题库：验证题目测试集") public Result<?> validate(@PathVariable Long id) { return Result.ok(service.validate(id,StpUtil.getLoginIdAsLong())); }
    @GetMapping("/jobs/{id}") @io.sapoj.annotation.OperationLog("算法题库：查看发布验证任务") public Result<?> job(@PathVariable Long id) { return Result.ok(service.job(id,StpUtil.getLoginIdAsLong(),true)); }
}
