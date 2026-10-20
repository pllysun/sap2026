package io.sapoj;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapoj.entity.judger.OjSubmission;
import io.sapoj.mapper.judger.OjSubmissionMapper;
import io.sapoj.service.judger.OjExecutionEvents;
import io.sapoj.vo.judger.JudgeStage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real Tomcat chunked response: MockMvc cannot detect interrupted final socket flush. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class SseSocketTest {
    static final String PASSWORD=UUID.randomUUID().toString();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",()->"jdbc:h2:mem:sse_socket;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        r.add("app.bootstrap.admin-account",()->"admin");r.add("app.bootstrap.admin-password",()->PASSWORD);
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;@Autowired OjSubmissionMapper jobs;@Autowired OjExecutionEvents events;
    @Test void terminalEventEndsWithACompleteHttpResponse()throws Exception{
        String base="http://127.0.0.1:"+port;
        try(var client=HttpClient.newHttpClient();var scheduler=Executors.newSingleThreadScheduledExecutor()){
            var login=client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/login")).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("studentId","admin","password",PASSWORD)))).build(),HttpResponse.BodyHandlers.ofString());
            var data=json.readTree(login.body()).path("data");String token=data.path("token").asText();Long user=Long.valueOf(data.path("user").path("id").asText());
            var job=new OjSubmission();job.setUserId(user);job.setProblemId(1L);job.setKind("RUN");job.setLanguage("python");job.setMode("STDIO");job.setStatus("RUNNING");job.setAttempt(1);job.setPassedCases(0);job.setTotalCases(1);job.setCreatedAt(LocalDateTime.now());job.setStartedAt(LocalDateTime.now());jobs.insert(job);
            scheduler.schedule(()->{
                job.setStatus("AC");job.setPassedCases(1);jobs.updateById(job);
                events.publish(job,new JudgeStage("CASE_FINISHED",1,1,1,1,"AC"));
                events.publish(job,new JudgeStage("FINISHED",0,1,1,1,null));
            },1,TimeUnit.SECONDS);
            var response=client.send(HttpRequest.newBuilder(URI.create(base+"/api/oj/submissions/"+job.getId()+"/events"))
                .timeout(Duration.ofSeconds(10)).header("sap-token",token).build(),HttpResponse.BodyHandlers.ofInputStream());
            assertEquals(200,response.statusCode());assertEquals("no",response.headers().firstValue("X-Accel-Buffering").orElse(""));
            try(var body=response.body()){
                String text=new String(body.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(text.contains("FINISHED"));assertTrue(text.contains("event:progress"));assertFalse(text.contains(token));
            }
        }
    }
}
