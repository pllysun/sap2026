package com.sap.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.common.BusinessException;
import com.sap.jw.client.JwMfaPendingException;
import com.sap.jw.service.JwEvaluationService;
import com.sap.jw.service.JwExamService;
import com.sap.jw.service.JwGradeService;
import com.sap.jw.service.JwScheduleService;
import com.sap.service.RateLimiterService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
@RequiredArgsConstructor
public class AcademicMcpTools {
    private static final String TERM_PATTERN = "^[0-9]{4}-[0-9]{4}-[0-9]$";
    private static final int QUERY_LIMIT = 30;
    private static final int WINDOW_SECONDS = 60;

    private final ObjectMapper json;
    private final RateLimiterService limiter;
    private final JwScheduleService schedules;
    private final JwGradeService grades;
    private final JwEvaluationService evaluations;
    private final JwExamService exams;

    record Invocation(McpKeyService.Principal principal, AtomicBoolean rateLimited) {
        static final String ATTRIBUTE = Invocation.class.getName();

        static Invocation from(McpTransportContext context) {
            return (Invocation) context.get(ATTRIBUTE);
        }
    }

    record GradeItem(String courseName, String score, String credit) {}
    record ExamItem(String courseName, String time, String room, String seat) {}
    record ExamList(String term, List<ExamItem> exams) {}
    record EvaluationItem(String courseName, String teacher, boolean evaluated, boolean submitted, String status) {}
    record EvaluationList(String round, String deadline, List<String> availableRounds, List<EvaluationItem> courses) {}

    List<SyncToolSpecification> definitions() {
        return Arrays.stream(McpScope.values()).map(scope -> SyncToolSpecification.builder()
                .tool(McpSchema.Tool.builder().name(scope.tool).description(scope.description)
                        .inputSchema(schema(scope))
                        .annotations(McpSchema.ToolAnnotations.builder().readOnlyHint(true).build())
                        .build())
                .callHandler((context, request) -> call(scope, Invocation.from(context), request.arguments()))
                .build()).toList();
    }

    boolean isVisible(McpTransportContext context, McpSchema.Tool tool) {
        var invocation = Invocation.from(context);
        return invocation != null && Arrays.stream(McpScope.values())
                .anyMatch(scope -> scope.tool.equals(tool.name()) && invocation.principal().permits(scope));
    }

    private static McpSchema.JsonSchema schema(McpScope scope) {
        Map<String, Object> properties = scope == McpScope.EVALUATIONS
                ? Map.of("round", Map.of("type", "string", "minLength", 1, "maxLength", 120,
                        "description", "评教轮次全名。省略时优先查询进行中的轮次。"))
                : Map.of("term", Map.of("type", "string", "pattern", TERM_PATTERN,
                        "description", "教务学期，例如 2025-2026-2"));
        return McpSchema.JsonSchema.builder().type("object").properties(properties)
                .required(scope == McpScope.GRADES ? List.of("term") : List.of())
                .additionalProperties(false).build();
    }

    private McpSchema.CallToolResult call(McpScope scope, Invocation invocation, Map<String, Object> arguments) {
        if (invocation == null || !invocation.principal().permits(scope)) {
            return error("这把密钥没有权限使用该工具");
        }
        var principal = invocation.principal();
        if (!limiter.tryAcquireWindowEnforced("rl:mcp:query:user:" + principal.userId(), QUERY_LIMIT, WINDOW_SECONDS)) {
            invocation.rateLimited().set(true);
            return error("当前账号已达到每 60 秒 30 次查询额度，请稍后重试");
        }

        long userId = principal.userId();
        String account = principal.account();
        String term = arguments == null ? null : (String) arguments.get("term");
        String round = arguments == null ? null : (String) arguments.get("round");
        try {
            Object data = switch (scope) {
                case SCHEDULE -> schedules.getSchedule(userId, account, term);
                case GRADES -> Map.of("term", term, "grades", grades.getGrades(userId, account).stream()
                        .filter(grade -> term.equals(grade.getTerm() == null ? null : grade.getTerm().trim()))
                        .map(grade -> new GradeItem(grade.getCourseName(), grade.getScore(), grade.getCredit())).toList());
                case EVALUATIONS -> evaluationList(userId, account, round);
                case EXAMS -> new ExamList(term, exams.getExams(userId, account, term).stream()
                        .map(exam -> new ExamItem(exam.getCourseName(), exam.getTime(), exam.getRoom(), exam.getSeat()))
                        .toList());
            };
            return McpSchema.CallToolResult.builder().structuredContent(data).addTextContent(json.writeValueAsString(data)).build();
        } catch (JwMfaPendingException e) {
            return error("学校要求短信二次验证，请在 SAP 中完成教务验证后再重试");
        } catch (BusinessException e) {
            return error(e.getCode() == 400 || e.getCode() == 429 ? e.getMessage() : "教务查询暂未完成，请在 SAP 中检查教务账号后重试");
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("教务 MCP 工具 {} 调用失败 ({})", scope.tool, e.getClass().getSimpleName());
            return error("教务查询暂时不可用，请稍后重试");
        }
    }

    private EvaluationList evaluationList(long userId, String account, String round) {
        var evaluation = evaluations.getOverview(userId, account, round);
        if (round != null && !round.trim().equals(evaluation.getTaskName())) {
            throw new BusinessException(400, "未找到该评教轮次，请使用系统返回的轮次全名");
        }
        var courses = evaluation.getTasks().stream()
                .map(task -> new EvaluationItem(task.getCourseName(), task.getTeacher(), task.isEvaluated(),
                        task.isSubmitted(), task.getStatusText()))
                .toList();
        return new EvaluationList(evaluation.getTaskName(), evaluation.getEndTime(), evaluation.getTerms(), courses);
    }

    private static McpSchema.CallToolResult error(String message) {
        return McpSchema.CallToolResult.builder().isError(true).addTextContent(message).build();
    }
}
