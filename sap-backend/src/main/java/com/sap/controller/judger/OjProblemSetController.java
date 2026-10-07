package com.sap.controller.judger;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.dto.judger.SubmitRequest;
import com.sap.service.judger.OjProblemSetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/oj/sets") @SaCheckLogin @RequiredArgsConstructor
public class OjProblemSetController {
    private final OjProblemSetService service;
    private final com.sap.service.judger.OjProgressService progress;
    private Long uid(){return StpUtil.getLoginIdAsLong();}
    @GetMapping @OperationLog("题单：浏览题单")
    public Result<?> list(@RequestParam(required=false) String keyword,@RequestParam(required=false) String mode,@RequestParam(defaultValue="1") int page){return Result.ok(service.list(uid(),false,keyword,mode,page));}
    @GetMapping("/{id}") @OperationLog("题单：查看题单")
    public Result<?> detail(@PathVariable Long id){Long userId=uid();return Result.ok(progress.problemSet(service.detail(id,userId,false),userId,id));}
    @PostMapping("/{id}/join") @OperationLog("题单：加入或报名")
    public Result<?> join(@PathVariable Long id){service.join(id,uid());return Result.ok();}
    @GetMapping("/{id}/items/{itemId}") @OperationLog("题单：查看题目")
    public Result<?> problem(@PathVariable Long id,@PathVariable Long itemId){return Result.ok(service.problem(id,itemId,uid()));}
    @PostMapping("/{id}/items/{itemId}/run") @OperationLog("题单：运行代码")
    public Result<?> run(@PathVariable Long id,@PathVariable Long itemId,@Valid @RequestBody SubmitRequest r){return Result.ok(service.enqueue(id,itemId,uid(),r,"RUN"));}
    @PostMapping("/{id}/items/{itemId}/submit") @OperationLog("题单：提交判题")
    public Result<?> submit(@PathVariable Long id,@PathVariable Long itemId,@Valid @RequestBody SubmitRequest r){return Result.ok(service.enqueue(id,itemId,uid(),r,"SUBMIT"));}
    @GetMapping("/{id}/ranking") @OperationLog("题单：查看排名与完成名单")
    public Result<?> ranking(@PathVariable Long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="false") boolean completed){return Result.ok(service.standings(id,uid(),false,page,completed));}
    @GetMapping("/{id}/submissions") @OperationLog("题单：查看提交记录")
    public Result<?> history(@PathVariable Long id,@RequestParam(defaultValue="true") boolean mine,@RequestParam(required=false) Long itemId,@RequestParam(required=false) String language,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page){return Result.ok(service.history(id,uid(),false,mine,itemId,language,status,page));}
    @GetMapping("/{id}/submissions/{jobId}/code") @OperationLog("题单：查看提交代码")
    public Result<?> source(@PathVariable Long id,@PathVariable Long jobId){return Result.ok(service.source(id,jobId,uid(),false));}
}
