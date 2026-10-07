package com.sap.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.dto.judger.ProblemPack;
import com.sap.entity.judger.OjLanguage;
import com.sap.util.judger.OutputChecker;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JudgerBoundaryTest {
    private final ObjectMapper json=new ObjectMapper();
    private OjLanguage language() { OjLanguage l=new OjLanguage(); l.setLanguageKey("cpp"); l.setTimeLimitMs(1000); l.setMemoryLimitMb(128); return l; }
    private ProblemPack.TestCase test() { var t=new ProblemPack.TestCase(); t.setName("hidden"); t.setInput("secret-input"); t.setExpectedOutput("secret-answer"); return t; }
    @Test void normalExitWithWrongOutputIsNotAcceptedAndHiddenDataNeverReturned() throws Exception {
        GoJudgeClient client=mock(GoJudgeClient.class);
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(true),anyList()))
            .thenReturn(json.readTree("{\"status\":\"Accepted\",\"fileIds\":{\"answer\":\"compiled-id\"}}"));
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(false),anyList()))
            .thenReturn(json.readTree("{\"status\":\"Accepted\",\"files\":{\"stdout\":\"wrong\",\"stderr\":\"secret-input\"},\"time\":1000000,\"memory\":4096}"));
        var result=new JudgeEngine(client).judge(new ProblemPack(),language(),"STDIO","source",List.of(test()),false,1,n -> {});
        assertEquals("WA",result.getVerdict()); assertEquals(0,result.getPassedCases());
        String response=json.writeValueAsString(result);
        assertFalse(response.contains("secret-")); assertFalse(response.contains("wrong"));
        verify(client).removeArtifact("compiled-id");
    }
    @Test void compileFailureDoesNotExecuteAndCachedArtifactsAreRemoved() throws Exception {
        GoJudgeClient client=mock(GoJudgeClient.class);
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(true),anyList()))
            .thenReturn(json.readTree("{\"status\":\"Nonzero Exit Status\",\"files\":{\"stderr\":\"compiler error\"},\"fileIds\":{\"answer\":\"temporary-id\"}}"));
        var result=new JudgeEngine(client).judge(new ProblemPack(),language(),"STDIO","bad source",List.of(test()),false,1,n -> {});
        assertEquals("CE",result.getVerdict()); assertEquals("compiler error",result.getCompilerOutput());
        verify(client,never()).execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(false),anyList());
        verify(client).removeArtifact("temporary-id");
    }
    @Test void timeLimitCannotBeMistakenForWrongAnswerOrAcceptance() throws Exception {
        GoJudgeClient client=mock(GoJudgeClient.class);
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(true),anyList()))
            .thenReturn(json.readTree("{\"status\":\"Accepted\",\"fileIds\":{\"answer\":\"id\"}}"));
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(false),anyList()))
            .thenReturn(json.readTree("{\"status\":\"Time Limit Exceeded\",\"files\":{\"stdout\":\"secret-answer\"}}"));
        assertEquals("TLE",new JudgeEngine(client).judge(new ProblemPack(),language(),"STDIO","loop",List.of(test()),false,1,n -> {}).getVerdict());
    }
    @Test void unorderedComparatorPreservesMultiplicityAndTokenCount() {
        assertTrue(OutputChecker.matches("  1\n0 ","0 1","UNORDERED_TOKENS"));
        assertFalse(OutputChecker.matches("0 0","0 1","UNORDERED_TOKENS"));
        assertFalse(OutputChecker.matches("0 1 1","0 1","UNORDERED_TOKENS"));
        assertFalse(OutputChecker.matches("1 0","0 1","TOKENS"));
    }
    @Test void passingCasesCanTakeMoreThan120SecondsOfServiceWallTime() throws Exception {
        var clock=new java.util.concurrent.atomic.AtomicLong();var client=mock(GoJudgeClient.class);
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(true),anyList())).thenReturn(json.readTree("{\"status\":\"Accepted\",\"fileIds\":{\"answer\":\"id\"}}"));
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(false),anyList())).thenAnswer(i->{clock.addAndGet(5_000_000_000L);return json.readTree("{\"status\":\"Accepted\",\"files\":{\"stdout\":\"secret-answer\"}}");});
        var engine=new JudgeEngine(client);engine.clock=clock::get;
        var result=engine.judge(new ProblemPack(),language(),"STDIO","source",Collections.nCopies(30,test()),false,1,n->{});
        assertEquals("AC",result.getVerdict());assertEquals(30,result.getPassedCases());assertEquals(150_000_000_000L,clock.get());
    }
    @Test void protectiveTimeoutIsSystemFailureAndNeverContestPenalty() throws Exception {
        var clock=new java.util.concurrent.atomic.AtomicLong();var client=mock(GoJudgeClient.class);var cfg=new com.sap.config.judger.JudgerProperties();cfg.setMaxJudgeGroupMs(60000);
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(true),anyList())).thenReturn(json.readTree("{\"status\":\"Accepted\",\"fileIds\":{\"answer\":\"id\"}}"));
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(false),anyList())).thenAnswer(i->{clock.addAndGet(10_000_000_000L);return json.readTree("{\"status\":\"Accepted\",\"files\":{\"stdout\":\"secret-answer\"}}");});
        var engine=new JudgeEngine(client,cfg);engine.clock=clock::get;
        var result=engine.judge(new ProblemPack(),language(),"STDIO","source",Collections.nCopies(50,test()),false,1,n->{});assertEquals("SYSTEM_ERROR",result.getVerdict());
        var job=new com.sap.entity.judger.OjSubmission();job.setId(1L);job.setKind("SUBMIT");job.setStatus(result.getVerdict());job.setProblemSetItemId(1L);job.setAcceptedAt(1000L);
        var score=new OjContestScoringService().score(true,0,10000,Set.of(1L),List.of(job));assertEquals(0,score.cells().get(1L).wrong());verify(client).removeArtifact("id");
    }
    @Test void javaHeapUsesConfiguredMemoryAndMeasuredTotalLimitStillApplies() throws Exception {
        var client=mock(GoJudgeClient.class);
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(true),anyList())).thenReturn(json.readTree("{\"status\":\"Accepted\",\"fileIds\":{\"answer.jar\":\"id\"}}"));
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(false),anyList())).thenReturn(json.readTree("{\"status\":\"Accepted\",\"files\":{\"stdout\":\"secret-answer\"}}"));
        for(int memory:List.of(128,256,384)) {
            var language=language();language.setLanguageKey("java");language.setMemoryLimitMb(memory);new JudgeEngine(client).judge(new ProblemPack(),language,"STDIO","source",List.of(test()),false,1,n->{});
            verify(client).execute(argThat(args->args.contains("-Xmx"+JudgeEngine.javaHeapMb(memory)+"m")),anyMap(),anyString(),eq(1000),eq(memory),eq(false),anyList());
        }
        assertTrue(JudgeEngine.javaHeapMb(256)>120);assertTrue(JudgeEngine.javaHeapMb(384)>JudgeEngine.javaHeapMb(256));
    }
}
