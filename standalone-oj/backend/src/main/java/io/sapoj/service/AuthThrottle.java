package io.sapoj.service;
import io.sapoj.common.BusinessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded per-account / IP counters. No credentials or request bodies are retained. */
@Service
public class AuthThrottle {
    private record Counter(int count,long expires) {}
    private final ConcurrentHashMap<String,Counter> counters=new ConcurrentHashMap<>();
    public void take(String key,int limit,long windowMs) {
        long now=System.currentTimeMillis();
        if(counters.size()>=10000&&!counters.containsKey(key))throw new BusinessException(429,"请求过多，请稍后重试");
        Counter c=counters.compute(key,(k,old)->old==null||old.expires<=now?new Counter(1,now+windowMs):new Counter(old.count+1,old.expires));
        if(c.count>limit)throw new BusinessException(429,"请求过多，请稍后重试");
    }
    @Scheduled(fixedDelay=60000) public void cleanup(){long now=System.currentTimeMillis();counters.entrySet().removeIf(e->e.getValue().expires<=now);}
}
