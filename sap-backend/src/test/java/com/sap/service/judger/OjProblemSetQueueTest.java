package com.sap.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.common.BusinessException;
import com.sap.config.judger.JudgerProperties;
import com.sap.dto.judger.*;
import com.sap.entity.judger.*;
import com.sap.mapper.judger.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class OjProblemSetQueueTest {
    static {var a=new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");a.setCurrentNamespace("com.sap.test.queue");com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(a,OjSubmission.class);}
    private final OjSubmissionMapper submissions=mock(OjSubmissionMapper.class);
    private final JudgerNodes nodes=mock(JudgerNodes.class);
    private final JudgerProperties config=new JudgerProperties();
    private final OjService service=new OjService(mock(OjProblemMapper.class),mock(OjLanguageMapper.class),submissions,new ObjectMapper(),mock(JudgeEngine.class),mock(GoJudgeClient.class),config,mock(OjRuntimeLogService.class),nodes,OjTestSupport.snapshots(),OjTestSupport.activeUsers());
    private final OjProblem p=new OjProblem();private final OjLanguage lang=new OjLanguage();private final SubmitRequest request=new SubmitRequest();
    OjProblemSetQueueTest(){config.setEnabled(true);p.setId(1L);p.setRevision(1L);lang.setLanguageKey("cpp");request.setProblemId(1L);request.setLanguage("cpp");request.setMode("STDIO");request.setCode("int main(){}");request.setRequestKey("request-1234567890");when(submissions.selectCount(any())).thenReturn(0L);when(submissions.insert(any(OjSubmission.class))).thenAnswer(i->{((OjSubmission)i.getArgument(0)).setId(7L);return 1;});}
    @AfterEach void close(){service.shutdown();}
    private Map<String,Object> enqueue(Long deadline){return service.enqueueFrozen(10L,2L,3L,p,new ProblemPack(),lang,"verified",request,"SUBMIT",1L,deadline);}
    @Test void disabledAccountCannotSubmitOrRunWithAnExistingSession(){
        var users=(com.sap.mapper.UserMapper)org.springframework.test.util.ReflectionTestUtils.getField(service,"users");var disabled=new com.sap.entity.User();disabled.setId(10L);disabled.setStatus(0);when(users.selectById(10L)).thenReturn(disabled);
        assertEquals(403,assertThrows(BusinessException.class,()->service.enqueue(10L,request,"RUN")).getCode());assertEquals(403,assertThrows(BusinessException.class,()->service.enqueue(10L,request,"SUBMIT")).getCode());assertEquals(403,assertThrows(BusinessException.class,()->enqueue(null)).getCode());verifyNoInteractions(nodes);verify(submissions,never()).insert(any(OjSubmission.class));
    }
    @Test void busyOnlineNodesAcceptDurableJobsWithCurrentReceptionTime(){
        when(nodes.overview()).thenReturn(Map.of("capacity",8));when(nodes.freeSlots()).thenReturn(0);
        long before=System.currentTimeMillis();var result=enqueue(before+10_000);
        assertEquals("QUEUED",result.get("status"));assertEquals(2L,result.get("problemSetId"));assertTrue(((Long)result.get("acceptedAt"))>=before);
        var captured=org.mockito.ArgumentCaptor.forClass(OjSubmission.class);verify(submissions).insert(captured.capture());
        assertEquals("request-1234567890",captured.getValue().getRequestKey());assertEquals("verified",captured.getValue().getValidationSignature());assertTrue(captured.getValue().getSnapshotJson().contains("int main")==false);
    }
    @Test void offlineFullQueueAccountLimitAndCutoffNeverCreateAcceptedRecord(){
        when(nodes.overview()).thenReturn(Map.of("capacity",0));assertEquals(503,assertThrows(BusinessException.class,()->enqueue(null)).getCode());
        when(nodes.overview()).thenReturn(Map.of("capacity",8));when(submissions.selectCount(any())).thenReturn(2L);assertEquals(429,assertThrows(BusinessException.class,()->enqueue(null)).getCode());
        when(submissions.selectCount(any())).thenReturn(0L,50L);assertEquals(429,assertThrows(BusinessException.class,()->enqueue(null)).getCode());
        when(submissions.selectCount(any())).thenReturn(0L);assertEquals(400,assertThrows(BusinessException.class,()->enqueue(System.currentTimeMillis()-1)).getCode());verify(submissions,never()).insert(any(OjSubmission.class));
    }
    @Test void identicalRetriesReplayButChangedPayloadOrContextIsRejected(){
        var j=new OjSubmission();j.setId(7L);j.setUserId(10L);j.setProblemSetId(2L);j.setProblemSetItemId(3L);j.setLanguage("cpp");j.setMode("STDIO");j.setKind("SUBMIT");j.setCode(request.getCode());j.setStatus("AC");j.setSnapshotJson("{\"custom\":false,\"input\":\"\"}");
        when(submissions.selectOne(any())).thenReturn(j);assertEquals(7L,service.replay(10L,2L,3L,request,"SUBMIT").get("id"));
        assertEquals(409,assertThrows(BusinessException.class,()->service.replay(10L,2L,4L,request,"SUBMIT")).getCode());
        request.setCode("changed");assertEquals(409,assertThrows(BusinessException.class,()->service.replay(10L,2L,3L,request,"SUBMIT")).getCode());verify(submissions,never()).insert(any(OjSubmission.class));
    }
}
