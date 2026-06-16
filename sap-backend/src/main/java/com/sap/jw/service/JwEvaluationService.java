package com.sap.jw.service;

import com.sap.common.BusinessException;
import com.sap.jw.client.JwHttpSession;
import com.sap.jw.config.JwProperties;
import com.sap.jw.parser.EvaluationParser;
import com.sap.jw.parser.EvaluationParser.EvalForm;
import com.sap.jw.parser.EvaluationParser.Round;
import com.sap.jw.vo.EvalOverviewVO;
import com.sap.jw.vo.EvalResultVO;
import com.sap.jw.vo.EvalTaskVO;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 学生评教服务：
 * <ul>
 *   <li>{@link #getOverview} —— 列某学期评教任务（已评 + 未评），供查看与一键评教前置。</li>
 *   <li>{@link #autoEvaluate} —— 一键自动评教：每项指标选满分，仅留 1 项（扣分最小者）次高档以
 *       绕过“不能全选同一档”限制，加固定评语后提交（issubmit=1，不可撤销）。</li>
 * </ul>
 * 评教页均为 UTF-8。账号当前无待评数据，提交分支未能联调，逻辑严格依据真实表单逆向实现。
 */
@Service
public class JwEvaluationService {

    private static final String FIND_PATH = "/jsxsd/xspj/xspj_find.do";
    private static final String SAVE_PATH = "/jsxsd/xspj/xspj_save.do";
    private static final String LIST_PATH = "/jsxsd/xspj/xspj_list.do";
    /** 默认固定评语（强智要求评语≥30个汉字）。 */
    private static final String DEFAULT_COMMENT =
            "老师授课认真负责，讲解深入浅出，重点突出，课堂氛围活跃，注重理论联系实际，"
                    + "布置作业批改及时，答疑耐心细致，让我受益匪浅，收获很大，非常感谢老师的辛勤付出！";

    private final JwSessionManager sessionManager;
    private final EvaluationParser parser;
    private final JwProperties props;

    public JwEvaluationService(JwSessionManager sessionManager, EvaluationParser parser, JwProperties props) {
        this.sessionManager = sessionManager;
        this.parser = parser;
        this.props = props;
    }

    /** 评教总览：term 为空取最新一轮。 */
    public EvalOverviewVO getOverview(Long userId, String account, String term) {
        JwHttpSession session = sessionManager.getSession(userId, account);
        String findHtml = get(session, FIND_PATH);
        if (!findHtml.contains("xspj_list")) {
            sessionManager.invalidate(userId, account);
            session = sessionManager.getSession(userId, account);
            findHtml = get(session, FIND_PATH);
        }
        List<Round> rounds = parser.parseRounds(findHtml);
        EvalOverviewVO vo = new EvalOverviewVO();
        List<String> terms = new ArrayList<>();
        for (Round r : rounds) if (!terms.contains(r.term)) terms.add(r.term);
        vo.setTerms(terms);
        if (rounds.isEmpty()) {
            vo.setTasks(new ArrayList<>());
            return vo;
        }
        Round target = null;
        if (term != null && !term.isBlank()) {
            for (Round r : rounds) if (term.equals(r.term)) { target = r; break; }
        }
        if (target == null) target = rounds.get(0); // find 页新→旧
        vo.setTerm(target.term);
        vo.setTasks(fetchTasks(session, target));
        return vo;
    }

    private List<EvalTaskVO> fetchTasks(JwHttpSession session, Round round) {
        List<EvalTaskVO> tasks = new ArrayList<>();
        if (round.types.isEmpty()) {
            // 没有细分类型则用聚合列表
            String url = LIST_PATH + "?pj0502id=" + round.pj0502id + "&xnxq01id=" + enc(round.term);
            tasks.addAll(parser.parseTasks(get(session, url), round.term, null));
            return tasks;
        }
        for (String[] type : round.types) {
            String url = LIST_PATH + "?pj0502id=" + round.pj0502id
                    + "&xnxq01id=" + enc(round.term) + "&pj01id=" + type[0];
            tasks.addAll(parser.parseTasks(get(session, url), round.term, type[1]));
        }
        return tasks;
    }

    /** 一键自动评教：对该学期所有未评任务自动填分+评语+提交。 */
    public List<EvalResultVO> autoEvaluate(Long userId, String account, String term, String comment) {
        String cmt = (comment == null || comment.isBlank()) ? DEFAULT_COMMENT : comment.trim();
        if (cmt.length() < 30) throw new BusinessException("评语须不少于 30 个字");

        EvalOverviewVO overview = getOverview(userId, account, term);
        List<EvalResultVO> results = new ArrayList<>();
        JwHttpSession session = sessionManager.getSession(userId, account);
        for (EvalTaskVO task : overview.getTasks()) {
            if (task.isEvaluated()) continue;
            results.add(evaluateOne(session, task, cmt));
        }
        return results;
    }

    private EvalResultVO evaluateOne(JwHttpSession session, EvalTaskVO task, String comment) {
        EvalResultVO r = new EvalResultVO();
        r.setTeacher(task.getTeacher());
        r.setTypeName(task.getTypeName());
        try {
            String editHtml = get(session, props.getJwglBase() + ensureAbs(task.getEditUrl()));
            EvalForm form = parser.parseForm(editHtml);
            if (form.radios.isEmpty()) {
                r.setSuccess(false);
                r.setMessage("评教表单无指标，无法自动评教");
                return r;
            }
            Map<String, String> choices = decide(form);
            List<String[]> body = buildBody(form, choices, comment);
            String saveUrl = props.getJwglBase()
                    + (form.saveAction == null || form.saveAction.isBlank() ? SAVE_PATH : form.saveAction);
            HttpResponse<String> resp = session.postFormPairs(saveUrl, body);
            String html = resp.body() == null ? "" : resp.body();
            boolean ok = resp.statusCode() < 400 && !html.contains("失败")
                    && !html.contains("错误") && !html.contains("必须") && !html.contains("不能");
            r.setSuccess(ok);
            r.setScore(formatScore(totalScore(form, choices)));
            r.setMessage(ok ? "评教提交成功" : snippet(html));
        } catch (Exception e) {
            r.setSuccess(false);
            r.setMessage("提交异常：" + e.getMessage());
        }
        return r;
    }

    /** 每项指标选满分(col0)，仅扣分最小的一项选次高档(col1)，绕过“不能全选同一档”。 */
    private Map<String, String> decide(EvalForm form) {
        String targetXh = null;
        double minDed = Double.MAX_VALUE;
        for (Map.Entry<String, List<String>> e : form.radios.entrySet()) {
            List<String> opts = e.getValue();
            if (opts.size() < 2) continue;
            Double s0 = form.fz.get(e.getKey() + "|" + opts.get(0));
            Double s1 = form.fz.get(e.getKey() + "|" + opts.get(1));
            double ded = (s0 != null && s1 != null) ? (s0 - s1) : 0.0;
            if (ded < minDed) { minDed = ded; targetXh = e.getKey(); }
        }
        Map<String, String> choices = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : form.radios.entrySet()) {
            List<String> opts = e.getValue();
            boolean leaveOne = e.getKey().equals(targetXh) && opts.size() >= 2;
            choices.put(e.getKey(), opts.get(leaveOne ? 1 : 0));
        }
        return choices;
    }

    private List<String[]> buildBody(EvalForm form, Map<String, String> choices, String comment) {
        List<String[]> body = new ArrayList<>();
        for (String[] kv : form.hidden) {
            body.add(new String[]{kv[0], "issubmit".equals(kv[0]) ? "1" : kv[1]});
        }
        for (Map.Entry<String, String> c : choices.entrySet()) {
            body.add(new String[]{"pj0601id_" + c.getKey(), c.getValue()});
        }
        body.add(new String[]{"jynr", comment});
        return body;
    }

    private double totalScore(EvalForm form, Map<String, String> choices) {
        double sum = 0;
        for (Map.Entry<String, String> c : choices.entrySet()) {
            Double v = form.fz.get(c.getKey() + "|" + c.getValue());
            if (v != null) sum += v;
        }
        return sum;
    }

    private String formatScore(double s) {
        if (s <= 0) return null;
        return s == Math.floor(s) ? String.valueOf((long) s) : String.format("%.2f", s);
    }

    private String get(JwHttpSession session, String pathOrUrl) {
        String url = pathOrUrl.startsWith("http") ? pathOrUrl : props.getJwglBase() + pathOrUrl;
        try {
            HttpResponse<byte[]> resp = session.getFollow(url, 8);
            return new String(resp.body(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("获取评教页失败：" + e.getMessage());
        }
    }

    private String ensureAbs(String editUrl) {
        return editUrl.startsWith("/") ? editUrl : "/" + editUrl;
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String snippet(String html) {
        String t = html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return t.length() > 60 ? t.substring(0, 60) : (t.isEmpty() ? "提交结果未知" : t);
    }
}
