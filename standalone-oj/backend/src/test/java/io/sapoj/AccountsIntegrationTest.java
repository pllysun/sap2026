package io.sapoj;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapoj.service.Accounts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class AccountsIntegrationTest {
    static final String ADMIN_PASSWORD=UUID.randomUUID().toString();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",()->"jdbc:h2:mem:standalone_auth;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        r.add("app.bootstrap.admin-account",()->"admin");r.add("app.bootstrap.admin-password",()->ADMIN_PASSWORD);
        r.add("app.registration-enabled",()->"true");r.add("judger.enabled",()->"false");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Accounts accounts;
    @Autowired io.sapoj.service.BusinessSettings settings;
    String body(Object value)throws Exception{return json.writeValueAsString(value);}
    String login(String account,String password)throws Exception{
        var response=mvc.perform(post("/api/auth/login").contentType("application/json").content(body(Map.of("studentId",account,"password",password))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200)).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("token").asText();
    }
    String learner()throws Exception{
        String id="u"+UUID.randomUUID().toString().replace("-","").substring(0,18),password=UUID.randomUUID().toString();
        accounts.create(id,password,"练习用户",3);return login(id,password);
    }
    @Test void anonymousCannotReadProblemsOrPrivateAdministration()throws Exception{
        mvc.perform(get("/api/oj/problems")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/oj/problems")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ping")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }
    @Test void registrationNeverAcceptsAnAdministrativeRole()throws Exception{
        var config=settings.get();config.setRegistrationEnabled(true);settings.save(config);
        String id="r"+UUID.randomUUID().toString().replace("-","").substring(0,18),password=UUID.randomUUID().toString();
        mvc.perform(post("/api/auth/register").contentType("application/json").content(body(Map.of("studentId",id,"password",password,"nickname","新用户","roleCode",0))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        var token=login(id,password);
        mvc.perform(get("/api/auth/info").header("sap-token",token)).andExpect(jsonPath("$.data.roles[0]").value(3)).andExpect(jsonPath("$.data.user.password").doesNotExist());
        mvc.perform(get("/api/admin/oj/problems").header("sap-token",token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/admin/login").contentType("application/json").content(body(Map.of("studentId",id,"password",password))))
            .andExpect(jsonPath("$.code").value(400));
    }
    @Test void learnersCannotEscalateRolesThroughTheirProfile()throws Exception{
        String token=learner();
        mvc.perform(put("/api/auth/profile").header("sap-token",token).contentType("application/json").content(body(Map.of("nickname","新昵称","roleCode",0,"status",1))))
            .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.roleCode").value(3)).andExpect(jsonPath("$.data.password").doesNotExist());
        mvc.perform(get("/api/admin/users").header("sap-token",token)).andExpect(status().isForbidden());
    }
    @Test void administratorCanMaintainAccountsAndSeeOnlySafeFields()throws Exception{
        String token=login("admin",ADMIN_PASSWORD);
        mvc.perform(get("/api/admin/users").header("sap-token",token)).andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.records[0].password").doesNotExist());
        mvc.perform(get("/api/admin/oj/problems").header("sap-token",token)).andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/api/jw/status").header("sap-token",token)).andExpect(status().isNotFound());
    }
    @Test void selfDisablingAndPasswordResetHavePredictableSessionSemantics()throws Exception{
        String token=login("admin",ADMIN_PASSWORD);
        var admin=accounts.byAccount("admin");
        mvc.perform(put("/api/admin/users/"+admin.getId()).header("sap-token",token).contentType("application/json").content(body(Map.of("nickname","管理员","roleCode",3,"status",1))))
            .andExpect(jsonPath("$.code").value(400));
        String id="p"+UUID.randomUUID().toString().replace("-","").substring(0,18),old=UUID.randomUUID().toString(),next=UUID.randomUUID().toString();
        accounts.create(id,old,"练习用户",3);String learner=login(id,old);
        mvc.perform(put("/api/auth/password").header("sap-token",learner).contentType("application/json").content(body(Map.of("currentPassword",old,"password",next))))
            .andExpect(jsonPath("$.code").value(200));
        mvc.perform(get("/api/auth/info").header("sap-token",learner)).andExpect(status().isUnauthorized());
        assertFalse(login(id,next).isBlank());
    }
    @Test void fakeNodeHeartbeatNeedsTheNodeCredentialNotAUserSession()throws Exception{
        mvc.perform(post("/api/oj-nodes/987654/heartbeat").contentType("application/json").content("{}"))
            .andExpect(jsonPath("$.code").value(404));
        mvc.perform(post("/api/oj-nodes/987654/heartbeat").header("sap-token",learner()).contentType("application/json").content("{}"))
            .andExpect(jsonPath("$.code").value(404));
    }
}
