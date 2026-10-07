package com.sap.controller.judger;

import cn.dev33.satoken.annotation.*;
import cn.dev33.satoken.stp.StpUtil;
import com.sap.annotation.OperationLog;
import com.sap.common.BusinessException;
import com.sap.common.Result;
import com.sap.dto.judger.*;
import com.sap.service.judger.OjProblemSetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/oj/sets") @RequiredArgsConstructor
@SaCheckRole(value={"0","1","2"},mode=SaMode.OR)
public class OjProblemSetAdminController {
    private final OjProblemSetService service;
    private Long uid(){return StpUtil.getLoginIdAsLong();}
    @GetMapping @OperationLog("题单管理：查看题单列表")
    public Result<?> list(@RequestParam(required=false) String keyword,@RequestParam(required=false) String mode,@RequestParam(defaultValue="1") int page){return Result.ok(service.list(uid(),true,keyword,mode,page));}
    @GetMapping("/{id}") @OperationLog("题单管理：查看题单与操作记录")
    public Result<?> detail(@PathVariable Long id){return Result.ok(service.detail(id,uid(),true));}
    @PostMapping @OperationLog("题单管理：创建题单")
    public Result<?> create(@RequestBody ProblemSetRequest r){return Result.ok(service.save(null,r,uid()));}
    @PutMapping("/{id}") @OperationLog("题单管理：编辑题单")
    public Result<?> update(@PathVariable Long id,@RequestBody ProblemSetRequest r){return Result.ok(service.save(id,r,uid()));}
    @PostMapping("/{id}/copy") @OperationLog("题单管理：复制题单")
    public Result<?> copy(@PathVariable Long id){return Result.ok(service.copy(id,uid()));}
    @PutMapping("/{id}/status") @OperationLog("题单管理：发布或归档题单")
    public Result<?> status(@PathVariable Long id,@RequestBody ProblemSetActionRequest r){service.status(id,r.getStatus(),uid());return Result.ok();}
    @PutMapping("/{id}/public-code") @OperationLog("题单管理：设置赛后代码公开")
    public Result<?> publicCode(@PathVariable Long id,@RequestBody ProblemSetActionRequest r){if(r.getPublicCode()==null)throw new BusinessException(400,"请选择公开状态");service.publicCode(id,r.getPublicCode(),uid());return Result.ok();}
    @PostMapping("/{id}/extend") @OperationLog("题单管理：延长比赛")
    public Result<?> extend(@PathVariable Long id,@RequestBody ProblemSetActionRequest r){if(r.getEndsAt()==null)throw new BusinessException(400,"请填写结束时间");service.extend(id,r.getEndsAt(),r.getReason(),uid());return Result.ok();}
    @PostMapping("/{id}/items/{itemId}/cancel") @OperationLog("题单管理：作废题目")
    public Result<?> cancel(@PathVariable Long id,@PathVariable Long itemId,@RequestBody ProblemSetActionRequest r){service.cancel(id,itemId,r.getReason(),uid());return Result.ok();}
    @PutMapping("/{id}/participants") @OperationLog("题单管理：调整参与资格")
    public Result<?> participants(@PathVariable Long id,@RequestBody ProblemSetActionRequest r){if(r.getUserId()==null||r.getDisqualified()==null)throw new BusinessException(400,"请指定参与者和资格状态");service.disqualify(id,r.getUserId(),r.getDisqualified(),r.getReason(),uid());return Result.ok();}
    @PostMapping("/{id}/submissions/{jobId}/rejudge") @OperationLog("题单管理：重判提交")
    public Result<?> rejudge(@PathVariable Long id,@PathVariable Long jobId,@RequestBody ProblemSetActionRequest r){service.rejudge(id,jobId,r.getReason(),uid());return Result.ok();}
    @GetMapping("/{id}/ranking") @OperationLog("题单管理：查看完成名单与排名")
    public Result<?> ranking(@PathVariable Long id,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="false") boolean completed){return Result.ok(service.standings(id,uid(),true,page,completed));}
    @GetMapping("/{id}/submissions") @OperationLog("题单管理：查看提交记录")
    public Result<?> history(@PathVariable Long id,@RequestParam(required=false) Long itemId,@RequestParam(required=false) String language,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page){return Result.ok(service.history(id,uid(),true,false,itemId,language,status,page));}
    @GetMapping("/{id}/submissions/{jobId}/code") @OperationLog("题单管理：查看提交代码")
    public Result<?> code(@PathVariable Long id,@PathVariable Long jobId){return Result.ok(service.source(id,jobId,uid(),true));}
}
