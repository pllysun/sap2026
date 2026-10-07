package com.sap.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.common.BusinessException;
import com.sap.entity.judger.OjSubmission;
import com.sap.mapper.judger.OjSubmissionMapper;
import com.sap.vo.judger.JudgeStage;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OjExecutionEventsTest {
    @org.junit.jupiter.api.BeforeAll static void metadata(){com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(),"execution-test"),OjSubmission.class);}

    private final OjSubmissionMapper mapper=mock(OjSubmissionMapper.class);
    private final OjExecutionEvents events=new OjExecutionEvents(mapper);
    private OjSubmission job(){var j=new OjSubmission();j.setId(7L);j.setUserId(1L);j.setKind("RUN");j.setStatus("RUNNING");j.setAttempt(1);j.setTotalCases(2);j.setPassedCases(0);return j;}
    @AfterEach void cleanup(){events.shutdown();}

    @Test void otherAccountsMissingJobsAndAdministrativeValidationCannotSubscribe() {
        var j=job();when(mapper.selectOne(any())).thenReturn(j);
        assertEquals(404,assertThrows(BusinessException.class,()->events.subscribe(7L,2L)).getCode());
        j.setKind("VALIDATE");assertEquals(404,assertThrows(BusinessException.class,()->events.subscribe(7L,1L)).getCode());
        when(mapper.selectOne(any())).thenReturn(null);assertEquals(404,assertThrows(BusinessException.class,()->events.subscribe(8L,1L)).getCode());
    }
    @Test void snapshotsRetainCompletedCasesAndPhaseTimingsAcrossReconnectsAndTerminalDatabaseReads() throws Exception {
        var j=job();events.publish(j,new JudgeStage("DISPATCHED",0,0,0,2,null));
        events.publish(j,new JudgeStage("COMPILING",0,0,0,2,null));events.publish(j,new JudgeStage("COMPILED",0,0,0,2,null));
        var first=events.publish(j,new JudgeStage("CASE_FINISHED",1,1,0,2,"WA"));
        var snapshot=events.snapshot(j);assertSame(first,snapshot);assertEquals("WA",snapshot.cases().get(0).verdict());assertTrue(snapshot.timings().containsKey("COMPILED"));
        j.setStatus("WA");var terminal=events.snapshot(j);assertTrue(terminal.terminal());assertEquals(1,terminal.completedCases());assertEquals(first.cases(),terminal.cases());assertTrue(terminal.sequence()>first.sequence());
        j.setCode("private-source");j.setSnapshotJson("private-test");j.setResultJson("private-output");
        String payload=new ObjectMapper().writeValueAsString(terminal);assertFalse(payload.contains("private-"));
    }
    @Test void nodeReassignmentResetsSampleResultsAndCompileTimingsButKeepsQueueTime() {
        var j=job();j.setStatus("QUEUED");events.publish(j,new JudgeStage("QUEUED",0,0,0,0,null));j.setStatus("RUNNING");
        events.publish(j,new JudgeStage("COMPILING",0,0,0,2,null));events.publish(j,new JudgeStage("CASE_FINISHED",1,1,1,2,"AC"));j.setAttempt(2);
        var reset=events.publish(j,new JudgeStage("DISPATCHED",0,0,0,2,null));
        assertTrue(reset.cases().isEmpty());assertFalse(reset.timings().containsKey("COMPILING"));assertTrue(reset.timings().containsKey("QUEUED"));assertEquals(0,reset.completedCases());
    }
    @Test void activeConnectionsAreBoundedPerAccountAndCanBeReleasedForReconnecting() {
        var j=job();when(mapper.selectOne(any())).thenReturn(j);
        events.subscribe(7L,1L);events.subscribe(7L,1L);events.subscribe(7L,1L);
        assertEquals(429,assertThrows(BusinessException.class,()->events.subscribe(7L,1L)).getCode());
        events.reset(7L);assertNotNull(events.subscribe(7L,1L));
    }
    @Test void oldCompletedJobsAndApplicationRestartUseDatabaseStatusWithoutInventingCompileTimings() {
        var j=job();j.setStatus("AC");j.setPassedCases(2);
        var snapshot=events.snapshot(j);assertTrue(snapshot.terminal());assertEquals("AC",snapshot.status());assertFalse(snapshot.timings().containsKey("COMPILING"));assertTrue(snapshot.cases().isEmpty());
        events.reset(7L);j.setStatus("QUEUED");j.setPassedCases(0);assertEquals("QUEUED",events.snapshot(j).stage());
    }
}
