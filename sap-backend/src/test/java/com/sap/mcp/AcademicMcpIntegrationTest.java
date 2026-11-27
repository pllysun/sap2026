package com.sap.mcp;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.sap.entity.User;
import com.sap.entity.UserRole;
import com.sap.jw.client.JwMfaPendingException;
import com.sap.jw.service.JwCredentialService;
import com.sap.jw.service.JwEvaluationService;
import com.sap.jw.service.JwExamService;
import com.sap.jw.service.JwGradeService;
import com.sap.jw.service.JwScheduleService;
import com.sap.jw.vo.EvalOverviewVO;
import com.sap.jw.vo.EvalTaskVO;
import com.sap.jw.vo.ExamVO;
import com.sap.jw.vo.GradeVO;
import com.sap.jw.vo.ScheduleVO;
import com.sap.mapper.UserMapper;
import com.sap.mapper.UserRoleMapper;
import com.sap.util.PasswordUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:academic_mcp;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.profiles.active=local",
        "spring.autoconfigure.exclude=cn.dev33.satoken.dao.SaTokenDaoRedisJackson",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "jw.aes-key=local-mcp-integration-test-dummy-key-001",
        "judger.enabled=false",
        "app.ratelimit.enabled=false",
        "app.ratelimit.use-redis=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(AcademicMcpIntegrationTest.LocalSessionStorage.class)
class AcademicMcpIntegrationTest {
    private static final String TERM = "2025-2026-2";
    private static final String PASSWORD = "academic-mcp-integration-only";
    private static final AtomicInteger IDS = new AtomicInteger();

    @Autowired private TestRestTemplate http;
    @Autowired private UserMapper users;
    @Autowired private UserRoleMapper roles;
    @Autowired private JwCredentialService credentials;
    @Autowired private JdbcTemplate jdbc;
    @MockBean private JwScheduleService schedules;
    @MockBean private JwGradeService grades;
    @MockBean private JwEvaluationService evaluations;
    @MockBean private JwExamService exams;

    @TestConfiguration(proxyBeanMethods = false)
    static class LocalSessionStorage {
        @Bean
        SaTokenDao mcpTestSessionDao() {
            return new SaTokenDaoDefaultImpl();
        }
    }

    @BeforeEach
    void useHttpClientWithoutChallengeRetries() {
        http.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @Test
    void toolDiscoveryUsesKeyScopeAndDeletionRevokesIt() throws Exception {
        var alice = member();
        var bob = member();
        assertThat(create(alice, bob.bindingId(), "schedule:read").getBody().path("code").asInt()).isEqualTo(400);
        assertThat(mcp(null, "tools/list", Map.of()).getStatusCode().value()).isEqualTo(401);

        var response = create(alice, alice.bindingId(), "schedule:read");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        var created = response.getBody().path("data");
        var key = created.path("secret").asText();
        var id = created.path("key").path("id").asLong();
        assertThat(key).matches("sap_mcp_[A-Za-z0-9_-]{43}");
        assertThat(jdbc.queryForObject("SELECT key_hash FROM mcp_api_key WHERE id=?", byte[].class, id))
                .isEqualTo(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.US_ASCII)));
        var aliceSettings = settings(alice);
        assertThat(aliceSettings.path("keys").path(0).path("prefix").asText()).isEqualTo(key.substring(0, 16));
        assertThat(aliceSettings.toString()).doesNotContain(key, "keyHash", "secret", PASSWORD);
        assertThat(settings(bob).path("keys").size()).isZero();
        assertThat(delete(bob, id).path("code").asInt()).isEqualTo(404);

        var init = mcp(key, "initialize", Map.of(
                "protocolVersion", "2025-11-25", "capabilities", Map.of(),
                "clientInfo", Map.of("name", "sap-test", "version", "1")));
        assertThat(init.getStatusCode().value()).isEqualTo(200);
        assertThat(init.getBody().path("result").path("serverInfo").path("name").asText()).isEqualTo("sap-academic");
        var listed = mcp(key, "tools/list", Map.of()).getBody().path("result").path("tools");
        assertThat(listed.size()).isEqualTo(1);
        assertThat(listed.path(0).path("name").asText()).isEqualTo("get_my_schedule");
        assertThat(mcp(key, "tools/list", Map.of(), "https://untrusted.example").getStatusCode().value()).isEqualTo(403);

