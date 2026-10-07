package com.sap.service.judger;

/*
 * 2026-10-03 local audit. Run from an isolated copy of sap-backend, never production.
 * Tests containing "ConfirmedGap" assert current vulnerable behavior; passing
 * those tests is evidence of the gap, not a security certification.
 */
import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet;
import cn.dev33.satoken.stp.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.config.judger.JudgerProperties;
import com.sap.controller.judger.*;
import com.sap.dto.judger.*;
import com.sap.entity.judger.*;
import com.sap.mapper.judger.*;
import com.sap.util.judger.OutputChecker;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.*;
import org.springframework.web.context.request.*;
import org.springframework.web.method.HandlerMethod;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BackendSecurityAuditTest {
    final ObjectMapper json=new ObjectMapper();
    OjLanguage language(String key){var l=new OjLanguage();l.setLanguageKey(key);l.setTimeLimitMs(1000);l.setMemoryLimitMb(128);return l;}
    ProblemPack.TestCase sample(){var t=new ProblemPack.TestCase();t.setName("audit sample");t.setInput("public input");t.setExpectedOutput("1");t.setSample(true);return t;}
    GoJudgeClient engineClient(String artifact,String output,String stderr){
        var c=mock(GoJudgeClient.class);
        when(c.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(true),anyList())).thenReturn(json.valueToTree(Map.of("status","Accepted","fileIds",Map.of(artifact,"audit-artifact"))));
        when(c.execute(anyList(),anyMap(),anyString(),anyInt(),anyInt(),eq(false),anyList())).thenReturn(json.valueToTree(Map.of("status","Accepted","files",Map.of("stdout",output,"stderr",stderr))));
        return c;
    }
    @Test void sourceAndInputCannotAlterCommandsForAllFiveLanguages(){
        String malicious="$(touch /tmp/audit-NOT-EXECUTED); ../../escape; `id`; rm -rf /";
        var t=sample();t.setInput(malicious);
        for(String key:List.of("c","cpp","java","python","rust")){
            var c=engineClient(key.equals("java")?"answer.jar":"answer","1","");
            var result=new JudgeEngine(c).judge(new ProblemPack(),language(key),"STDIO",malicious,List.of(t),true,1,n->{});
            assertEquals("AC",result.getVerdict());
            var args=ArgumentCaptor.forClass(List.class);var inputs=ArgumentCaptor.forClass(Map.class);
            verify(c,times(2)).execute(args.capture(),inputs.capture(),anyString(),anyInt(),anyInt(),anyBoolean(),anyList());
            for(var command:args.getAllValues())assertFalse(command.toString().contains(malicious));
            assertEquals(1,inputs.getAllValues().getFirst().size());
            assertTrue(inputs.getAllValues().getFirst().values().toString().contains(malicious));
            if(!key.equals("java"))assertFalse(args.getAllValues().getFirst().contains("/bin/sh"));
        }
    }
    @Test void sandboxRequestCarriesCpuWallMemoryProcessAndOutputLimits(){
        var cfg=new JudgerProperties();cfg.setEnabled(true);
        var map=mock(OjNodeMapper.class);var transport=mock(JudgerNodeTransport.class);var secrets=mock(JudgerNodeSecrets.class);
        var node=new OjNode();node.setId(1L);node.setName("local fixture");node.setEnabled(true);node.setMaxConcurrency(1);
        when(map.selectList(any())).thenReturn(List.of(node));when(map.selectById(1L)).thenReturn(node);when(secrets.token(node)).thenReturn("fixture-token");
        when(transport.request(eq(node),eq("POST"),eq("/leases"),any(),anyInt(),isNull())).thenReturn(json.valueToTree(Map.of("lease","audit-lease-01234567890123456789")));
        when(transport.request(eq(node),eq("POST"),eq("/engine/run"),any(),anyInt(),anyString())).thenReturn(json.valueToTree(List.of(Map.of("status","Accepted"))));
        var nodes=new JudgerNodes(map,cfg,transport,secrets);
        try {
            nodes.heartbeat(1L,"Bearer fixture-token",json.valueToTree(Map.of("protocol",1,"runtimeId",cfg.getRuntimeId(),"capacity",1,"active",0,"freeSlots",1,"state","RUNNING")));
            var lease=nodes.acquire(Set.of());assertNotNull(lease);lease.bind();
            var c=new GoJudgeClient(cfg,json,nodes,transport);
            c.execute(List.of("/w/answer"),Map.of(),"public",1000,128,false,List.of());
            var body=ArgumentCaptor.forClass(Object.class);verify(transport).request(eq(node),eq("POST"),eq("/engine/run"),body.capture(),anyInt(),eq(lease.id));
            var command=json.valueToTree(body.getValue()).path("cmd").get(0);
            assertEquals(1_000_000_000L,command.path("cpuLimit").asLong());assertEquals(3_000_000_000L,command.path("clockLimit").asLong());
            assertEquals(128L*1024*1024,command.path("memoryLimit").asLong());assertTrue(command.path("strictMemoryLimit").asBoolean());assertEquals(32,command.path("procLimit").asInt());
            assertEquals(262144,command.path("files").get(1).path("max").asInt());assertEquals(65536,command.path("files").get(2).path("max").asInt());
            String env=command.path("env").toString();assertFalse(env.contains("fixture-token"));lease.close();
        }finally{nodes.shutdown();}
    }
    @Test void requestValidationRejectsOversizeCodeAndInput(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var validator=factory.getValidator();var r=new SubmitRequest();r.setProblemId(1L);r.setLanguage("cpp");r.setMode("STDIO");r.setCode("source");
            assertTrue(validator.validate(r).isEmpty());r.setCode("x".repeat(65537));assertTrue(validator.validate(r).stream().anyMatch(v->v.getPropertyPath().toString().equals("code")));
            r.setCode("source");r.setInput("x".repeat(1048577));assertTrue(validator.validate(r).stream().anyMatch(v->v.getPropertyPath().toString().equals("input")));
        }
    }
    @Test void anonymousAndMemberRolesCannotReachAdminController() throws Exception {
        SaTokenConfig saved=SaManager.getConfig();var savedDao=SaManager.getSaTokenDao();var savedContext=SaManager.getSaTokenContext();var savedRoles=SaManager.getStpInterface();
        try{
            var cfg=new SaTokenConfig();cfg.setTokenName("sap-token");cfg.setIsReadCookie(false);cfg.setIsPrint(false);cfg.setIsShare(false);SaManager.setConfig(cfg);
            SaManager.setSaTokenDao(new SaTokenDaoDefaultImpl());SaManager.setSaTokenContext(new SaTokenContextForSpringInJakartaServlet());
            SaManager.setStpInterface(new StpInterface(){public List<String> getPermissionList(Object id,String t){return List.of();}public List<String> getRoleList(Object id,String t){return List.of("3");}});
            var controller=new OjAdminController(mock(OjService.class),mock(OjInsightsService.class));var handler=new HandlerMethod(controller,OjAdminController.class.getMethod("languages"));
            var interceptor=new SaInterceptor(h->StpUtil.checkLogin());var req=new MockHttpServletRequest("GET","/api/admin/oj/languages");var response=new MockHttpServletResponse();
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req,response));
            assertThrows(cn.dev33.satoken.exception.NotLoginException.class,()->interceptor.preHandle(req,response,handler));
            String token=StpUtil.stpLogic.createLoginSession(991099L,new SaLoginModel());req.addHeader("sap-token",token);
            assertThrows(cn.dev33.satoken.exception.NotRoleException.class,()->interceptor.preHandle(req,response,handler));
        }finally{RequestContextHolder.resetRequestAttributes();SaManager.setConfig(saved);SaManager.setSaTokenDao(savedDao);SaManager.setSaTokenContext(savedContext);SaManager.setStpInterface(savedRoles);}
    }
    @Test void aggregateRunOutputHasNoJobLevelCapConfirmedGap() throws Exception {
        // Bounded local proof: only 2.5 MiB, no real code execution or flood.
        String stdout=" ".repeat(262140)+"1";String stderr="x".repeat(65535);
        assertTrue(OutputChecker.matches(stdout,"1","TOKENS"));
        var c=engineClient("answer",stdout,stderr);var pack=new ProblemPack();
        var result=new JudgeEngine(c).judge(pack,language("cpp"),"STDIO","fixture",Collections.nCopies(8,sample()),true,1,n->{});
        assertEquals("AC",result.getVerdict());assertEquals(8,result.getCases().size());
        long retained=result.getCases().stream().mapToLong(v->((String)v.get("output")).getBytes(StandardCharsets.UTF_8).length+((String)v.get("stderr")).getBytes(StandardCharsets.UTF_8).length).sum();
        assertEquals(2_621_408L,retained);assertTrue(json.writeValueAsString(result).length()>2_600_000);
        System.out.println("AUDIT_CONFIRMED_GAP aggregate_output: 8 cases retained "+retained+" bytes; 500-case equivalent "+(retained/8*500)+" bytes");
    }
    @Test void dailyQuotaDefaultsToUnlimitedConfirmedGap(){assertEquals(0,new JudgerProperties().getDailySubmissionLimit());}
}
