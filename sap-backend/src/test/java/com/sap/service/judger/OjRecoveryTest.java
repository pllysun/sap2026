package com.sap.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.config.judger.JudgerProperties;
import com.sap.entity.judger.*;
import com.sap.mapper.judger.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OjRecoveryTest {
    static {var a=new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"");a.setCurrentNamespace("recovery");com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(a,OjSubmission.class);}
    final OjSubmissionMapper submissions=mock(OjSubmissionMapper.class);
    final OjRuntimeLogService logs=mock(OjRuntimeLogService.class);
    final JudgerNodesTest fixture=new JudgerNodesTest();
    OjService service;OjSubmission job;
    @BeforeEach void setup(){
        fixture.setup();fixture.node(1,1);fixture.pool.shutdown();
        service=new OjService(mock(OjProblemMapper.class),mock(OjLanguageMapper.class),submissions,new ObjectMapper(),mock(JudgeEngine.class),mock(GoJudgeClient.class),fixture.config,logs,fixture.pool,OjTestSupport.snapshots(),OjTestSupport.activeUsers());
        job=new OjSubmission();job.setId(7L);job.setStatus("QUEUED");when(submissions.selectList(any())).thenReturn(List.of(job));
    }
    @AfterEach void close(){service.shutdown();fixture.teardown();}
    @Test void failedDatabaseClaimCannotConsumeNodeCapacity(){
        when(submissions.update(isNull(),any())).thenThrow(new IllegalStateException("database unavailable"));
        service.poll();assertEquals(0,fixture.pool.view(fixture.rows.getFirst()).get("active"));
        verify(fixture.transport).request(any(),eq("DELETE"),startsWith("/leases/"),isNull(),eq(2),isNull());
    }
    @Test void failedInitialWorkerUpdateReleasesSlotAndCanBeRecovered() throws Exception {
        when(submissions.update(isNull(),any())).thenReturn(1).thenThrow(new IllegalStateException("database unavailable"));
        service.poll();
        verify(fixture.transport,timeout(2000)).request(any(),eq("DELETE"),startsWith("/leases/"),isNull(),eq(2),isNull());
        service.shutdown();assertEquals(0,fixture.pool.view(fixture.rows.getFirst()).get("active"));
        doReturn(1).when(submissions).update(isNull(),any());service.recoverOrphans();verify(logs).event("RECOVERED",7L,"QUEUED");
    }
    @Test void liveOwnerIsNeverRequeuedDuringRecovery() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(submissions.update(isNull(),any())).thenAnswer(i->{if(Thread.currentThread().isVirtual()){entered.countDown();release.await(2,TimeUnit.SECONDS);}return 1;});
        service.poll();assertTrue(entered.await(2,TimeUnit.SECONDS));service.recoverOrphans();verify(logs,never()).event("RECOVERED",7L,"QUEUED");release.countDown();
    }
}
