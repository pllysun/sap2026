package com.sap.jw.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sap.common.BusinessException;
import com.sap.jw.client.JwQualitySession;
import com.sap.jw.dto.EvalAnswerDTO;
import com.sap.jw.dto.EvalSubmitDTO;
import com.sap.jw.vo.EvalFormVO;
import com.sap.jw.vo.EvalOverviewVO;
import com.sap.jw.vo.EvalResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwEvaluationServiceTest {

    @Mock
    private JwQualitySessionManager sessionManager;
    @Mock
    private JwQualitySession session;

    private JwEvaluationService service;

    @BeforeEach
    void setUp() {
        service = new JwEvaluationService(sessionManager);
        when(sessionManager.getSession(1L, "20250001")).thenReturn(session);
        lenient().when(session.post(anyString(), anyMap()))
                .thenAnswer(invocation -> route(invocation.getArgument(0)));
    }

    @Test
    void overviewMapsNewPlatformTaskAndCourse() {
        EvalOverviewVO overview = service.getOverview(1L, "20250001", null);

        assertEquals(9L, overview.getTaskId());
        assertEquals("2026年春季学期学生评教", overview.getTaskName());
        assertTrue(overview.isRestrictHighest());
        assertEquals(1, overview.getTasks().size());
        assertEquals(7224880L, overview.getTasks().get(0).getCourseId());
        assertEquals("离散数学", overview.getTasks().get(0).getCourseName());
        assertEquals("朱老师", overview.getTasks().get(0).getTeacher());
        assertFalse(overview.getTasks().get(0).isEvaluated());
    }

    @Test
    void formExposesOriginalQuestionsAndRestrictions() {
        EvalFormVO form = service.getForm(1L, "20250001", 9L, 7224880L);

        assertEquals("离散数学", form.getCourseName());
        assertEquals(11, form.getQuestions().size());
        assertEquals(100.0, form.getMaxTotal());
        assertTrue(form.isRestrictHighest());
        assertTrue(form.getQuestions().get(0).isRequired());
        assertEquals(10.0, form.getQuestions().get(0).getMaxScore());
        assertEquals("问答题", form.getQuestions().get(10).getType());
    }

    @Test
    void autoEvaluationUsesHighestLegalScoreInsteadOfForbiddenHundred() {
        when(session.postJson(anyString(), any())).thenReturn(success("保存学生评价成功"));

        List<EvalResultVO> results = service.autoEvaluate(1L, "20250001", 9L, null, "自定义好评");

        assertEquals(1, results.size());
        assertTrue(results.get(0).isSuccess());
        assertEquals("99.99", results.get(0).getScore());

        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(session).postJson(anyString(), body.capture());
        JSONObject submission = firstSubmission(body.getValue());
        assertEquals(0, new BigDecimal("99.99").compareTo(submission.getBigDecimal("totalscore")));
        JSONArray answers = submission.getJSONArray("evaluateResult");
        assertEquals(11, answers.size());
        long slightlyReduced = answers.stream()
                .filter(JSONObject.class::isInstance)
                .map(JSONObject.class::cast)
                .filter(a -> "9.99".equals(a.getString("index_title")))
                .count();
        assertEquals(1, slightlyReduced);
        assertEquals("自定义好评", answers.getJSONObject(10).getString("index_title"));
    }

    @Test
    void manualEvaluationRejectsAllHighestBeforeNetworkSubmit() {
        EvalSubmitDTO dto = submitDto("手动评价", BigDecimal.TEN);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.submit(1L, "20250001", dto));

        assertTrue(error.getMessage().contains("不允许所有评分题均填写最高分"));
        verify(session, never()).postJson(anyString(), any());
    }

    @Test
    void manualEvaluationPreservesCustomScoresAndComment() {
        when(session.postJson(anyString(), any())).thenReturn(success("保存成功"));
        EvalSubmitDTO dto = submitDto("这是一段由学生手动填写的评价", BigDecimal.TEN);
        dto.getAnswers().get(0).setScore(new BigDecimal("9.50"));

        EvalResultVO result = service.submit(1L, "20250001", dto);

        assertTrue(result.isSuccess());
        assertEquals("99.5", result.getScore());
        ArgumentCaptor<Object> body = ArgumentCaptor.forClass(Object.class);
        verify(session).postJson(anyString(), body.capture());
        JSONObject submission = firstSubmission(body.getValue());
        assertEquals("这是一段由学生手动填写的评价",
                submission.getJSONArray("evaluateResult").getJSONObject(10).getString("index_title"));
    }

    @Test
    void manualMqFailureKeepsRetryableResultWhenSourceDidNotAcceptSubmission() {
        when(session.postJson(anyString(), any()))
                .thenThrow(new BusinessException("mq消息队列异常！确认是否启动mq服务或重启mq服务再试"));
        EvalSubmitDTO dto = submitDto("手动评价", BigDecimal.TEN);
        dto.getAnswers().get(0).setScore(new BigDecimal("6"));

        EvalResultVO result = service.submit(1L, "20250001", dto);

        assertFalse(result.isSuccess());
        assertFalse(result.isPending());
        assertTrue(result.isRetryable());
        assertTrue(result.getMessage().contains("源站消息队列暂时异常"));
        verify(session, times(1)).postJson(anyString(), any());
    }

    @Test
    void manualMqFailureIsRecoveredWhenCourseIsAlreadySubmitted() {
        AtomicInteger courseReads = new AtomicInteger();
        doAnswer(invocation -> {
            String path = invocation.getArgument(0);
            if (path.endsWith("getXspjStudentCourses")) {
                return courseResponse(courseReads.incrementAndGet() >= 2 ? 1 : 0);
            }
            return route(path);
        }).when(session).post(anyString(), anyMap());
        when(session.postJson(anyString(), any()))
                .thenThrow(new BusinessException("mq消息队列异常"));
        EvalSubmitDTO dto = submitDto("手动评价", BigDecimal.TEN);
        dto.getAnswers().get(0).setScore(new BigDecimal("6"));

        EvalResultVO result = service.submit(1L, "20250001", dto);

        assertTrue(result.isSuccess());
        assertFalse(result.isRetryable());
        assertTrue(result.getMessage().contains("已重新核实"));
    }

    @Test
    void manualMqFailureDoesNotInviteRetryWhileSourceIsProcessing() {
        AtomicInteger courseReads = new AtomicInteger();
        doAnswer(invocation -> {
            String path = invocation.getArgument(0);
            if (path.endsWith("getXspjStudentCourses")) {
                return courseResponse(courseReads.incrementAndGet() >= 2 ? 2 : 0);
            }
            return route(path);
        }).when(session).post(anyString(), anyMap());
        when(session.postJson(anyString(), any()))
                .thenThrow(new BusinessException("mq消息队列异常"));
        EvalSubmitDTO dto = submitDto("手动评价", BigDecimal.TEN);
        dto.getAnswers().get(0).setScore(new BigDecimal("6"));

        EvalResultVO result = service.submit(1L, "20250001", dto);

        assertFalse(result.isSuccess());
        assertTrue(result.isPending());
        assertFalse(result.isRetryable());
        assertTrue(result.getMessage().contains("勿重复提交"));
    }

    @Test
    void autoEvaluationStopsAfterGlobalMqFailureAndMarksRemainingCoursesSkipped() {
        doAnswer(invocation -> {
            String path = invocation.getArgument(0);
            if (path.endsWith("getXspjStudentCourses")) return multiCourseResponse();
            return route(path);
        }).when(session).post(anyString(), anyMap());
        when(session.postJson(anyString(), any()))
                .thenThrow(new BusinessException("mq消息队列异常"));

        List<EvalResultVO> results = service.autoEvaluate(1L, "20250001", 9L, null, null);

        assertEquals(2, results.size());
        assertTrue(results.get(0).isRetryable());
        assertFalse(results.get(0).isSkipped());
        assertTrue(results.get(1).isRetryable());
        assertTrue(results.get(1).isSkipped());
        verify(session, times(1)).postJson(anyString(), any());
    }

    private EvalSubmitDTO submitDto(String comment, BigDecimal score) {
        EvalSubmitDTO dto = new EvalSubmitDTO();
        dto.setTaskId(9L);
        dto.setCourseId(7224880L);
        List<EvalAnswerDTO> answers = new ArrayList<>();
        for (long id = 266; id <= 275; id++) {
            EvalAnswerDTO answer = new EvalAnswerDTO();
            answer.setIndexId(id);
            answer.setScore(score);
            answers.add(answer);
        }
        EvalAnswerDTO text = new EvalAnswerDTO();
        text.setIndexId(276L);
        text.setText(comment);
        answers.add(text);
        dto.setAnswers(answers);
        return dto;
    }

    private JSONObject route(String path) {
        if (path.endsWith("getXspjtask")) return taskResponse();
        if (path.endsWith("getXspjStudentCourses")) return courseResponse();
        if (path.endsWith("getXspjTindexSystem")) return formResponse();
        throw new AssertionError("unexpected path: " + path);
    }

    private JSONObject taskResponse() {
        JSONObject task = new JSONObject();
        task.put("taskid", 9L);
        task.put("taskname", "2026年春季学期学生评教");
        task.put("starttime", "2026-07-10");
        task.put("endtime", "2026-08-31");
        task.put("currentStatus", "进行中");
        task.put("status", 1);
        task.put("yearterm", 6);
        task.put("indexid", "26,25");
        task.put("sfqxzdzgf", "是");
        task.put("sfqxzdf", "是");
        JSONObject data = new JSONObject();
        data.put("pageData", List.of(task));
        data.put("taskSfwc", List.of());
        return ok(data, "查询成功");
    }

    private JSONObject courseResponse() {
        return courseResponse(0);
    }

    private JSONObject courseResponse(int status) {
        JSONObject data = new JSONObject();
        data.put("pageData", List.of(course(7224880L, "离散数学", status)));
        return ok(data, "查询成功");
    }

    private JSONObject multiCourseResponse() {
        JSONObject data = new JSONObject();
        data.put("pageData", List.of(
                course(7224880L, "离散数学", 0),
                course(7224881L, "高等数学", 0)));
        return ok(data, "查询成功");
    }

    private JSONObject course(long id, String name, int status) {
        JSONObject course = new JSONObject();
        course.put("id", id);
        course.put("coursecode", "130090184");
        course.put("coursename", name);
        course.put("classno", "202520262000606");
        course.put("jobnumber", "T001");
        course.put("teachername", "朱老师");
        course.put("studentid", "20250001");
        course.put("studentname", "测试学生");
        course.put("yearterm", 6);
        course.put("courseorgcode", "3029");
        course.put("courseorgname", "计算机与数学学院");
        course.put("pjcoursetype", "理论课");
        course.put("hassubmit", status);
        return course;
    }

    private JSONObject formResponse() {
        List<JSONObject> questions = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            JSONObject question = baseQuestion(266L + i, i + 1, "打分题", "否", "是");
            question.put("score", new BigDecimal("10.00"));
            question.put("weight", BigDecimal.ONE);
            question.put("scoring_type", 0);
            questions.add(question);
        }
        JSONObject text = baseQuestion(276L, 11, "问答题", "是", "否");
        text.put("score", BigDecimal.ZERO);
        text.put("weight", BigDecimal.ZERO);
        questions.add(text);
        JSONObject data = new JSONObject();
        data.put("pageData", questions);
        return ok(data, "查询成功");
    }

    private JSONObject baseQuestion(long id, int order, String type, String empty, String scored) {
        JSONObject question = new JSONObject();
        question.put("indexid", id);
        question.put("ordor", order);
        question.put("firstlevlindex", order <= 2 ? "教学态度" : "教学效果");
        question.put("title", order == 11 ? "对老师的评价：" : "评价指标 " + order);
        question.put("remark", "");
        question.put("type", type);
        question.put("isemptyed", empty);
        question.put("isscored", scored);
        question.put("optionarr", List.of());
        return question;
    }

    private JSONObject success(String message) {
        return ok(new JSONObject(), message);
    }

    private JSONObject ok(JSONObject data, String message) {
        JSONObject response = new JSONObject();
        response.put("code", 200);
        response.put("message", message);
        response.put("data", data);
        return response;
    }

    private JSONObject firstSubmission(Object body) {
        assertTrue(body instanceof List<?>);
        Object first = ((List<?>) body).get(0);
        assertTrue(first instanceof JSONObject);
        return (JSONObject) first;
    }
}
