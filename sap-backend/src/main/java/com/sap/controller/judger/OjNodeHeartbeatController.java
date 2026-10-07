package com.sap.controller.judger;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import com.sap.annotation.OperationLog;
import com.sap.common.Result;
import com.sap.service.judger.JudgerNodes;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Separate machine authentication; never accepts a user session in place of a node token. */
@RestController @RequestMapping("/api/oj-nodes") @RequiredArgsConstructor
public class OjNodeHeartbeatController {
    private final JudgerNodes nodes;
    private final ObjectMapper json;
    @PostMapping("/{id}/heartbeat") @OperationLog("算法题库：判题节点资源上报")
    public Result<?> heartbeat(@PathVariable Long id,@RequestHeader(value="Authorization",required=false) String token,@RequestBody Map<String,Object> data){
        nodes.heartbeat(id,token,json.valueToTree(data));return Result.ok();
    }
}
