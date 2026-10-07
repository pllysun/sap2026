package com.sap.service.judger;

import com.fasterxml.jackson.databind.*;
import com.sap.common.BusinessException;
import com.sap.config.judger.JudgerProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

@Component @RequiredArgsConstructor
public class GoJudgeClient {
    private final JudgerProperties config;
    private final ObjectMapper json;
    private final JudgerNodes nodes;
    private final JudgerNodeTransport transport;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public JsonNode health() { return json.valueToTree(nodes.overview()); }

    public JsonNode execute(List<String> args, Map<String,Object> inputs, String stdin,
                            int timeMs, int memoryMb, boolean compile, List<String> artifacts) {
        Map<String,Object> command = new LinkedHashMap<>();
        command.put("args", args);
        command.put("env", List.of("PATH=/usr/local/bin:/usr/bin:/bin:/opt/rust/bin:/opt/java27/bin",
            "HOME=/w", "LANG=C.UTF-8", "LC_ALL=C.UTF-8", "TMPDIR=/tmp", "PYTHONDONTWRITEBYTECODE=1"));
        command.put("files", List.of(Map.of("content", stdin == null ? "" : stdin),
            Map.of("name","stdout","max",262144), Map.of("name","stderr","max",65536)));
        command.put("cpuLimit", timeMs * 1_000_000L);
        command.put("clockLimit", Math.max(3000L, timeMs * 3L) * 1_000_000L);
        command.put("memoryLimit", memoryMb * 1024L * 1024L);
        command.put("stackLimit", 32 * 1024 * 1024);
        command.put("procLimit", compile ? 64 : 32);
        command.put("strictMemoryLimit", true);
        command.put("copyIn", inputs);
        command.put("copyOutCached", artifacts);
        command.put("copyOutMax", 32 * 1024 * 1024);
        JsonNode result = request("POST", "/run", Map.of("cmd", List.of(command)),
            Math.max(15, timeMs * 3 / 1000 + 5));
        if (!result.isArray() || result.size() != 1) throw new BusinessException(503,"判题服务返回异常");
        return result.get(0);
    }

    public void removeArtifact(String id) {
        if (id == null || !id.matches("[a-zA-Z0-9_-]+")) return;
        request("DELETE", "/file/" + id, null, 5);
    }

    private JsonNode request(String method, String path, Object body, int timeout) {
        if (!config.isEnabled()) throw new BusinessException(503,"判题服务尚未启用");
        JudgerNodes.Lease lease=nodes.currentLease();
        if(lease==null)throw new NodeUnavailableException();
        try{return transport.request(lease.node,method,"/engine"+path,body,timeout,lease.id);}
        catch(JudgerNodeTransport.NodeCapacityException e){throw new NodeUnavailableException();}
    }
}
