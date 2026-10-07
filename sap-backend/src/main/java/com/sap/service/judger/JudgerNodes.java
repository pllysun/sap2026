package com.sap.service.judger;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.sap.common.BusinessException;
import com.sap.config.judger.JudgerProperties;
import com.sap.dto.judger.NodeRequest;
import com.sap.entity.judger.OjNode;
import com.sap.mapper.judger.OjNodeMapper;
import jakarta.annotation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service @RequiredArgsConstructor
public class JudgerNodes {
    private final OjNodeMapper mapper;
    private final JudgerProperties config;
    private final JudgerNodeTransport transport;
    private final JudgerNodeSecrets secrets;
    private final Map<Long,Report> reports=new ConcurrentHashMap<>();
    private final Map<Long,Integer> active=new ConcurrentHashMap<>();
    private final Set<Long> refreshing=ConcurrentHashMap.newKeySet();
    private final ExecutorService monitor=Executors.newVirtualThreadPerTaskExecutor();
    private final ThreadLocal<Lease> current=new ThreadLocal<>();
    java.util.function.LongSupplier clock=System::currentTimeMillis;
    private record Report(JsonNode data,long at,boolean reachable) {}
    public final class Lease implements AutoCloseable {
        public final OjNode node;
        public final String id;
        private boolean closed;
        private Lease(OjNode node,String id) {this.node=node;this.id=id;}
        public void bind() {current.set(this);}
        @Override public void close() {
            if(closed)return;closed=true;current.remove();
            synchronized(JudgerNodes.this){active.compute(node.getId(),(k,n)->Math.max(0,(n==null?0:n)-1));}
            // An interrupted judge thread still needs its short cleanup request;
            // HttpClient.send otherwise cancels it before the node releases the slot.
            boolean interrupted=Thread.interrupted();
            try{transport.request(node,"DELETE","/leases/"+id,null,2,null);}catch(Exception ignored){}
            finally{if(interrupted)Thread.currentThread().interrupt();}
            if(!monitor.isShutdown())refreshAsync(node);
        }
    }
    public Lease currentLease() {return current.get();}
    @PostConstruct public void initialize() {
        if(!config.isEnabled())return;
        if(mapper.selectCount(new LambdaQueryWrapper<OjNode>().eq(OjNode::getBuiltin,true))==0) {
            OjNode local=new OjNode();local.setName("容器内置节点");local.setEndpoint(config.getAgentEndpoint());local.setBuiltin(true);
            local.setEnabled(true);local.setMaxConcurrency(1);local.setCreatedAt(LocalDateTime.now());mapper.insert(local);
        }
        refreshAll();
    }
    @PreDestroy public void shutdown(){monitor.shutdownNow();}
    public List<OjNode> all(){return mapper.selectList(new LambdaQueryWrapper<OjNode>().orderByDesc(OjNode::getBuiltin).orderByAsc(OjNode::getId));}
    private OjNode require(Long id){OjNode node=mapper.selectById(id);if(node==null)throw new BusinessException(404,"节点不存在");return node;}
    @Scheduled(fixedDelay=5000) public void refreshAll(){if(config.isEnabled())all().forEach(this::refreshAsync);}
    private void refreshAsync(OjNode node){if(!refreshing.add(node.getId()))return;try{monitor.submit(()->{try{refresh(node);}finally{refreshing.remove(node.getId());}});}catch(RejectedExecutionException e){refreshing.remove(node.getId());}}
    public void refresh(OjNode node){
        try {
            JsonNode status=transport.request(node,"GET","/status",null,3,null);accept(node,status);
            synchronized(this) {
                OjNode desired=mapper.selectById(node.getId());
                if(desired!=null && !Boolean.TRUE.equals(desired.getEnabled()) && "RUNNING".equals(status.path("state").asText()))
                    accept(node,transport.request(node,"POST","/stop",Map.of(),3,null));
                else if(desired!=null && Boolean.TRUE.equals(desired.getBuiltin()) && Boolean.TRUE.equals(desired.getEnabled()) && "STOPPED".equals(status.path("state").asText()))
                    accept(node,transport.request(node,"POST","/start",Map.of(),3,null));
            }
        }catch(Exception e){Report previous=reports.get(node.getId());reports.put(node.getId(),new Report(previous==null?null:previous.data,previous==null?0:previous.at,false));}
    }
    private void accept(OjNode node,JsonNode data){
        if(data==null||!data.isObject()||data.toString().length()>16000||data.path("protocol").asInt()!=1)throw new BusinessException(400,"节点上报格式无效");
        reports.put(node.getId(),new Report(data,clock.getAsLong(),true));
    }
    public void heartbeat(Long id,String authorization,JsonNode data){
        OjNode node=require(id);
        if(authorization==null||!MessageDigest.isEqual(authorization.getBytes(StandardCharsets.UTF_8),("Bearer "+secrets.token(node)).getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException(401,"节点认证失败");
        accept(node,data);
    }
    private boolean online(Report r){return r!=null&&r.reachable&&clock.getAsLong()-r.at<15000&&r.data!=null;}
    private boolean compatible(Report r){return online(r)&&config.getRuntimeId().equals(r.data.path("runtimeId").asText());}
    private int capacity(OjNode node,Report r){return compatible(r)?Math.max(0,Math.min(node.getMaxConcurrency(),r.data.path("capacity").asInt())):0;}
    private int used(OjNode node,Report r){return Math.max(active.getOrDefault(node.getId(),0),r==null||r.data==null?0:r.data.path("active").asInt());}
    private int free(OjNode node,Report r){
        if(!config.isEnabled()||!Boolean.TRUE.equals(node.getEnabled())||!compatible(r)||!"RUNNING".equals(r.data.path("state").asText()))return 0;
        return Math.max(0,Math.min(capacity(node,r)-used(node,r),r.data.path("freeSlots").asInt()));
    }
    public synchronized int freeSlots(){return Math.max(0,Math.min(32-active.values().stream().mapToInt(Integer::intValue).sum(),all().stream().mapToInt(n->free(n,reports.get(n.getId()))).sum()));}
    public synchronized Lease acquire(Set<Long> excluded){
        if(active.values().stream().mapToInt(Integer::intValue).sum()>=32)return null;
        List<OjNode> candidates=new ArrayList<>(all());
        candidates.removeIf(n->excluded.contains(n.getId())||free(n,reports.get(n.getId()))<1);
        candidates.sort(Comparator.comparingDouble(n->score(used(n,reports.get(n.getId())),capacity(n,reports.get(n.getId())))));
        for(OjNode node:candidates) {
            try {
                JsonNode response=transport.request(node,"POST","/leases",Map.of("runtimeId",config.getRuntimeId()),3,null);
                String id=response.path("lease").asText();if(!id.matches("[\\w-]{20,100}"))throw new NodeUnavailableException();
                active.merge(node.getId(),1,Integer::sum);return new Lease(node,id);
            }catch(JudgerNodeTransport.NodeCapacityException e){refreshAsync(node);}
            catch(Exception e){Report r=reports.get(node.getId());reports.put(node.getId(),new Report(r==null?null:r.data,r==null?0:r.at,false));}
        }
        return null;
    }
    static double score(int active,int capacity){return (active+1d)/Math.max(1,capacity);}
    public Map<String,Object> overview(){
        List<Map<String,Object>> rows=all().stream().map(this::view).toList();
        return Map.of("nodes",rows,"availableSlots",freeSlots(),"platformLimit",32,
            "capacity",Math.min(32,rows.stream().mapToInt(r->Boolean.TRUE.equals(r.get("enabled"))&&"RUNNING".equals(r.get("state"))?(int)r.get("capacity"):0).sum()),
            "online",rows.stream().filter(r->Boolean.TRUE.equals(r.get("online"))).count(),"strategy","RESOURCE_BALANCED");
    }
    public Map<String,Object> view(OjNode node){
        Report r=reports.get(node.getId());boolean online=online(r);Map<String,Object> out=new LinkedHashMap<>();
        out.put("id",node.getId());out.put("name",node.getName());out.put("endpoint",node.getEndpoint());out.put("builtin",node.getBuiltin());
        out.put("enabled",node.getEnabled());out.put("maxConcurrency",node.getMaxConcurrency());out.put("online",online);
        out.put("state",!online?"OFFLINE":!compatible(r)?"INCOMPATIBLE":r.data.path("state").asText("ERROR"));
        out.put("lastSeen",r==null||r.at==0?null:r.at);out.put("capacity",capacity(node,r));out.put("active",used(node,r));out.put("freeSlots",free(node,r));
        for(String key:List.of("cpuCores","cpuPercent","memoryMb","memoryUsedMb","hostAvailableMb","hostMemoryMb","startedAt","runtimeId","error"))
            out.put(key,r==null||r.data==null?null:scalar(r.data.get(key)));
        return out;
    }
    private static Object scalar(JsonNode value){
        if(value==null||value.isNull())return null;
        if(value.isNumber())return value.numberValue();
        if(value.isBoolean())return value.booleanValue();
        return value.isTextual()?value.textValue():null;
    }
    public synchronized Map<String,Object> save(Long id,NodeRequest request){
        OjNode node=id==null?new OjNode():require(id);
        if(id==null&&all().size()>=16)throw new BusinessException(400,"最多管理 16 个判题节点");
        String endpoint=request.getEndpoint().replaceAll("/+$","");
        if(Boolean.TRUE.equals(node.getBuiltin())){
            if(!endpoint.equals(node.getEndpoint())||request.getMaxConcurrency()!=1)throw new BusinessException(400,"内置节点的地址和并发上限由容器资源固定");
        }else {
            validateEndpoint(endpoint);
            boolean change=id==null||!endpoint.equals(node.getEndpoint())||(request.getToken()!=null&&!request.getToken().isBlank());
            if(id!=null&&change&&Boolean.TRUE.equals(node.getEnabled()))throw new BusinessException(400,"请先停止节点再修改连接配置");
            if(request.getToken()!=null&&!request.getToken().isBlank()){
                if(!request.getToken().matches("[A-Za-z0-9_-]{32,256}"))throw new BusinessException(400,"节点令牌须为 32–256 位字母、数字或下划线、连字符");
                node.setTokenCipher(secrets.encrypt(request.getToken()));
            }else if(id==null)throw new BusinessException(400,"请输入独立节点的连接令牌");
            node.setEndpoint(endpoint);
        }
        node.setName(request.getName().strip());node.setMaxConcurrency(request.getMaxConcurrency());
        if(id==null){node.setBuiltin(false);node.setEnabled(true);node.setCreatedAt(LocalDateTime.now());mapper.insert(node);}else mapper.updateById(node);
        refresh(node);return view(node);
    }
    static void validateEndpoint(String value){
        try {
            URI uri=URI.create(value);
            if(!List.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getQuery()!=null||!uri.getPath().isEmpty())throw new IllegalArgumentException();
            for(InetAddress addr:InetAddress.getAllByName(uri.getHost())) {
                if(addr.isAnyLocalAddress()||addr.isLoopbackAddress()||addr.isLinkLocalAddress()||addr.isMulticastAddress())throw new IllegalArgumentException();
                if("http".equals(uri.getScheme())&&!addr.isSiteLocalAddress())throw new IllegalArgumentException();
            }
        }catch(Exception e){throw new BusinessException(400,"节点地址须为公网 HTTPS 或内网 HTTP 地址，不支持回环和链路本地地址");}
    }
    public synchronized Map<String,Object> control(Long id,String action){
        if(!List.of("start","stop","refresh").contains(action))throw new BusinessException(400,"节点操作无效");
        OjNode node=require(id);
        if("refresh".equals(action)){refresh(node);return view(node);}
        node.setEnabled("start".equals(action));mapper.updateById(node);
        try{accept(node,transport.request(node,"POST","/"+action,Map.of(),5,null));}
        catch(Exception e){reports.remove(id);throw new BusinessException(503,"节点暂时无法连接，已保存调度设置，请检查节点后重试");}
        return view(node);
    }
    public synchronized void delete(Long id){
        OjNode node=require(id);
        if(Boolean.TRUE.equals(node.getBuiltin()))throw new BusinessException(400,"内置节点不能移除，可以停止运行");
        if(Boolean.TRUE.equals(node.getEnabled())||active.getOrDefault(id,0)>0)throw new BusinessException(400,"请先停止节点并等待任务结束");
        mapper.deleteById(id);reports.remove(id);active.remove(id);
    }
}
