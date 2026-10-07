package com.sap.service.judger;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sap.common.BusinessException;
import com.sap.entity.judger.OjSubmission;
import com.sap.mapper.judger.OjSubmissionMapper;
import com.sap.vo.judger.JudgeStage;
import com.sap.vo.judger.OjExecutionProgress;
import jakarta.annotation.PreDestroy;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded, ephemeral progress transport. Durable submissions remain the source of truth. */
@Service
public class OjExecutionEvents {
    private static final int MAX_STATES=256, MAX_CONNECTIONS=128, PER_USER=3;
    private final OjSubmissionMapper submissions;
    private final String streamId=UUID.randomUUID().toString();
    private final AtomicLong sequence=new AtomicLong();
    private final Map<Long,OjExecutionProgress> states=new LinkedHashMap<>();
    private final Set<Subscription> subscriptions=new HashSet<>();
    private final ExecutorService writers=Executors.newVirtualThreadPerTaskExecutor();

    public OjExecutionEvents(OjSubmissionMapper submissions) { this.submissions=submissions; }

    /** One small ownership query, including on reconnect. No code or snapshots are read. */
    public SseEmitter subscribe(Long id, Long user) {
        OjSubmission job=submissions.selectOne(new LambdaQueryWrapper<OjSubmission>()
            .eq(OjSubmission::getId,id).select(OjSubmission::getId,OjSubmission::getUserId,
                OjSubmission::getKind,OjSubmission::getStatus,OjSubmission::getAttempt,
                OjSubmission::getTotalCases,OjSubmission::getPassedCases));
        if(job==null || !Objects.equals(user,job.getUserId()) || "VALIDATE".equals(job.getKind()))
            throw new BusinessException(404,"提交记录不存在");
        SseEmitter emitter=new SseEmitter(60_000L);
        Subscription connection=new Subscription(id,user,emitter);
        synchronized(this) {
            if(subscriptions.size()>=MAX_CONNECTIONS || subscriptions.stream().filter(s->s.user.equals(user)).count()>=PER_USER)
                throw new BusinessException(429,"进度连接过多，请关闭其他做题页面后重试");
            subscriptions.add(connection);
            connection.offer(snapshot(job));
        }
        emitter.onCompletion(connection::close);
        emitter.onTimeout(connection::close);
        emitter.onError(e->connection.close());
        try { connection.writer=writers.submit(connection::write); }
        catch(RejectedExecutionException e) { connection.close(); throw new BusinessException(503,"进度服务暂不可用"); }
        return emitter;
    }

    public synchronized OjExecutionProgress snapshot(OjSubmission job) {
        OjExecutionProgress current=states.get(job.getId());
        // A terminal database result also covers missed publications or an application restart.
        if(!pending(job.getStatus()) && (current==null || !current.terminal() || !job.getStatus().equals(current.status())))
            return publish(job,new JudgeStage("FINISHED",0,number(job.getPassedCases()),number(job.getPassedCases()),number(job.getTotalCases()),null));
        if(current!=null) return current;
        return publish(job,new JudgeStage("QUEUED".equals(job.getStatus())?"QUEUED":"DISPATCHED",
            0,number(job.getPassedCases()),number(job.getPassedCases()),number(job.getTotalCases()),null));
    }

    public synchronized OjExecutionProgress publish(OjSubmission job, JudgeStage stage) {
        if(job.getId()==null || "VALIDATE".equals(job.getKind())) return null;
        OjExecutionProgress previous=states.get(job.getId());
        long now=System.currentTimeMillis();
        int attempt=number(job.getAttempt());
        boolean reset=previous==null || "QUEUED".equals(stage.stage()) || attempt!=previous.attempt();
        Map<String,Long> timings=new LinkedHashMap<>();
        List<OjExecutionProgress.CaseState> cases=new ArrayList<>();
        if(previous!=null) {
            if(reset) { if(previous.timings().containsKey("QUEUED"))timings.put("QUEUED",previous.timings().get("QUEUED")); }
            else { timings.putAll(previous.timings()); cases.addAll(previous.cases()); }
        }
        if(Set.of("QUEUED","DISPATCHED","COMPILING","COMPILED","COMPILE_FAILED","FINISHED").contains(stage.stage()))
            timings.putIfAbsent(stage.stage(),now);
        if("CASE_FINISHED".equals(stage.stage()) && cases.size()<500)
            cases.add(new OjExecutionProgress.CaseState(stage.caseIndex(),stage.caseVerdict()));
        String status="FINISHED".equals(stage.stage())?job.getStatus():"QUEUED".equals(stage.stage())?"QUEUED":"RUNNING";
        int completed=stage.completedCases();
        if("FINISHED".equals(stage.stage()) && previous!=null && !reset)completed=Math.max(completed,previous.completedCases());
        OjExecutionProgress progress=new OjExecutionProgress(streamId,sequence.incrementAndGet(),job.getId(),stage.stage(),status,
            attempt,stage.caseIndex(),completed,stage.passedCases(),stage.totalCases(),stage.caseVerdict(),now,
            Collections.unmodifiableMap(timings),List.copyOf(cases));
        states.put(job.getId(),progress);
        while(states.size()>MAX_STATES)states.remove(states.keySet().iterator().next());
        // Never perform a socket write on a judge worker. Slow clients receive the latest cumulative state.
        subscriptions.stream().filter(s->s.id.equals(job.getId())).forEach(s->s.offer(progress));
        return progress;
    }

    public synchronized void reset(Long id) {
        states.remove(id);
        for(Subscription s:List.copyOf(subscriptions))if(s.id.equals(id))s.close();
    }
    @Scheduled(fixedDelay=60_000) public synchronized void prune() {
        long now=System.currentTimeMillis();
        states.values().removeIf(s->now-s.updatedAt()>(s.terminal()?600_000:3_600_000));
    }
    @PreDestroy public synchronized void shutdown() {
        for(Subscription s:List.copyOf(subscriptions))s.close();
        writers.shutdownNow();states.clear();
    }
    private static boolean pending(String status) {return "QUEUED".equals(status)||"RUNNING".equals(status);}
    private static int number(Integer n) {return n==null?0:n;}

    private final class Subscription {
        final Long id,user; final SseEmitter emitter;
        final ArrayBlockingQueue<OjExecutionProgress> latest=new ArrayBlockingQueue<>(1);
        volatile boolean closed; volatile Future<?> writer;
        Subscription(Long id,Long user,SseEmitter emitter) {this.id=id;this.user=user;this.emitter=emitter;}
        void offer(OjExecutionProgress value) { if(!latest.offer(value)) {latest.poll();latest.offer(value);} }
        void write() {
            long sent=0;
            try {
                while(!closed) {
                    OjExecutionProgress value=latest.poll(10,TimeUnit.SECONDS);
                    if(value==null)emitter.send(SseEmitter.event().comment("heartbeat"));
                    else if(value.sequence()>sent) {
                        emitter.send(SseEmitter.event().id(streamId+":"+value.sequence()).name("progress").data(value,MediaType.APPLICATION_JSON));
                        sent=value.sequence();
                        if(value.terminal())break;
                    }
                }
            } catch(Exception ignored) { /* Reconnect or ordinary polling recovers progress. */ }
            finally { close(); }
        }
        void close() {
            synchronized(OjExecutionEvents.this) { if(closed)return;closed=true;subscriptions.remove(this); }
            Future<?> task=writer;if(task!=null)task.cancel(true);
            emitter.complete();
        }
    }
}
