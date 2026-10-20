package io.sapoj.controller.judger;
import cn.dev33.satoken.annotation.*;
import io.sapoj.annotation.OperationLog;
import io.sapoj.common.Result;
import io.sapoj.dto.judger.NodeRequest;
import io.sapoj.service.judger.JudgerNodes;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/admin/oj/nodes") @RequiredArgsConstructor
@SaCheckRole(value={"0","1","2"},mode=SaMode.OR)
public class OjNodeController {
    private final JudgerNodes nodes;
    @GetMapping @OperationLog("算法题库：查看判题节点与资源")
    public Result<?> list(){return Result.ok(nodes.overview());}
    @PostMapping @OperationLog("算法题库：添加独立判题节点")
    public Result<?> create(@Valid @RequestBody NodeRequest request){return Result.ok(nodes.save(null,request));}
    @PutMapping("/{id}") @OperationLog("算法题库：修改判题节点")
    public Result<?> update(@PathVariable Long id,@Valid @RequestBody NodeRequest request){return Result.ok(nodes.save(id,request));}
    @PostMapping("/{id}/{action}") @OperationLog("算法题库：控制判题节点启停与探测")
    public Result<?> control(@PathVariable Long id,@PathVariable String action){return Result.ok(nodes.control(id,action));}
    @DeleteMapping("/{id}") @OperationLog("算法题库：移除独立判题节点")
    public Result<?> delete(@PathVariable Long id){nodes.delete(id);return Result.ok();}
}