        var schedule = new ScheduleVO();
        schedule.setTerm(TERM);
        when(schedules.getSchedule(alice.userId(), alice.account(), TERM)).thenReturn(schedule);
        assertThat(call(key, "get_my_schedule", Map.of("term", TERM)).getBody()
                .path("result").path("structuredContent").path("term").asText()).isEqualTo(TERM);
        var injectedAccount = call(key, "get_my_schedule", Map.of("term", TERM, "account", bob.account())).getBody();
        assertThat(injectedAccount.has("error") || injectedAccount.path("result").path("isError").asBoolean()).isTrue();
        verify(schedules).getSchedule(alice.userId(), alice.account(), TERM);
        assertThat(call(key, "get_my_grades", Map.of("term", TERM)).getBody().path("result").path("isError").asBoolean()).isTrue();
        verifyNoInteractions(grades);

        assertThat(delete(alice, id).path("code").asInt()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mcp_api_key WHERE id=?", Long.class, id)).isZero();
        assertThat(mcp(key, "tools/list", Map.of()).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void gradeExamAndEvaluationToolsReturnOnlyAuthorizedData() {
        var member = member();
        var key = create(member, member.bindingId(), "grades:read", "exams:read", "evaluations:read")
                .getBody().path("data").path("secret").asText();

        var actions = mcp(key, "tools/list", Map.of()).getBody().path("result").path("tools");
        assertThat(actions.size()).isEqualTo(3);
        assertThat(actions.toString()).doesNotContain("submit", "get_my_schedule");
        var missingTerm = call(key, "get_my_grades", Map.of()).getBody();
        assertThat(missingTerm.has("error") || missingTerm.path("result").path("isError").asBoolean()).isTrue();
        verifyNoInteractions(grades);

        var grade = new GradeVO();
        grade.setTerm(TERM);
        grade.setCourseName("目标学期");
        var otherGrade = new GradeVO();
        otherGrade.setTerm("2025-2026-1");
        otherGrade.setCourseName("其他学期");
        when(grades.getGrades(member.userId(), member.account())).thenReturn(List.of(grade, otherGrade));
        var gradeData = call(key, "get_my_grades", Map.of("term", TERM)).getBody().path("result").path("structuredContent");
        assertThat(gradeData.path("grades").size()).isEqualTo(1);
        assertThat(gradeData.path("grades").path(0).path("courseName").asText()).isEqualTo("目标学期");

        var exam = new ExamVO();
        exam.setCourseName("考试课程");
        exam.setRoom("本地测试教室");
        exam.setAdmissionTicket("unrequested-ticket");
        when(exams.getExams(member.userId(), member.account(), null)).thenReturn(List.of(exam));
        var examData = call(key, "get_my_exams", Map.of()).getBody().path("result").path("structuredContent");
        assertThat(examData.path("exams").path(0).path("room").asText()).isEqualTo("本地测试教室");
        assertThat(examData.toString()).doesNotContain("unrequested-ticket");

        var evaluation = new EvalOverviewVO();
        evaluation.setTaskName("本地评教轮次");
        evaluation.setTerms(List.of("本地评教轮次"));
        var course = new EvalTaskVO();
        course.setCourseName("待评课程");
        course.setStatusText("未评价");
        evaluation.setTasks(List.of(course));
        when(evaluations.getOverview(member.userId(), member.account(), null)).thenReturn(evaluation);
        var evalData = call(key, "get_my_evaluations", Map.of()).getBody().path("result").path("structuredContent");
        assertThat(evalData.path("courses").path(0).path("status").asText()).isEqualTo("未评价");
        assertThat(evalData.path("courses").path(0).path("evaluated").asBoolean()).isFalse();

        when(exams.getExams(member.userId(), member.account(), TERM))
                .thenThrow(new JwMfaPendingException("private-challenge", "private-phone"));
        var mfa = call(key, "get_my_exams", Map.of("term", TERM)).getBody().path("result");
        assertThat(mfa.path("isError").asBoolean()).isTrue();
        assertThat(mfa.toString()).contains("短信二次验证").doesNotContain("private-challenge", "private-phone");

        credentials.unbind(member.userId(), member.account());
        credentials.save(member.userId(), member.account(), PASSWORD);
        assertThat(mcp(key, "tools/list", Map.of()).getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void quotaAllowsOnlyOneOfTwoConcurrentCreatesAtNineKeys() throws Exception {
        var member = member();
        for (int i = 0; i < 9; i++) {
            assertThat(create(member, member.bindingId(), "schedule:read").getBody().path("code").asInt()).isEqualTo(200);
        }
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                start.await();
                return create(member, member.bindingId(), "schedule:read").getBody().path("code").asInt();
            });
            var second = pool.submit(() -> {
                start.await();
                return create(member, member.bindingId(), "schedule:read").getBody().path("code").asInt();
            });
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        var keys = settings(member).path("keys");
        assertThat(keys.size()).isEqualTo(10);
        assertThat(delete(member, keys.path(0).path("id").asLong()).path("code").asInt()).isEqualTo(200);
        assertThat(create(member, member.bindingId(), "schedule:read").getBody().path("code").asInt()).isEqualTo(200);
    }

    @Test
    void allKeysShareTheUserQueryWindow() {
        var member = member();
        String firstKey = create(member, member.bindingId(), "schedule:read").getBody().path("data").path("secret").asText();
        String secondKey = create(member, member.bindingId(), "schedule:read").getBody().path("data").path("secret").asText();
        when(schedules.getSchedule(member.userId(), member.account(), null)).thenReturn(new ScheduleVO());
        for (int i = 0; i < 30; i++) {
            assertThat(call(i % 2 == 0 ? firstKey : secondKey, "get_my_schedule", Map.of()).getStatusCode().value())
                    .isEqualTo(200);
        }
        assertThat(call(secondKey, "get_my_schedule", Map.of()).getStatusCode().value()).isEqualTo(429);
        assertThat(mcp(firstKey, "tools/list", Map.of()).getStatusCode().value()).isEqualTo(200);
    }

    private Member member() {
        int n = IDS.incrementAndGet();
        String account = "mcp_test_" + n;
        var user = new User();
        user.setStudentId(account);
        user.setName("MCP test");
        user.setQq("9000000" + n);
        user.setPassword(PasswordUtil.encode(PASSWORD));
        user.setStatus(1);
        user.setDeleted(0);
        users.insert(user);
        var role = new UserRole();
        role.setUserId(user.getId());
        role.setRoleCode(3);
        roles.insert(role);
        credentials.save(user.getId(), account, PASSWORD);
        var auth = http.postForEntity("/api/auth/login", Map.of("studentId", account, "password", PASSWORD), JsonNode.class);
        assertThat(auth.getBody().path("code").asInt()).isEqualTo(200);
        return new Member(user.getId(), account, credentials.get(user.getId(), account).getId(),
                auth.getBody().path("data").path("token").asText());
    }

    private ResponseEntity<JsonNode> create(Member owner, long bindingId, String... scopes) {
        return http.exchange("/api/mcp-keys", HttpMethod.POST,
                new HttpEntity<>(Map.of("name", "测试密钥", "credentialId", bindingId, "scopes", List.of(scopes)),
                        sapHeaders(owner)), JsonNode.class);
    }

    private JsonNode settings(Member owner) {
        return http.exchange("/api/mcp-keys", HttpMethod.GET, new HttpEntity<>(sapHeaders(owner)), JsonNode.class)
                .getBody().path("data");
    }

    private JsonNode delete(Member owner, long id) {
        return http.exchange("/api/mcp-keys/" + id, HttpMethod.DELETE, new HttpEntity<>(sapHeaders(owner)), JsonNode.class)
                .getBody();
    }

    private static HttpHeaders sapHeaders(Member member) {
        var headers = new HttpHeaders();
        headers.set("sap-token", member.token());
        return headers;
    }

    private ResponseEntity<JsonNode> call(String key, String name, Map<String, Object> arguments) {
        return mcp(key, "tools/call", Map.of("name", name, "arguments", arguments));
    }

    private ResponseEntity<JsonNode> mcp(String key, String method, Map<String, Object> params) {
        return mcp(key, method, params, null);
    }

    private ResponseEntity<JsonNode> mcp(String key, String method, Map<String, Object> params, String origin) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));
        if (key != null) headers.setBearerAuth(key);
        if (origin != null) headers.setOrigin(origin);
        return http.postForEntity("/mcp",
                new HttpEntity<>(Map.of("jsonrpc", "2.0", "id", 1, "method", method, "params", params), headers),
                JsonNode.class);
    }

    private record Member(long userId, String account, long bindingId, String token) {}
}
