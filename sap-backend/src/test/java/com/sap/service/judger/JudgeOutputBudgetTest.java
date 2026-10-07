package com.sap.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.dto.judger.ProblemPack;
import com.sap.entity.judger.OjLanguage;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JudgeOutputBudgetTest {
    final ObjectMapper json=new ObjectMapper();
    OjLanguage language() {var l=new OjLanguage();l.setLanguageKey("cpp");l.setTimeLimitMs(1000);l.setMemoryLimitMb(128);return l;}
    ProblemPack.TestCase sample() {var t=new ProblemPack.TestCase();t.setName("样例🙂");t.setInput("42");t.setExpectedOutput("42");return t;}
    GoJudgeClient client() throws Exception {
        var client=mock(GoJudgeClient.class);
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(true),anyList()))
            .thenReturn(json.readTree("{\"status\":\"Accepted\",\"fileIds\":{\"answer\":\"id\"}}"));
        return client;
    }
    @Test void fiveHundredPassingSamplesStayBoundedWithoutReducingCoverage() throws Exception {
        var client=client();
        var execution=json.valueToTree(Map.of("status","Accepted","files",Map.of("stdout"," ".repeat(262140)+"42","stderr","x".repeat(65536))));
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(false),anyList())).thenReturn(execution);
        var progress=new AtomicInteger();
        var result=new JudgeEngine(client).judge(new ProblemPack(),language(),"STDIO","source",Collections.nCopies(500,sample()),true,1,progress::set);
        assertEquals("AC",result.getVerdict());assertEquals(500,result.getPassedCases());assertEquals(500,progress.get());
        assertEquals(500,result.getCases().size());assertTrue(result.isOutputTruncated());
        assertEquals(true,result.getCases().get(499).get("outputTruncated"));
        assertEquals("样例🙂",result.getCases().get(499).get("name"));
        assertTrue(json.writeValueAsBytes(result).length<JudgeOutputBudget.LIMIT+200000);
        verify(client).removeArtifact("id");
    }
    @Test void wrongAnswerAfterDisplayBudgetIsExhaustedStillFails() throws Exception {
        var client=client();var calls=new AtomicInteger();
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(false),anyList())).thenAnswer(i->
            json.valueToTree(Map.of("status","Accepted","files",Map.of("stdout",calls.incrementAndGet()==10?"wrong":" ".repeat(262140)+"42","stderr","x".repeat(65536)))));
        var result=new JudgeEngine(client).judge(new ProblemPack(),language(),"STDIO","source",Collections.nCopies(10,sample()),true,1,n->{});
        assertEquals("WA",result.getVerdict());assertEquals(9,result.getPassedCases());assertTrue(result.isOutputTruncated());
        assertEquals("WA",result.getCases().get(9).get("verdict"));
    }
    @Test void normalOutputIsByteForByteUnchanged() throws Exception {
        var client=client();
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(false),anyList()))
            .thenReturn(json.valueToTree(Map.of("status","Accepted","files",Map.of("stdout","42\n","stderr","诊断🙂\n"))));
        var result=new JudgeEngine(client).judge(new ProblemPack(),language(),"STDIO","source",List.of(sample()),true,1,n->{});
        assertFalse(result.isOutputTruncated());
        assertEquals(Map.of("name","样例🙂","verdict","AC","output","42\n","stderr","诊断🙂\n","expected","42"),result.getCases().get(0));
    }
    @Test void escapingAndUnicodeRespectBytesAndNeverSplitEmoji() throws Exception {
        var budget=new JudgeOutputBudget(13);Map<String,Object> view=new LinkedHashMap<>();
        budget.put(view,"output","中🙂\u0000more");
        assertEquals("中🙂\u0000",view.get("output"));assertEquals(true,view.get("outputTruncated"));
        budget.put(view,"stderr","anything");assertEquals("",view.get("stderr"));
        budget=new JudgeOutputBudget(6);view.clear();budget.put(view,"output","中🙂");
        assertEquals("中",view.get("output"));assertTrue(budget.isTruncated());
    }
}
