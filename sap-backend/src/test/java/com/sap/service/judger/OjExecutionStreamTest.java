package com.sap.service.judger;

import cn.dev33.satoken.stp.StpUtil;
import com.sap.controller.judger.OjExecutionController;
import com.sap.entity.judger.OjSubmission;
import com.sap.mapper.judger.OjSubmissionMapper;
import com.sap.vo.judger.JudgeStage;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Duration;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OjExecutionStreamTest {
    @org.junit.jupiter.api.BeforeAll static void metadata(){com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(new org.apache.ibatis.builder.MapperBuilderAssistant(new org.apache.ibatis.session.Configuration(),"execution-test"),OjSubmission.class);}

    @Test void servletStreamsIntermediatePhasesAndClosesOnlyAfterTheDurableTerminalResult() throws Exception {
        var mapper=mock(OjSubmissionMapper.class);var events=new OjExecutionEvents(mapper);
        var job=new OjSubmission();job.setId(7L);job.setUserId(1L);job.setKind("RUN");job.setStatus("RUNNING");job.setAttempt(1);job.setTotalCases(1);job.setPassedCases(0);
        when(mapper.selectOne(any())).thenReturn(job);
        var converters=new java.util.ArrayList<org.springframework.http.converter.HttpMessageConverter<?>>();
        new com.sap.config.FastjsonConfig().configureMessageConverters(converters);
        converters.add(new org.springframework.http.converter.StringHttpMessageConverter(java.nio.charset.StandardCharsets.UTF_8));
        var mvc=MockMvcBuilders.standaloneSetup(new OjExecutionController(events))
            .setMessageConverters(converters.toArray(org.springframework.http.converter.HttpMessageConverter[]::new)).build();
        try(var login=mockStatic(StpUtil.class)) {
            login.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            var result=mvc.perform(get("/api/oj/submissions/7/events").header("sap-token","test-header-only"))
                .andExpect(request().asyncStarted()).andExpect(header().string("X-Accel-Buffering","no"))
                .andExpect(header().string("Cache-Control","no-store, no-transform")).andReturn();
            await().atMost(Duration.ofSeconds(3)).until(()->result.getResponse().getContentAsString().contains("DISPATCHED"));
            events.publish(job,new JudgeStage("COMPILING",0,0,0,1,null));
            await().atMost(Duration.ofSeconds(3)).until(()->result.getResponse().getContentAsString().contains("COMPILING"));
            assertFalse(result.getResponse().getContentAsString().contains("FINISHED"));
            events.publish(job,new JudgeStage("COMPILED",0,0,0,1,null));
            events.publish(job,new JudgeStage("CASE_FINISHED",1,1,1,1,"AC"));job.setStatus("AC");job.setPassedCases(1);
            events.publish(job,new JudgeStage("FINISHED",0,1,1,1,null));
            result.getAsyncResult(3000);
            String body=mvc.perform(asyncDispatch(result)).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/event-stream")).andReturn().getResponse().getContentAsString();
            assertTrue(body.contains("event:progress"));assertTrue(body.contains("FINISHED"));assertTrue(body.contains("CASE_FINISHED") || body.contains("\"cases\":[{\"index\":1"));assertFalse(body.contains("test-header-only"));
            assertTrue(body.contains("\"jobId\":\"7\""));
            assertTrue(java.util.regex.Pattern.compile("\\\"sequence\\\":\\\"[0-9]+\\\"").matcher(body).find());
        } finally {events.shutdown();}
    }
}
