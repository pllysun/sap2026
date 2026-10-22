package com.sap.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.dto.judger.ProblemPack;
import com.sap.entity.judger.OjLanguage;
import com.sap.util.judger.OutputChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class MultipleAnswersTest {
    private final ObjectMapper json=new ObjectMapper();
    private ProblemPack.TestCase test() {
        var t=new ProblemPack.TestCase();t.setName("peak");t.setInput("1 2 1 3 5 6 4");t.setExpectedOutput("1");return t;
    }
    private com.fasterxml.jackson.databind.JsonNode response(String status,String output) {
        return json.valueToTree(Map.of("status",status,"files",Map.of("stdout",output,"stderr","private-checker-diagnostic"),
            "fileIds",Map.of("answer","binary","answer.jar","jar")));
    }
    private OjLanguage language(String key) {var l=new OjLanguage();l.setLanguageKey(key);l.setTimeLimitMs(1000);l.setMemoryLimitMb(128);return l;}
    @Test void finiteAlternativesKeepTokenOrderAndMultiplicity() {
        assertTrue(OutputChecker.matches("5\n","1","ANY_OF",List.of("5")));
        assertTrue(OutputChecker.matches("1 2","1 2","ANY_OF",List.of()));
        assertFalse(OutputChecker.matches("2 1","1 2","ANY_OF",List.of("3 4")));
        assertFalse(OutputChecker.matches("1 1","1","ANY_OF",List.of("5")));
        assertFalse(OutputChecker.matches("1","1","UNKNOWN"));
    }
    @Test void unorderedLinesPreserveGroupsAndDuplicateCounts() {
        assertTrue(OutputChecker.matches("b c\na a\n", "a  a\r\nb c", "UNORDERED_LINES"));
        assertFalse(OutputChecker.matches("a c\na b", "a b\na c\na c", "UNORDERED_LINES"));
        assertFalse(OutputChecker.matches("b a", "a b", "UNORDERED_LINES"));
        assertTrue(OutputChecker.matches("2 1 1", "1 2 1", "UNORDERED_TOKENS"));
        assertFalse(OutputChecker.matches("1 2", "1 2 1", "UNORDERED_TOKENS"));
    }
    @Test void transportUsesUtf8ByteFramingAndCannotTurnOutputIntoSource() {
        String input="中\n\u0000文",actual="'); raise Exception('injected') #\n",expected="答案";
        String framed=SpecialJudge.input(input,actual,expected);
        byte[] bytes=framed.getBytes(StandardCharsets.UTF_8);int header=framed.indexOf('\n');
        int[] lengths=Arrays.stream(framed.substring(0,header).split(" ")).mapToInt(Integer::parseInt).toArray();
        int start=header+1;
        for(int i=0;i<3;i++) {String value=new String(bytes,start,lengths[i],StandardCharsets.UTF_8);assertEquals(List.of(input,actual,expected).get(i),value);start+=lengths[i];}
        assertEquals(bytes.length,start);
        assertFalse(SpecialJudge.source("def check(a,b,c): return True").contains(actual));
        assertTrue(SpecialJudge.input("\u0000".repeat(524287),"x".repeat(262144),"1").length()<1048576);
        assertThrows(IllegalArgumentException.class,()->SpecialJudge.input("x".repeat(524289),"",""));
    }
    @ParameterizedTest @CsvSource({"c,STDIO","c,FUNCTION","cpp,STDIO","cpp,FUNCTION","java,STDIO","java,FUNCTION","python,STDIO","python,FUNCTION","rust,STDIO","rust,FUNCTION"})
    void eachLanguageAndModeChecksEveryCaseInAllThreeRounds(String key,String mode) {
        var client=mock(GoJudgeClient.class);var checks=new AtomicInteger();
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),anyBoolean(),anyList())).thenAnswer(call->{
            if ((Boolean)call.getArgument(5)) return response("Accepted","");
            Map<?,?> files=call.getArgument(1);
            if(files.toString().contains("import base64")){checks.incrementAndGet();assertEquals(1000,(Integer)call.getArgument(3));assertEquals(128,(Integer)call.getArgument(4));return response("Accepted","AC\n");}
            return response("Accepted","5\n");
        });
        var pack=new ProblemPack();pack.setChecker("SPECIAL_JUDGE");pack.setCheckerSource("def check(a,b,c): return True");
        var profile=new ProblemPack.Profile();profile.setFunctionDriver("__USER_CODE__");pack.getProfiles().put(key,profile);
        var result=new JudgeEngine(client).judge(pack,language(key),mode,"candidate",List.of(test(),test()),false,3,n->{});
        assertEquals("AC",result.getVerdict());assertEquals(6,result.getPassedCases());assertEquals(6,checks.get());assertTrue(result.getCases().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"Nonzero Exit Status","Time Limit Exceeded","Memory Limit Exceeded","Output Limit Exceeded"})
    void checkerFailuresAreSystemErrorsRatherThanWrongAnswers(String status) throws Exception {
        var client=mock(GoJudgeClient.class);
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),anyBoolean(),anyList())).thenReturn(response("Accepted",""),response("Accepted","5"),response(status,"AC"));
        var p=new ProblemPack();p.setChecker("SPECIAL_JUDGE");p.setCheckerSource("bad-checker");
        var result=new JudgeEngine(client).judge(p,language("python"),"STDIO","candidate",List.of(test()),true,1,n->{});
        assertEquals("SYSTEM_ERROR",result.getVerdict());assertTrue(result.getMessage().contains("校验器"));
        assertFalse(json.writeValueAsString(result).contains("bad-checker"));
    }
    @Test void validCheckerCanRejectAndMalformedProtocolFailsClosed() {
        for(String decision:List.of("WA","","true","AC\nWA")) {
            var client=mock(GoJudgeClient.class);
            when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),anyBoolean(),anyList())).thenReturn(response("Accepted",""),response("Accepted","5"),response("Accepted",decision));
            var p=new ProblemPack();p.setChecker("SPECIAL_JUDGE");p.setCheckerSource("checker");
            var result=new JudgeEngine(client).judge(p,language("python"),"STDIO","candidate",List.of(test()),false,1,n->{});
            assertEquals("WA".equals(decision)?"WA":"SYSTEM_ERROR",result.getVerdict());
        }
    }
    @Test void customInputWithoutExpectedOutputDoesNotInventAVerdict() {
        var client=mock(GoJudgeClient.class);
        when(client.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),anyBoolean(),anyList())).thenReturn(response("Accepted",""),response("Accepted","5"));
        var p=new ProblemPack();p.setChecker("SPECIAL_JUDGE");var t=test();t.setExpectedOutput(null);
        assertEquals("AC",new JudgeEngine(client).judge(p,language("python"),"STDIO","source",List.of(t),true,1,n->{}).getVerdict());
        verify(client,times(2)).execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),anyBoolean(),anyList());
    }
}
