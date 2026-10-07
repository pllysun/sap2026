package com.sap.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.common.BusinessException;
import com.sap.config.judger.JudgerProperties;
import com.sap.dto.judger.*;
import com.sap.entity.judger.*;
import com.sap.mapper.judger.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OjPrivacyTest {
    static {
        var assistant=new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");
        assistant.setCurrentNamespace("com.sap.test.judger");
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,OjSubmission.class);
    }
    private final OjProblemMapper problems=mock(OjProblemMapper.class);
    private final OjLanguageMapper languages=mock(OjLanguageMapper.class);
    private final OjSubmissionMapper submissions=mock(OjSubmissionMapper.class);
    private final JudgerProperties config=new JudgerProperties();
    private final ObjectMapper json=new ObjectMapper();
    private final OjService service=new OjService(problems,languages,submissions,json,mock(JudgeEngine.class),mock(GoJudgeClient.class),config,mock(OjRuntimeLogService.class),mock(JudgerNodes.class),OjTestSupport.snapshots(),OjTestSupport.activeUsers());
    @AfterEach void close() { service.shutdown(); }
    private OjProblem published() {
        config.setEnabled(true);
        OjLanguage l=new OjLanguage(); l.setLanguageKey("cpp"); l.setTimeLimitMs(1000); l.setMemoryLimitMb(128);
        when(languages.selectList(any())).thenReturn(List.of(l));
        ProblemPack pack=new ProblemPack(); pack.setTitle("Example"); pack.setSourcePlatform("LeetCode"); pack.setSourceUrl("https://leetcode.com/problems/two-sum/");
        var profile=new ProblemPack.Profile(); profile.setStarterStdio("public starter"); profile.setFunctionDriver("PRIVATE_DRIVER"); pack.getProfiles().put("cpp",profile);
        pack.getReferences().put("cpp",Map.of("STDIO","PRIVATE_REFERENCE"));
        var hidden=new ProblemPack.TestCase(); hidden.setName("hidden"); hidden.setInput("PRIVATE_INPUT"); hidden.setExpectedOutput("PRIVATE_ANSWER");
        var sample=new ProblemPack.TestCase(); sample.setName("sample"); sample.setInput("sample"); sample.setExpectedOutput("sample answer"); sample.setSample(true); pack.setCases(List.of(hidden,sample));
        OjProblem p=new OjProblem(); p.setId(1L); p.setStatus("PUBLISHED"); p.setPackJson(service.write(pack));
        p.setValidationSignature(ReflectionTestUtils.invokeMethod(service,"signature")); when(problems.selectById(1L)).thenReturn(p); return p;
    }
    @Test void userDetailOnlyIncludesSamplesAndStarterTemplates() {
        published(); String response=service.write(service.detail(1L,false));
        assertTrue(response.contains("public starter")); assertTrue(response.contains("sample answer"));
        assertFalse(response.contains("PRIVATE_")); assertFalse(response.contains("references")); assertFalse(response.contains("functionDriver"));
    }
    @Test void disabledOrUnvalidatedProblemCannotBeRead() {
        OjProblem p=published(); p.setStatus("DISABLED"); assertEquals(404,assertThrows(BusinessException.class,()->service.detail(1L,false)).getCode());
        p.setStatus("CONTEST_ONLY"); assertEquals(404,assertThrows(BusinessException.class,()->service.detail(1L,false)).getCode());
        var request=new SubmitRequest();request.setProblemId(1L);request.setMode("STDIO");request.setLanguage("cpp");request.setCode("private");
        assertEquals(404,assertThrows(BusinessException.class,()->service.enqueue(10L,request,"SUBMIT")).getCode());
        p.setStatus("PUBLISHED"); p.setValidationSignature("old settings"); assertThrows(BusinessException.class,()->service.detail(1L,false));
    }
    @Test void submissionOwnershipAndPrivateSnapshotAreProtected() {
        OjSubmission job=new OjSubmission(); job.setId(7L); job.setUserId(10L); job.setKind("SUBMIT"); job.setSnapshotJson("PRIVATE_INPUT"); job.setResultJson("{}");
        when(submissions.selectOne(any())).thenReturn(job);
        assertEquals(404,assertThrows(BusinessException.class,()->service.job(7L,11L,false)).getCode());
        assertFalse(service.write(service.job(7L,10L,false)).contains("PRIVATE_INPUT"));
        job.setKind("VALIDATE"); assertThrows(BusinessException.class,()->service.job(7L,10L,false));
    }
    @Test void orderChangesDoNotInvalidateJudgingAndRejectStaleOrDuplicateLists() {
        var assistant=new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), "");assistant.setCurrentNamespace("com.sap.test.order");com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(assistant,OjProblem.class);
        OjProblem first=new OjProblem();first.setId(1L);first.setSortOrder(0L);first.setRevision(4L);first.setValidationSignature("valid");
        OjProblem second=new OjProblem();second.setId(2L);second.setSortOrder(1L);
        when(problems.selectList(any())).thenAnswer(invocation->new ArrayList<>(List.of(first,second)));
        assertEquals(409,assertThrows(BusinessException.class,()->service.reorder(List.of(2L,1L),List.of(1L,2L))).getCode());
        assertThrows(BusinessException.class,()->service.reorder(List.of(1L,2L),List.of(1L,1L)));
        verify(problems,never()).update(any(OjProblem.class),any());
        service.reorder(List.of(1L,2L),List.of(2L,1L));
        var wrappers=org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(problems,times(2)).update(isNull(),wrappers.capture());
        for(var wrapper:wrappers.getAllValues()) {String set=((com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<?>)wrapper).getSqlSet();assertTrue(set.contains("sort_order"));assertFalse(set.contains("revision"));assertFalse(set.contains("validation"));}
        assertEquals(4L,first.getRevision());assertEquals("valid",first.getValidationSignature());
    }
    @Test void forbiddenModeIsRejectedBeforeCreatingAJob() {
        published(); SubmitRequest request=new SubmitRequest(); request.setProblemId(1L); request.setLanguage("cpp"); request.setMode("FUNCTION"); request.setCode("source");
        assertEquals(400,assertThrows(BusinessException.class,()->service.enqueue(10L,request,"SUBMIT")).getCode());
        verify(submissions,never()).insert(any(OjSubmission.class));
    }
}
