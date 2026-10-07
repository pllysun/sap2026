package com.sap.controller.judger;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.common.Result;
import com.sap.dto.judger.SubmitRequest;
import com.sap.service.judger.OjService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@cn.dev33.satoken.annotation.SaCheckLogin
@RestController @RequestMapping("/api/oj") @RequiredArgsConstructor
public class OjController {
    private final OjService service;
    private final com.sap.service.judger.OjProgressService progress;
    private final com.sap.service.judger.OjInsightsService insights;
    @GetMapping("/runtime") @com.sap.annotation.OperationLog("算法题库：查看可用运行资源")
    public Result<?> runtime() {var h=service.health();return Result.ok(java.util.Map.of("available",h.get("available"),"availableSlots",h.get("availableSlots"),"freeSlots",h.get("availableSlots"),"capacity",h.get("capacity")));}
    @GetMapping("/leaderboard") @com.sap.annotation.OperationLog("算法题库：查看提交统计") public Result<?> leaderboard() { return Result.ok(insights.leaderboard()); }
    @GetMapping("/problems/{id}/ranking") @com.sap.annotation.OperationLog("算法题库：查看题目排行") public Result<?> ranking(@PathVariable Long id,@RequestParam String language,@RequestParam String mode) { return Result.ok(insights.ranking(id,language,mode)); }
    @GetMapping("/submissions/{id}/performance") @com.sap.annotation.OperationLog("算法题库：查看运行性能统计") public Result<?> performance(@PathVariable Long id) { return Result.ok(insights.performance(id,StpUtil.getLoginIdAsLong())); }
    @GetMapping("/problems") @com.sap.annotation.OperationLog("算法题库：浏览题库") public Result<?> list(@RequestParam(required=false) String keyword,
        @RequestParam(required=false) String difficulty,@RequestParam(required=false) String tag,
        @RequestParam(required=false) String source,@RequestParam(required=false) String mode,
        @RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        return Result.ok(progress.library(service.list(false,keyword,difficulty,tag,source,mode,page,size),StpUtil.getLoginIdAsLong()));
    }
    @GetMapping("/filters") @com.sap.annotation.OperationLog("算法题库：查看题目筛选项")
    public Result<?> filters() { return Result.ok(service.filters()); }
    @GetMapping("/progress") @com.sap.annotation.OperationLog("算法题库：查看本人题目状态")
    public Result<?> progress(@RequestParam java.util.List<Long> ids) {
        if(ids.isEmpty()||ids.size()>50||ids.stream().anyMatch(id->id==null||id<=0))
            throw new com.sap.common.BusinessException("题目状态查询最多支持 50 道题");
        return Result.ok(progress.libraryStatus(service.visibleCatalogIds(ids.stream().distinct().toList()),StpUtil.getLoginIdAsLong()));
    }
    @GetMapping("/problems/{id}") @com.sap.annotation.OperationLog("算法题库：查看题目") public Result<?> detail(@PathVariable Long id) { return Result.ok(service.detail(id,false)); }
    @GetMapping("/languages") @com.sap.annotation.OperationLog("算法题库：查看可用语言") public Result<?> languages() { return Result.ok(service.languageList(false)); }
    @PostMapping("/run") @com.sap.annotation.OperationLog("算法题库：运行代码") public Result<?> run(@Valid @RequestBody SubmitRequest request) { return Result.ok(service.enqueue(StpUtil.getLoginIdAsLong(),request,"RUN")); }
    @PostMapping("/submit") @com.sap.annotation.OperationLog("算法题库：提交判题") public Result<?> submit(@Valid @RequestBody SubmitRequest request) { return Result.ok(service.enqueue(StpUtil.getLoginIdAsLong(),request,"SUBMIT")); }
    @GetMapping("/submissions/{id}") @com.sap.annotation.OperationLog("算法题库：查看本人提交详情") public Result<?> submission(@PathVariable Long id) { return Result.ok(service.job(id,StpUtil.getLoginIdAsLong(),false)); }
    @GetMapping("/submissions") @com.sap.annotation.OperationLog("算法题库：查看本人提交记录") public Result<?> history(@RequestParam(required=false) Long problemId,@RequestParam(defaultValue="1") int page) { return Result.ok(service.history(problemId,StpUtil.getLoginIdAsLong(),page)); }
}
