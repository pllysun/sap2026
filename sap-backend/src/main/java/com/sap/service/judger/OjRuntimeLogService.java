package com.sap.service.judger;
import com.sap.entity.judger.OjRuntimeLog;
import com.sap.mapper.judger.OjRuntimeLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
/** Structured events only: no arbitrary log message, code, credentials or test data. */
@Service @RequiredArgsConstructor @Slf4j
public class OjRuntimeLogService {
    private final OjRuntimeLogMapper mapper;
    public void event(String event,Long jobId,String status) {
        event(event,jobId,status,null);
    }
    public void event(String event,Long jobId,String status,String nodeName) {
        try {OjRuntimeLog row=new OjRuntimeLog();row.setEvent(event);row.setJobId(jobId);row.setStatus(status);
            row.setNodeName(nodeName);
            row.setLevel(event.endsWith("ERROR") || "SYSTEM_ERROR".equals(status) || ("FINISHED".equals(event) && !"AC".equals(status))?"ERROR":"INFO");row.setCreatedAt(LocalDateTime.now());mapper.insert(row);
        }catch(Exception e){log.warn("OJ structured event write failed ({})",e.getClass().getSimpleName());}
    }
    @Scheduled(cron="0 10 3 * * *") public void prune(){mapper.delete(new LambdaQueryWrapper<OjRuntimeLog>().lt(OjRuntimeLog::getCreatedAt,LocalDateTime.now().minusDays(30)));}
}
