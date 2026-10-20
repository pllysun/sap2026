package io.sapoj;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapoj.config.judger.JudgerProperties;
import io.sapoj.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class SettingsIntegrationTest {
    static final String PASSWORD=UUID.randomUUID().toString();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",()->"jdbc:h2:mem:standalone_settings;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        r.add("app.bootstrap.admin-account",()->"admin");r.add("app.bootstrap.admin-password",()->PASSWORD);
    }
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired Accounts accounts;
    @Autowired BusinessSettings settings;@Autowired JudgerProperties judge;
    String login(String name,String password)throws Exception{
        var result=mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("studentId",name,"password",password))))
            .andReturn().getResponse().getContentAsString();return json.readTree(result).path("data").path("token").asText();
    }
    @Test void emptyInstallationHasNoProblems()throws Exception{
        mvc.perform(get("/api/admin/oj/problems").header("sap-token",login("admin",PASSWORD))).andExpect(jsonPath("$.data.total").value("0"));
    }
    @Test void settingsAreValidatedPersistedAndAppliedWithoutExposingInfrastructureCredentials()throws Exception{
        String token=login("admin",PASSWORD);
        var row=settings.get();row.setSiteName("独立练习站");row.setQueueCapacity(7);row.setDailySubmissionLimit(5);row.setMaxJudgeGroupMs(120000L);row.setRegistrationEnabled(true);
        mvc.perform(put("/api/admin/settings").header("sap-token",token).contentType("application/json").content(json.writeValueAsString(row))).andExpect(jsonPath("$.code").value(200));
        settings.initialize();assertEquals(7,judge.getQueueCapacity());assertEquals(5,judge.getDailySubmissionLimit());assertEquals(120000L,judge.getMaxJudgeGroupMs());
        mvc.perform(get("/api/public/config")).andExpect(jsonPath("$.data.siteName").value("独立练习站")).andExpect(jsonPath("$.data.registrationEnabled").value(true));
        row.setQueueCapacity(0);
        mvc.perform(put("/api/admin/settings").header("sap-token",token).contentType("application/json").content(json.writeValueAsString(row))).andExpect(jsonPath("$.code").value(400));
        settings.initialize();assertEquals(7,judge.getQueueCapacity());
        String safe=mvc.perform(get("/api/admin/storage").header("sap-token",token)).andExpect(jsonPath("$.data.enabled").value(false)).andReturn().getResponse().getContentAsString();
        assertFalse(safe.contains("accessKey"));assertFalse(safe.contains("secretKey"));
        mvc.perform(post("/api/admin/storage/check").header("sap-token",token)).andExpect(jsonPath("$.code").value(503));
        mvc.perform(get("/api/admin/storage/archives/invalid").header("sap-token",token)).andExpect(jsonPath("$.code").value(400));
    }
    @Test void learnerCannotChangeBusinessSettingsOrReadArchives()throws Exception{
        String id="t"+UUID.randomUUID().toString().replace("-","").substring(0,18),pw=UUID.randomUUID().toString();accounts.create(id,pw,"普通用户",3);String token=login(id,pw);
        for(String path:List.of("/api/admin/settings","/api/admin/storage","/api/admin/storage/archives"))
            mvc.perform(get(path).header("sap-token",token)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/settings").header("sap-token",token).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/storage/archives/123")).andExpect(status().isUnauthorized());
    }
}
