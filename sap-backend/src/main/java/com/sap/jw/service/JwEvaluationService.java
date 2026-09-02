package com.sap.jw.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sap.common.BusinessException;
import com.sap.jw.client.JwQualitySession;
import com.sap.jw.client.JwQualitySessionExpiredException;
import com.sap.jw.dto.EvalAnswerDTO;
import com.sap.jw.dto.EvalSubmitDTO;
import com.sap.jw.vo.EvalFormVO;
import com.sap.jw.vo.EvalOptionVO;
import com.sap.jw.vo.EvalOverviewVO;
import com.sap.jw.vo.EvalQuestionVO;
import com.sap.jw.vo.EvalResultVO;
import com.sap.jw.vo.EvalRoundVO;
import com.sap.jw.vo.EvalTaskVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 教学质量保障系统学生评教服务。
 * <p>学校直连 CAS 会将已登录教务会话换成该平台的 accessToken；这里直接调用
 * 新平台 REST 接口，不再依赖旧强智 {@code /jsxsd/xspj/**} HTML 页面。</p>
 */
@Service
public class JwEvaluationService {

    private static final String TASK_PATH = "/xspj/xspj/getXspjtask";
    private static final String COURSE_PATH = "/xspj/xspj/getXspjStudentCourses";
    private static final String FORM_PATH = "/xspj/xspj/getXspjTindexSystem";
    private static final String SAVE_PATH = "/xspj/xspj/saveStudentComment";
    private static final DateTimeFormatter COMMIT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final BigDecimal HUNDREDTH = new BigDecimal("0.01");
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final String DEFAULT_COMMENT =
            "老师授课认真负责，讲解清晰，重点突出，能够结合实际帮助理解课程内容，"
                    + "课堂组织有序，答疑耐心细致，学习过程中收获很多，感谢老师的辛勤付出。";

    private final JwQualitySessionManager sessionManager;

    public JwEvaluationService(JwQualitySessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    /** 获取全部评价轮次和指定轮次的课程状态。selector 兼容旧客户端的 term 参数。 */
    public EvalOverviewVO getOverview(Long userId, String account, String selector) {
        List<JSONObject> rounds = fetchRounds(userId, account);
        EvalOverviewVO vo = new EvalOverviewVO();
        vo.setRounds(rounds.stream().map(this::toRoundVO).toList());
        vo.setTerms(rounds.stream().map(r -> r.getString("taskname")).distinct().toList());
        if (rounds.isEmpty()) {
            vo.setTasks(List.of());
            return vo;
        }

        JSONObject round = selectRound(rounds, null, selector);
        applyRound(vo, round);
        vo.setTasks(fetchCourses(userId, account, round.getLong("taskid")).stream()
                .map(course -> toTaskVO(round, course))
                .toList());
        return vo;
    }

    /** 获取一门课程的原始评价题目，供客户端自定义填写。 */
    public EvalFormVO getForm(Long userId, String account, Long taskId, Long courseId) {
        if (taskId == null || courseId == null) throw new BusinessException("缺少评教任务或课程参数");
        JSONObject round = selectRound(fetchRounds(userId, account), taskId, null);
        JSONObject course = findCourse(fetchCourses(userId, account, taskId), courseId);
        if (course.getIntValue("hassubmit") == 1) throw new BusinessException("该课程已经完成评价");
        if (course.getIntValue("hassubmit") == 2) throw new BusinessException("该课程正在评价中，请稍后刷新");
        return buildForm(round, course, fetchQuestions(userId, account, round, course.getString("pjcoursetype")));
    }

    /** 按客户端填写的答案提交一门课程。 */
    public EvalResultVO submit(Long userId, String account, EvalSubmitDTO dto) {
        if (dto == null || dto.getTaskId() == null || dto.getCourseId() == null) {
            throw new BusinessException("缺少评教任务或课程参数");
        }
        JSONObject round = selectRound(fetchRounds(userId, account), dto.getTaskId(), null);
        JSONObject course = findCourse(fetchCourses(userId, account, dto.getTaskId()), dto.getCourseId());
        assertCanSubmit(course);
        List<JSONObject> questions = fetchQuestions(userId, account, round, course.getString("pjcoursetype"));
        return submitOne(userId, account, round, course, questions, dto.getAnswers());
    }

    /**
     * 对指定轮次全部未评课程提交平台允许的最高分。
     * 若任务禁止全选最高分，会在扣分粒度最小的一题上自动减分（当前表单为 0.01 分）。
     */
    public List<EvalResultVO> autoEvaluate(Long userId, String account, Long taskId,
                                           String selector, String comment) {
        String finalComment = comment == null || comment.isBlank() ? DEFAULT_COMMENT : comment.trim();
        JSONObject round = selectRound(fetchRounds(userId, account), taskId, selector);
        List<JSONObject> courses = fetchCourses(userId, account, round.getLong("taskid"));
        Map<String, List<JSONObject>> questionsByType = new HashMap<>();
        List<EvalResultVO> results = new ArrayList<>();
        boolean sourceUnavailable = false;

        for (JSONObject course : courses) {
            if (course.getIntValue("hassubmit") != 0) continue;
            EvalResultVO result = resultFor(course);
            if (sourceUnavailable) {
                result.setRetryable(true);
                result.setSkipped(true);
                result.setMessage("源站服务仍不可用，本课程未提交；稍后再次一键填写时会自动跳过已完成课程");
                results.add(result);
                continue;
            }
            try {
                String type = course.getString("pjcoursetype");
                List<JSONObject> questions = questionsByType.computeIfAbsent(type,
                        ignored -> fetchQuestions(userId, account, round, type));
                List<EvalAnswerDTO> answers = highestAnswers(questions, finalComment,
                        yes(round, "sfqxzdzgf"));
                result = submitOne(userId, account, round, course, questions, answers);
                if (!result.isSuccess() && !result.isPending() && result.isRetryable()) {
                    sourceUnavailable = true;
                }
            } catch (Exception e) {
                result.setSuccess(false);
                result.setRetryable(isSourceInfrastructureFailure(e.getMessage()));
                result.setMessage(friendlySubmitMessage(e.getMessage()));
                if (result.isRetryable()) sourceUnavailable = true;
            }
            results.add(result);
        }
        return results;
    }

    private EvalResultVO submitOne(Long userId, String account, JSONObject round, JSONObject course,
                                   List<JSONObject> questions, List<EvalAnswerDTO> answers) {
        Submission submission = buildSubmission(round, questions, answers);
        JSONObject payload = new JSONObject();
        copy(payload, course, "classno", "coursecode", "coursename", "jobnumber", "studentid",
                "studentname", "yearterm", "teachername", "pjcoursetype", "courseorgcode", "courseorgname");
        payload.put("taskid", round.getLong("taskid"));
        payload.put("totalscore", submission.totalScore());
        payload.put("evaluateResult", submission.results());
        payload.put("commit_time", LocalDateTime.now().format(COMMIT_TIME));

        try {
            JSONObject response = postJson(userId, account, SAVE_PATH, List.of(payload));
            String message = response.getString("message");
            // 个别源站版本会在 HTTP/API code 成功时仍把基础设施错误写进 message，不能误判为成功。
            if (isSourceInfrastructureFailure(message)) {
                return reconcileSubmitFailure(userId, account, round, course, submission,
                        new BusinessException(message));
            }
            EvalResultVO result = resultFor(course);
            result.setSuccess(true);
            result.setScore(format(submission.totalScore()));
            result.setMessage(message == null || message.isBlank() ? "评价提交成功" : message);
            return result;
        } catch (Exception e) {
            // 保存接口可能在源站已落库后才因 MQ/响应链路报错，先查课程状态，绝不盲目重发。
            return reconcileSubmitFailure(userId, account, round, course, submission, e);
        }
    }

    private EvalResultVO reconcileSubmitFailure(Long userId, String account, JSONObject round,
                                                JSONObject course, Submission submission, Exception failure) {
        EvalResultVO result = resultFor(course);
        result.setScore(format(submission.totalScore()));
        try {
            JSONObject latest = findCourse(fetchCourses(userId, account, round.getLong("taskid")),
                    course.getLong("id"));
            int status = latest.getIntValue("hassubmit");
            if (status == 1) {
                result.setSuccess(true);
                result.setMessage("源站返回异常，但已重新核实：评价提交成功");
                return result;
            }
            if (status == 2) {
                result.setPending(true);
                result.setMessage("源站已经接收评价，当前正在处理；请稍后点击同步确认，勿重复提交");
                return result;
            }
        } catch (Exception ignored) {
            // 状态查询也失败时保持“未确认”，由客户端保留手填内容或暂停后续自动提交。
        }

        result.setRetryable(isSourceInfrastructureFailure(failure.getMessage()));
        result.setMessage(friendlySubmitMessage(failure.getMessage()));
        return result;
    }

    private Submission buildSubmission(JSONObject round, List<JSONObject> questions,
                                       List<EvalAnswerDTO> suppliedAnswers) {
        Map<Long, EvalAnswerDTO> answers = new HashMap<>();
        if (suppliedAnswers != null) {
            for (EvalAnswerDTO answer : suppliedAnswers) {
                if (answer == null || answer.getIndexId() == null) continue;
                if (answers.put(answer.getIndexId(), answer) != null) {
                    throw new BusinessException("评价题答案重复：" + answer.getIndexId());
                }
            }
        }

        List<JSONObject> results = new ArrayList<>();
        List<ScoredAnswer> scored = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (JSONObject question : questions) {
            Long indexId = question.getLong("indexid");
            EvalAnswerDTO answer = answers.get(indexId);
            String type = question.getString("type");
            JSONObject item = commonResult(question);
            BigDecimal earned = BigDecimal.ZERO;
            BigDecimal minimum = BigDecimal.ZERO;
            BigDecimal maximum = maxScore(question);
            boolean scoreAnswered = false;

            switch (type == null ? "" : type) {
                case "打分题" -> {
                    BigDecimal score = answer == null ? null : answer.getScore();
                    if (score == null) {
                        requireAnswered(question, false);
                        item.put("index_score", BigDecimal.ZERO);
                        item.put("index_title", "");
                    } else {
                        validateNumeric(question, score, maximum);
                        earned = score;
                        minimum = numericMinimum(question);
                        scoreAnswered = true;
                        item.put("index_score", score);
                        item.put("index_title", format(score));
                    }
                }
                case "单选题" -> {
                    Long optionId = answer == null ? null : answer.getOptionId();
                    JSONObject option = findOption(question, optionId);
                    if (option == null) {
                        requireAnswered(question, false);
                        item.put("index_score", BigDecimal.ZERO);
                        item.put("index_title", "");
                    } else {
                        earned = decimal(option, "score");
                        minimum = optionScoreExtreme(question, false);
                        maximum = optionScoreExtreme(question, true);
                        scoreAnswered = true;
                        item.put("index_score", earned);
                        item.put("index_title", option.getString("title"));
                        item.put("indexid", option.getLong("indexid") == null
                                ? question.getLong("indexid") : option.getLong("indexid"));
                        item.put("option_id", option.getLong("id"));
                    }
                }
                case "多选题" -> {
                    List<Long> optionIds = answer == null ? null : answer.getOptionIds();
                    List<String> titles = new ArrayList<>();
                    if (optionIds != null) {
                        Set<Long> seen = new HashSet<>();
                        for (Long optionId : optionIds) {
                            if (optionId == null || !seen.add(optionId)) continue;
                            JSONObject option = findOption(question, optionId);
                            if (option == null) throw new BusinessException(label(question) + "包含无效选项");
                            titles.add(option.getString("title"));
                        }
                    }
                    requireAnswered(question, !titles.isEmpty());
                    item.put("index_score", BigDecimal.ZERO);
                    item.put("index_title", String.join("*", titles));
                }
                case "填空题" -> {
                    List<String> values = answer == null ? null : answer.getValues();
                    List<String> safe = values == null ? List.of() : values.stream()
                            .map(v -> v == null ? "" : v.trim()).toList();
                    boolean filled = !safe.isEmpty() && safe.stream().allMatch(v -> !v.isBlank());
                    requireAnswered(question, filled);
                    item.put("index_score", BigDecimal.ZERO);
                    item.put("index_title", String.join("*", safe));
                }
                default -> {
                    String text = answer == null || answer.getText() == null ? "" : answer.getText().trim();
                    requireAnswered(question, !text.isBlank());
                    item.put("index_score", BigDecimal.ZERO);
                    item.put("index_title", text);
                }
            }

            boolean scoreRestrictionType = "打分题".equals(type) || "单选题".equals(type);
            if (yes(question, "isscored") && scoreRestrictionType) {
                scored.add(new ScoredAnswer(earned, minimum, maximum, scoreAnswered));
                total = total.add(earned);
            }
            results.add(item);
        }
        validateScoreRestrictions(round, scored);
        return new Submission(total.stripTrailingZeros(), results);
    }

    private List<EvalAnswerDTO> highestAnswers(List<JSONObject> questions, String comment,
                                               boolean mustLeaveHighest) {
        List<EvalAnswerDTO> answers = new ArrayList<>();
        List<AutoCandidate> candidates = new ArrayList<>();
        for (JSONObject question : questions) {
            EvalAnswerDTO answer = new EvalAnswerDTO();
            answer.setIndexId(question.getLong("indexid"));
            String type = question.getString("type");
            if ("打分题".equals(type)) {
                BigDecimal maximum = maxScore(question);
                answer.setScore(maximum);
                if (yes(question, "isscored")) {
                    BigDecimal step = question.getIntValue("scoring_type") == 0 ? HUNDREDTH : ONE;
                    BigDecimal minimum = numericMinimum(question);
                    if (maximum.subtract(step).compareTo(minimum) >= 0) {
                        candidates.add(new AutoCandidate(step, () -> answer.setScore(maximum.subtract(step))));
                    }
                }
            } else if ("单选题".equals(type)) {
                List<JSONObject> options = optionArray(question).stream()
                        .sorted(Comparator.comparing((JSONObject o) -> decimal(o, "score")).reversed())
                        .toList();
                if (!options.isEmpty()) {
                    answer.setOptionId(options.get(0).getLong("id"));
                    if (yes(question, "isscored")) {
                        BigDecimal top = decimal(options.get(0), "score");
                        JSONObject second = options.stream()
                                .filter(o -> decimal(o, "score").compareTo(top) < 0).findFirst().orElse(null);
                        if (second != null) {
                            BigDecimal deduction = top.subtract(decimal(second, "score"));
                            candidates.add(new AutoCandidate(deduction,
                                    () -> answer.setOptionId(second.getLong("id"))));
                        }
                    }
                }
            } else if ("问答题".equals(type)) {
                answer.setText(comment);
            } else if (required(question)) {
                throw new BusinessException(label(question) + "无法自动填写，请使用手动评价");
            }
            answers.add(answer);
        }
        if (mustLeaveHighest) {
            AutoCandidate candidate = candidates.stream()
                    .min(Comparator.comparing(AutoCandidate::deduction)).orElse(null);
            if (candidate == null) throw new BusinessException("该量表禁止全选最高分，请使用手动评价");
            candidate.apply().run();
        }
        return answers;
    }

    private void validateScoreRestrictions(JSONObject round, List<ScoredAnswer> scored) {
        if (scored.isEmpty()) return;
        boolean allHighest = scored.stream().allMatch(s -> s.answered()
                && s.value().compareTo(s.maximum()) == 0);
        boolean allLowest = scored.stream().allMatch(s -> s.answered()
                && s.value().compareTo(s.minimum()) == 0);
        if (yes(round, "sfqxzdzgf") && allHighest) {
            throw new BusinessException("平台不允许所有评分题均填写最高分，请至少调整一项");
        }
        if (yes(round, "sfqxzdf") && allLowest) {
            throw new BusinessException("平台不允许所有评分题均填写最低分，请至少调整一项");
        }
    }

    private EvalFormVO buildForm(JSONObject round, JSONObject course, List<JSONObject> questions) {
        EvalFormVO vo = new EvalFormVO();
        vo.setTaskId(round.getLong("taskid"));
        vo.setTaskName(round.getString("taskname"));
        vo.setCourseId(course.getLong("id"));
        vo.setCourseCode(course.getString("coursecode"));
        vo.setCourseName(course.getString("coursename"));
        vo.setTeacherNo(course.getString("jobnumber"));
        vo.setTeacher(course.getString("teachername"));
        vo.setTypeName(course.getString("pjcoursetype"));
        vo.setRestrictHighest(yes(round, "sfqxzdzgf"));
        vo.setRestrictLowest(yes(round, "sfqxzdf"));
        vo.setDefaultComment(DEFAULT_COMMENT);
        BigDecimal total = questions.stream().filter(q -> yes(q, "isscored"))
                .map(this::questionMaximum).reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setMaxTotal(total.doubleValue());
        vo.setQuestions(questions.stream().map(this::toQuestionVO).toList());
        return vo;
    }

    private EvalQuestionVO toQuestionVO(JSONObject question) {
        EvalQuestionVO vo = new EvalQuestionVO();
        vo.setIndexId(question.getLong("indexid"));
        vo.setOrder(question.getInteger("ordor"));
        vo.setSection(question.getString("firstlevlindex"));
        vo.setType(question.getString("type"));
        vo.setTitle(question.getString("title"));
        vo.setRemark(question.getString("remark"));
        vo.setRequired(required(question));
        vo.setScored(yes(question, "isscored"));
        vo.setScoringType(question.getInteger("scoring_type"));
        vo.setMaxScore(questionMaximum(question).doubleValue());
        vo.setOptions(optionArray(question).stream().map(option -> {
            EvalOptionVO o = new EvalOptionVO();
            o.setId(option.getLong("id"));
            o.setTitle(option.getString("title"));
            o.setScore(decimal(option, "score").doubleValue());
            return o;
        }).toList());
        return vo;
    }

    private List<JSONObject> fetchRounds(Long userId, String account) {
        JSONObject data = data(post(userId, account, TASK_PATH, Map.of()));
        List<JSONObject> out = new ArrayList<>();
        addObjects(out, data.getJSONArray("pageData"));
        return out;
    }

    private List<JSONObject> fetchCourses(Long userId, String account, Long taskId) {
        JSONObject data = data(post(userId, account, COURSE_PATH, Map.of("taskid", taskId)));
        List<JSONObject> out = new ArrayList<>();
        addObjects(out, data.getJSONArray("pageData"));
        return out;
    }

    private List<JSONObject> fetchQuestions(Long userId, String account, JSONObject round, String courseType) {
        String indexId = round.getString("indexid");
        if (indexId == null || indexId.isBlank() || courseType == null || courseType.isBlank()) {
            throw new BusinessException("该课程未配置可用的评价量表");
        }
        JSONObject data = data(post(userId, account, FORM_PATH, Map.of(
                "indexid", indexId, "pjcoursetype", courseType)));
        List<JSONObject> out = new ArrayList<>();
        addObjects(out, data.getJSONArray("pageData"));
        if (out.isEmpty()) throw new BusinessException("该课程暂无可用的评价表");
        return out;
    }

    private JSONObject post(Long userId, String account, String path, Map<String, ?> params) {
        return withRetry(userId, account, session -> session.post(path, params));
    }

    private JSONObject postJson(Long userId, String account, String path, Object body) {
        return withRetry(userId, account, session -> session.postJson(path, body));
    }

    private JSONObject withRetry(Long userId, String account, Function<JwQualitySession, JSONObject> action) {
        try {
            return action.apply(sessionManager.getSession(userId, account));
        } catch (JwQualitySessionExpiredException e) {
            sessionManager.invalidate(userId, account);
            return action.apply(sessionManager.getSession(userId, account));
        }
    }

    private JSONObject selectRound(List<JSONObject> rounds, Long taskId, String selector) {
        if (rounds.isEmpty()) throw new BusinessException("当前没有教学评价任务");
        if (taskId != null) {
            return rounds.stream().filter(r -> taskId.equals(r.getLong("taskid"))).findFirst()
                    .orElseThrow(() -> new BusinessException("未找到指定的教学评价任务"));
        }
        if (selector != null && !selector.isBlank()) {
            String wanted = selector.trim();
            JSONObject matched = rounds.stream().filter(r -> wanted.equals(r.getString("taskname"))
                    || wanted.equals(r.getString("taskid")) || wanted.equals(r.getString("yearterm")))
                    .findFirst().orElse(null);
            if (matched != null) return matched;
        }
        return rounds.stream().filter(r -> "进行中".equals(r.getString("currentStatus"))
                        || r.getIntValue("status") == 1)
                .findFirst().orElse(rounds.get(0));
    }

    private JSONObject findCourse(List<JSONObject> courses, Long courseId) {
        return courses.stream().filter(c -> courseId.equals(c.getLong("id"))).findFirst()
                .orElseThrow(() -> new BusinessException("未找到指定的待评课程"));
    }

    private void applyRound(EvalOverviewVO vo, JSONObject round) {
        vo.setTerm(round.getString("taskname"));
        vo.setTaskId(round.getLong("taskid"));
        vo.setTaskName(round.getString("taskname"));
        vo.setStartTime(round.getString("starttime"));
        vo.setEndTime(round.getString("endtime"));
        vo.setStatus(round.getString("currentStatus"));
        vo.setRestrictHighest(yes(round, "sfqxzdzgf"));
        vo.setRestrictLowest(yes(round, "sfqxzdf"));
    }

    private EvalRoundVO toRoundVO(JSONObject round) {
        EvalRoundVO vo = new EvalRoundVO();
        vo.setId(round.getLong("taskid"));
        vo.setName(round.getString("taskname"));
        vo.setStartTime(round.getString("starttime"));
        vo.setEndTime(round.getString("endtime"));
        vo.setStatus(round.getString("currentStatus"));
        return vo;
    }

    private EvalTaskVO toTaskVO(JSONObject round, JSONObject course) {
        EvalTaskVO vo = new EvalTaskVO();
        vo.setTerm(round.getString("taskname"));
        vo.setTaskId(round.getLong("taskid"));
        vo.setCourseId(course.getLong("id"));
        vo.setCourseCode(course.getString("coursecode"));
        vo.setCourseName(course.getString("coursename"));
        vo.setClassNo(course.getString("classno"));
        vo.setTeacherNo(course.getString("jobnumber"));
        vo.setTeacher(course.getString("teachername"));
        vo.setCollege(course.getString("courseorgname"));
        vo.setTypeName(course.getString("pjcoursetype"));
        int status = course.getIntValue("hassubmit");
        vo.setStatus(status);
        vo.setStatusText(status == 1 ? "已评价" : status == 2 ? "评价中" : "未评价");
        vo.setEvaluated(status == 1);
        vo.setSubmitted(status == 1);
        vo.setJx0404id(String.valueOf(course.getLong("id")));
        return vo;
    }

    private EvalResultVO resultFor(JSONObject course) {
        EvalResultVO result = new EvalResultVO();
        result.setCourseId(course.getLong("id"));
        result.setCourseName(course.getString("coursename"));
        result.setTeacher(course.getString("teachername"));
        result.setTypeName(course.getString("pjcoursetype"));
        return result;
    }

    private JSONObject commonResult(JSONObject question) {
        JSONObject item = new JSONObject();
        item.put("index_order", question.getInteger("ordor"));
        item.put("sfbt", question.getString("isemptyed"));
        item.put("yjzb", question.getString("firstlevlindex"));
        item.put("index_type", question.getString("type"));
        item.put("indexid", question.getLong("indexid"));
        return item;
    }

    private void assertCanSubmit(JSONObject course) {
        int status = course.getIntValue("hassubmit");
        if (status == 1) throw new BusinessException("该课程已经完成评价，不能重复提交");
        if (status == 2) throw new BusinessException("该课程正在评价中，请稍后刷新后再试");
    }

    private void validateNumeric(JSONObject question, BigDecimal score, BigDecimal maximum) {
        if (score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(maximum) > 0) {
            throw new BusinessException(label(question) + "应在 0～" + format(maximum) + " 分之间");
        }
        int scoringType = question.getIntValue("scoring_type");
        BigDecimal normalized = score.stripTrailingZeros();
        if (scoringType == 0 && Math.max(normalized.scale(), 0) > 2) {
            throw new BusinessException(label(question) + "最多保留两位小数");
        }
        if ((scoringType == 1 || scoringType == 2) && normalized.scale() > 0) {
            throw new BusinessException(label(question) + "只能填写整数");
        }
        if (scoringType == 1 && score.compareTo(BigDecimal.ONE) < 0) {
            throw new BusinessException(label(question) + "须填写正整数");
        }
    }

    private void requireAnswered(JSONObject question, boolean answered) {
        if (required(question) && !answered) throw new BusinessException(label(question) + "为必填项");
    }

    private JSONObject findOption(JSONObject question, Long optionId) {
        if (optionId == null) return null;
        return optionArray(question).stream().filter(o -> optionId.equals(o.getLong("id")))
                .findFirst().orElseThrow(() -> new BusinessException(label(question) + "选项无效"));
    }

    private List<JSONObject> optionArray(JSONObject question) {
        List<JSONObject> out = new ArrayList<>();
        addObjects(out, question.getJSONArray("optionarr"));
        return out;
    }

    private BigDecimal questionMaximum(JSONObject question) {
        if ("单选题".equals(question.getString("type"))) return optionScoreExtreme(question, true);
        return maxScore(question);
    }

    private BigDecimal maxScore(JSONObject question) {
        return decimal(question, "score").multiply(decimal(question, "weight"));
    }

    private BigDecimal numericMinimum(JSONObject question) {
        return question.getIntValue("scoring_type") == 1 ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    private BigDecimal optionScoreExtreme(JSONObject question, boolean maximum) {
        return optionArray(question).stream().map(o -> decimal(o, "score"))
                .reduce(maximum ? BigDecimal::max : BigDecimal::min).orElse(BigDecimal.ZERO);
    }

    private boolean required(JSONObject question) {
        return "否".equals(question.getString("isemptyed"));
    }

    private boolean yes(JSONObject object, String key) {
        return "是".equals(object.getString(key));
    }

    private BigDecimal decimal(JSONObject object, String key) {
        BigDecimal value = object.getBigDecimal(key);
        return value == null ? BigDecimal.ZERO : value;
    }

    private JSONObject data(JSONObject response) {
        JSONObject data = response == null ? null : response.getJSONObject("data");
        if (data == null) throw new BusinessException("教学评价系统返回数据为空");
        return data;
    }

    private void addObjects(List<JSONObject> target, JSONArray array) {
        if (array == null) return;
        for (Object item : array) {
            if (item instanceof JSONObject object) target.add(object);
        }
    }

    private void copy(JSONObject target, JSONObject source, String... keys) {
        for (String key : keys) target.put(key, source.get(key));
    }

    private String label(JSONObject question) {
        Integer order = question.getInteger("ordor");
        return "第" + (order == null ? "?" : order) + "题";
    }

    private String format(BigDecimal value) {
        if (value == null) return null;
        return value.setScale(Math.max(0, value.stripTrailingZeros().scale()), RoundingMode.UNNECESSARY)
                .toPlainString();
    }

    private boolean isSourceInfrastructureFailure(String message) {
        if (message == null || message.isBlank()) return false;
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("mq") || lower.contains("rabbit") || lower.contains("kafka")
                || lower.contains("timeout") || lower.contains("timed out")
                || lower.contains("connection") || lower.contains("http 5")
                || message.contains("消息队列") || message.contains("连接超时")
                || message.contains("连接失败") || message.contains("连接被重置")
                || message.contains("服务未启动") || message.contains("服务暂时")
                || message.contains("系统繁忙") || message.contains("暂时不可用");
    }

    private String friendlySubmitMessage(String message) {
        if (message == null || message.isBlank()) return "评价提交失败，请稍后重试";
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("mq") || lower.contains("rabbit") || lower.contains("kafka")
                || message.contains("消息队列")) {
            return "源站消息队列暂时异常，本次评价尚未确认提交；请稍后重试";
        }
        if (isSourceInfrastructureFailure(message)) {
            return "源站服务暂时异常，本次评价尚未确认提交；请稍后重试";
        }
        return message;
    }

    private record Submission(BigDecimal totalScore, List<JSONObject> results) {}
    private record ScoredAnswer(BigDecimal value, BigDecimal minimum, BigDecimal maximum, boolean answered) {}
    private record AutoCandidate(BigDecimal deduction, Runnable apply) {}
}
