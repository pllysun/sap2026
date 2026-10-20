package io.sapoj.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapoj.config.judger.JudgerProperties;
import io.sapoj.dto.judger.ProblemPack;
import io.sapoj.entity.judger.OjLanguage;
import io.sapoj.entity.judger.OjNode;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses the real command builder; an optional export is replayed through Agent HTTP tests. */
class JudgeProtocolTest {
    @Test void fiveLanguagesBothModesAndRepeatedValidationKeepTheirProtocol() throws Exception {
        var json=new ObjectMapper();var config=new JudgerProperties();config.setEnabled(true);
        var nodes=mock(JudgerNodes.class);var transport=mock(JudgerNodeTransport.class);var node=new OjNode();
        var constructor=JudgerNodes.Lease.class.getDeclaredConstructor(JudgerNodes.class,OjNode.class,String.class);
        constructor.setAccessible(true);
        when(nodes.currentLease()).thenReturn(constructor.newInstance(nodes,node,"local-test-lease-yyyyyyyyyyyyyyyyyyyyyyyy"));
        List<Map<String,Object>> groups=new ArrayList<>();List<Map<String,Object>> requests=new ArrayList<>();
        when(transport.request(eq(node),anyString(),anyString(),any(),anyInt(),anyString())).thenAnswer(i->{
            String method=i.getArgument(1),path=i.getArgument(2);Object body=i.getArgument(3);
            var request=new LinkedHashMap<String,Object>();request.put("method",method);request.put("path",path);if(body!=null)request.put("body",body);requests.add(request);
            if(method.equals("DELETE"))return json.createObjectNode();
            var command=json.valueToTree(body).path("cmd").get(0);
            Map<String,String> ids=new LinkedHashMap<>();command.path("copyOutCached").forEach(n->ids.put(n.asText(),"artifact-"+n.asText().replace('.','-')));
            return json.valueToTree(List.of(Map.of("status","Accepted","fileIds",ids,"files",Map.of("stdout",command.path("files").get(0).path("content").asText(),"stderr",""))));
        });
        var client=new GoJudgeClient(config,json,nodes,transport);
        var engine=new JudgeEngine(client);
        for(String key:List.of("c","cpp","java","python","rust"))for(String mode:List.of("STDIO","FUNCTION")) {
            requests.clear();var language=new OjLanguage();language.setLanguageKey(key);language.setTimeLimitMs(10000);language.setMemoryLimitMb(384);
            var pack=new ProblemPack();var profile=new ProblemPack.Profile();profile.setFunctionDriver("driver-prefix\n__USER_CODE__\ndriver-suffix");pack.getProfiles().put(key,profile);
            var test=new ProblemPack.TestCase();test.setName("Unicode 样例🙂");test.setInput("42\n");test.setExpectedOutput("42");
            var result=engine.judge(pack,language,mode,"user-source-中文🙂",List.of(test,test),true,3,n->{});
            assertEquals("AC",result.getVerdict(),key+"/"+mode);assertEquals(6,result.getPassedCases());assertFalse(result.isOutputTruncated());
            var compile=json.valueToTree(requests.get(0).get("body")).path("cmd").get(0);
            String source=compile.path("copyIn").elements().next().path("content").asText();
            if (mode.equals("STDIO")) assertEquals("user-source-中文🙂", source);
            else {assertTrue(source.contains("driver-prefix\nuser-source-中文🙂\ndriver-suffix"));assertFalse(source.contains("__USER_CODE__"));}
            assertEquals(15000000000L,compile.path("cpuLimit").asLong());assertEquals(512L*1024*1024,compile.path("memoryLimit").asLong());
            if(key.equals("rust"))assertTrue(compile.path("args").toString().contains("link-arg=-Wl,--threads=1"));
            if(key.equals("python") && mode.equals("FUNCTION")) {
                assertTrue(compile.path("copyOutCached").toString().contains("answer"));
                var execution=json.valueToTree(requests.get(1).get("body")).path("cmd").get(0);
                assertEquals("artifact-answer", execution.path("copyIn").path("answer").path("fileId").asText());
            }
            assertEquals(key.equals("python") && mode.equals("STDIO") ? 7 : 8,requests.size());
            if(!key.equals("python") || mode.equals("FUNCTION"))assertEquals("DELETE",requests.get(requests.size()-1).get("method"));
            groups.add(Map.of("language",key,"mode",mode,"requests",List.copyOf(requests)));
        }
        String export=System.getProperty("judger.contract.output");
        if(export!=null)Files.writeString(Path.of(export),json.writerWithDefaultPrettyPrinter().writeValueAsString(groups));
    }
}
