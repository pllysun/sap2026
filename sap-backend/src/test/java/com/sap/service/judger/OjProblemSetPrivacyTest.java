package com.sap.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.common.BusinessException;
import com.sap.config.judger.JudgerProperties;
import com.sap.entity.User;
import com.sap.entity.judger.*;
import com.sap.mapper.UserMapper;
import com.sap.mapper.judger.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;

class OjProblemSetPrivacyTest {
    static {
        var a=new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");a.setCurrentNamespace("com.sap.test.sets");
        for(Class<?> c:List.of(OjProblemSet.class,OjProblemSetItem.class,OjProblemSetParticipant.class,OjProblemSetAudit.class,OjSubmission.class))com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(a,c);
    }
    private final OjProblemSetMapper sets=mock(OjProblemSetMapper.class);
    private final OjProblemSetItemMapper items=mock(OjProblemSetItemMapper.class);
    private final OjProblemSetParticipantMapper participants=mock(OjProblemSetParticipantMapper.class);
    private final OjProblemSetAuditMapper audits=mock(OjProblemSetAuditMapper.class);
    private final OjSubmissionMapper submissions=mock(OjSubmissionMapper.class);
    private final UserMapper users=mock(UserMapper.class);
    private final OjService oj=mock(OjService.class);
    private final OjProblemSetService service=new OjProblemSetService(sets,items,participants,audits,submissions,users,oj,new OjContestScoringService(),new JudgerProperties(),mock(JdbcTemplate.class));
    private final OjProblemSet set=new OjProblemSet();
    private final OjSubmission submission=new OjSubmission();
    OjProblemSetPrivacyTest(){
        set.setId(1L);set.setName("test");set.setStatus("PUBLISHED");set.setMode("CONTEST");set.setAccessType("PUBLIC");set.setLanguagesJson("[]");set.setStudentsJson("[]");set.setPublicCode(false);set.setStartsAt(System.currentTimeMillis()-120_000);set.setEndsAt(System.currentTimeMillis()+120_000);
        when(sets.selectById(1L)).thenReturn(set);when(sets.selectOne(any())).thenReturn(set);
        var u=new User();u.setId(10L);u.setStudentId("2026");u.setStatus(1);when(users.selectById(10L)).thenReturn(u);
        when(items.selectList(any())).thenReturn(List.of());when(submissions.selectList(any())).thenReturn(List.of());when(audits.selectList(any())).thenReturn(List.of());
        when(oj.read(anyString(),eq(List.class))).thenAnswer(i->new ObjectMapper().readValue((String)i.getArgument(0),List.class));
        submission.setId(7L);submission.setProblemSetId(1L);submission.setUserId(20L);submission.setKind("SUBMIT");submission.setCode("PRIVATE_SOURCE");submission.setSnapshotJson("PRIVATE_TESTS");submission.setLanguage("cpp");submission.setMode("STDIO");submission.setStatus("AC");
        when(submissions.selectById(7L)).thenReturn(submission);
    }
    private void joined(){var p=new OjProblemSetParticipant();p.setProblemSetId(1L);p.setUserId(10L);p.setDisqualified(false);when(participants.selectOne(any())).thenReturn(p);}
    @Test void endedContestCannotResumeEvenAfterPublicCodeWasClosed(){
        set.setEndsAt(System.currentTimeMillis()-1000);set.setPublicCode(false);
        assertThrows(BusinessException.class,()->service.extend(1L,System.currentTimeMillis()+10000,"延长",10L));verify(sets,never()).update(isNull(),any());
    }
    @Test void upcomingAndOngoingContestsCanExtendButPublishedCodeBlocksIt(){
        set.setRevision(1L);service.extend(1L,set.getEndsAt()+10000,"延长",10L);verify(sets).update(isNull(),any());
        set.setPublicCode(true);assertThrows(BusinessException.class,()->service.extend(1L,set.getEndsAt()+20000,"延长",10L));
    }
    @Test void contestSourceIsPrivateUntilAdminOpensAfterEnd(){
        joined();assertEquals(403,assertThrows(BusinessException.class,()->service.source(1L,7L,10L,false)).getCode());
        set.setPublicCode(true);assertThrows(BusinessException.class,()->service.source(1L,7L,10L,false));
        set.setEndsAt(System.currentTimeMillis()-1);
        var source=service.source(1L,7L,10L,false);assertEquals("PRIVATE_SOURCE",source.get("code"));assertFalse(source.toString().contains("PRIVATE_TESTS"));
        set.setPublicCode(false);assertThrows(BusinessException.class,()->service.source(1L,7L,10L,false));assertEquals("PRIVATE_SOURCE",service.source(1L,7L,10L,true).get("code"));
    }
    @Test void practiceSharesFormalSubmissionsOnlyWithParticipants(){
        set.setMode("PRACTICE");assertThrows(BusinessException.class,()->service.source(1L,7L,10L,false));joined();assertEquals("PRIVATE_SOURCE",service.source(1L,7L,10L,false).get("code"));
        submission.setKind("RUN");assertThrows(BusinessException.class,()->service.source(1L,7L,10L,false));submission.setUserId(10L);assertEquals("PRIVATE_SOURCE",service.source(1L,7L,10L,false).get("code"));
        submission.setKind("VALIDATE");assertEquals(404,assertThrows(BusinessException.class,()->service.source(1L,7L,10L,true)).getCode());
    }
    @Test void draftAndWhitelistCannotBeBypassedWithDirectUrls(){
        set.setStatus("DRAFT");assertEquals(404,assertThrows(BusinessException.class,()->service.detail(1L,10L,false)).getCode());assertEquals("DRAFT",service.detail(1L,10L,true).get("status"));
        set.setStatus("PUBLISHED");set.setAccessType("WHITELIST");set.setStudentsJson("[\"another\"]");assertEquals(404,assertThrows(BusinessException.class,()->service.source(1L,7L,10L,false)).getCode());
    }
    @Test void contestTitlesAndProblemContentsStayHiddenBeforeStart(){
        set.setStartsAt(System.currentTimeMillis()+120_000);var i=new OjProblemSetItem();i.setId(9L);i.setProblemSetId(1L);i.setTitle("PRIVATE_TITLE");i.setActive(true);i.setTagsJson("[]");i.setModesJson("[]");
        when(items.selectList(any())).thenReturn(List.of(i));
        assertFalse(service.detail(1L,10L,false).toString().contains("PRIVATE_TITLE"));assertTrue(service.detail(1L,10L,true).toString().contains("PRIVATE_TITLE"));
        assertEquals(403,assertThrows(BusinessException.class,()->service.problem(1L,9L,10L)).getCode());verify(items,never()).selectById(any());
    }
}
