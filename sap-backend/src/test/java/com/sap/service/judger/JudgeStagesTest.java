package com.sap.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.dto.judger.ProblemPack;
import com.sap.entity.judger.OjLanguage;
import com.sap.vo.judger.JudgeStage;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class JudgeStagesTest {
    private final ObjectMapper json=new ObjectMapper();
    private OjLanguage language(){var l=new OjLanguage();l.setLanguageKey("python");l.setTimeLimitMs(1000);l.setMemoryLimitMb(128);return l;}
    private ProblemPack.TestCase test(){var t=new ProblemPack.TestCase();t.setName("private-name");t.setInput("private-input");t.setExpectedOutput("42");return t;}
    private com.fasterxml.jackson.databind.JsonNode response(String status,String output){return json.valueToTree(Map.of("status",status,"files",Map.of("stdout",output,"stderr","diagnostic")));}

    @Test void compilationAndEachActualExecutionEmitOrderedMetadataWithoutPrivateTestData() throws Exception {
        GoJudgeClient client=mock(GoJudgeClient.class);
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),anyBoolean(),anyList())).thenReturn(response("Accepted",""),response("Accepted","42"),response("Accepted","42"));
        List<JudgeStage> events=new ArrayList<>();
        var result=new JudgeEngine(client).judge(new ProblemPack(),language(),"STDIO","private-code",List.of(test(),test()),false,1,n->{},events::add);
        assertEquals("AC",result.getVerdict());assertTrue(result.getCases().isEmpty());
        assertEquals(List.of("COMPILING","COMPILED","RUNNING_CASE","CASE_FINISHED","RUNNING_CASE","CASE_FINISHED"),events.stream().map(JudgeStage::stage).toList());
        assertEquals(List.of(0,0,0,1,1,2),events.stream().map(JudgeStage::completedCases).toList());
        assertEquals(2,events.get(5).passedCases());assertEquals(2,events.get(4).caseIndex());
        String serialized=json.writeValueAsString(events);assertFalse(serialized.contains("private-"));assertFalse(serialized.contains("diagnostic"));
    }
    @Test void compilerFailureNeverClaimsSamplesRan() {
        var client=mock(GoJudgeClient.class);
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),anyBoolean(),anyList())).thenReturn(response("Nonzero Exit Status",""));
        List<JudgeStage> events=new ArrayList<>();
        var result=new JudgeEngine(client).judge(new ProblemPack(),language(),"STDIO","bad",List.of(test()),true,1,n->{},events::add);
        assertEquals("CE",result.getVerdict());assertEquals(List.of("COMPILING","COMPILE_FAILED"),events.stream().map(JudgeStage::stage).toList());assertEquals(0,events.get(1).completedCases());
    }
    @Test void wrongAnswerCountsTheFailedExecutionAndStopsBeforeLaterCases() {
        var client=mock(GoJudgeClient.class);
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),anyBoolean(),anyList())).thenReturn(response("Accepted",""),response("Accepted","41"));
        List<JudgeStage> events=new ArrayList<>();
        var result=new JudgeEngine(client).judge(new ProblemPack(),language(),"STDIO","bad",List.of(test(),test()),false,1,n->{},events::add);
        assertEquals("WA",result.getVerdict());assertEquals("WA",events.get(3).caseVerdict());assertEquals(1,events.get(3).completedCases());assertEquals(0,events.get(3).passedCases());assertEquals(4,events.size());
    }
    @Test void aBrokenProgressConsumerCannotChangeTheJudgeVerdict() {
        var client=mock(GoJudgeClient.class);
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),anyBoolean(),anyList())).thenReturn(response("Accepted","42"));
        assertEquals("AC",new JudgeEngine(client).judge(new ProblemPack(),language(),"STDIO","source",List.of(test()),true,1,n->{},s->{throw new IllegalStateException();}).getVerdict());
    }
}
