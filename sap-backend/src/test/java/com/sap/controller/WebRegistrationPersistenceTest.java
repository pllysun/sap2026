package com.sap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.service.CaptchaService;
import com.sap.service.mail.EmailBusinessHooks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 使用完整 MVC、真实 MyBatis/H2 与事务，邮件投递替换为内存测试边界。 */
@SpringBootTest(properties={
        "spring.profiles.active=test", "spring.datasource.url=jdbc:h2:mem:web_email_registration;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=update", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "jw.aes-key=local-security-registration-32-byte-test-key", "judger.enabled=false",
        "app.ratelimit.use-redis=false", "spring.data.redis.port=1"})
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class WebRegistrationPersistenceTest {
    @Autowired MockMvc mvc;
    @Autowired CaptchaService captcha;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @MockBean EmailBusinessHooks hooks;

    @AfterEach void resetJoin() { db.update("UPDATE sys_setting SET setting_value='false' WHERE setting_key='join_enabled'"); }

    @Test void publicPolicyAndBothRegistrationRoutesFollowJoinSwitch() throws Exception {
        db.update("UPDATE sys_setting SET setting_value='true' WHERE setting_key='join_enabled'");
        mvc.perform(get("/api/auth/register/policy")).andExpect(jsonPath("$.data.joinEnabled").value(true))
                .andExpect(jsonPath("$.data.emailVerificationRequired").value(false)).andExpect(header().string("Cache-Control","no-store"));
        int index=0;
        for(String route:List.of("/api/auth/register","/api/auth/app/register")) {
            String ip="192.0.2."+(52+index++),account="open_"+UUID.randomUUID().toString().replace("-", "").substring(0,10);
            var form=new HashMap<String,Object>(Map.of("studentId",account,"name","入会注册测试","qq","98765432"+index,"password","local-test-password","gender",0));
            mvc.perform(post(route).with(r->{r.setRemoteAddr(ip);return r;}).contentType("application/json").content(json.writeValueAsBytes(form)))
                    .andExpect(jsonPath("$.code").value(400));
            long generated=System.currentTimeMillis();
            ReflectionTestUtils.setField(captcha,"clock",(LongSupplier)()->generated);
            var image=captcha.generate(ip);String id=image.get("captchaId").toString();
            var images=(Map<?,?>)ReflectionTestUtils.getField(captcha,"captchas");
            form.put("captchaId",id);form.put("captchaCode",ReflectionTestUtils.getField(images.get(id),"answer"));
            ReflectionTestUtils.setField(captcha,"clock",(LongSupplier)()->generated+10000);
            mvc.perform(post(route).with(r->{r.setRemoteAddr(ip);return r;}).contentType("application/json").content(json.writeValueAsBytes(form)))
                    .andExpect(jsonPath("$.code").value(200));
            assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE student_id=?",Integer.class,account));
        }
        verify(hooks,never()).appRegistrationCode(any(),any(),any(),any(),any(),any());
        db.update("UPDATE sys_setting SET setting_value='false' WHERE setting_key='join_enabled'");
        mvc.perform(get("/api/auth/register/policy")).andExpect(jsonPath("$.data.emailVerificationRequired").value(true));
        var stale=Map.of("studentId","closed_"+UUID.randomUUID().toString().substring(0,8),"name","策略更新测试","qq","123456789","password","local-test-password","captchaId","stale","captchaCode","ABCD");
        mvc.perform(post("/api/auth/app/register").contentType("application/json").content(json.writeValueAsBytes(stale)))
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test void onlyVerifiedEmailCanCreateAccountAndConsumedCodesCannotBeReplayed() throws Exception {
        String account="web_"+UUID.randomUUID().toString().replace("-", "").substring(0,12);
        Map<String,Object> form=new HashMap<>(Map.of("studentId",account,"name","邮箱注册测试", "qq","987654321", "password","local-test-password", "gender",1));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsBytes(form)))
                .andExpect(jsonPath("$.code").value(400));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE student_id=?",Integer.class,account));
        AtomicReference<String> code=new AtomicReference<>();
        when(hooks.appRegistrationCode(any(),any(),any(),any(),any(),any())).thenAnswer(i->{code.set(i.getArgument(3));return UUID.randomUUID().toString();});
        long generated=System.currentTimeMillis();
        ReflectionTestUtils.setField(captcha,"clock",(LongSupplier)()->generated);
        var image=captcha.generate("127.0.0.1");String id=image.get("captchaId").toString();
        var images=(Map<?,?>)ReflectionTestUtils.getField(captcha,"captchas");
        String answer=(String)ReflectionTestUtils.getField(images.get(id),"answer");
        ReflectionTestUtils.setField(captcha,"clock",(LongSupplier)()->generated+10000);
        form.put("captchaId",id);form.put("captchaCode",answer);
        String sent=mvc.perform(post("/api/auth/register/email-code").contentType("application/json").content(json.writeValueAsBytes(form)))
                .andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString();
        String challenge=json.readTree(sent).path("data").path("requestId").asText();
        assertFalse(challenge.isBlank(),sent);assertNotNull(code.get());
        form.put("emailRequestId",challenge);form.put("emailCode",code.get());
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsBytes(form)))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE student_id=?",Integer.class,account));
        mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsBytes(form)))
                .andExpect(jsonPath("$.code").value(400));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sys_user WHERE student_id=?",Integer.class,account));
    }
}
