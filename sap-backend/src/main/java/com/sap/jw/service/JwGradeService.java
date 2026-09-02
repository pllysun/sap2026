package com.sap.jw.service;

import com.sap.common.BusinessException;
import com.sap.jw.client.JwHttpSession;
import com.sap.jw.config.JwProperties;
import com.sap.jw.parser.GradeParser;
import com.sap.jw.util.JwErrorMessages;
import com.sap.jw.vo.GradeVO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 成绩服务：抓取并解析学生全部课程成绩。
 */
@Service
public class JwGradeService {

    private static final Logger log = LoggerFactory.getLogger(JwGradeService.class);

    private static final String GRADE_FRAME_PATH = "/jsxsd/kscj/cjcx_frm";
    private static final String CJCX_PATH = "/jsxsd/kscj/cjcx_list";
    /** 旧版路由仅作兼容回退；当前新版由 cjcx_frm + AJAX cjcx_list 提供数据。 */
    private static final List<String> GRADE_PATHS = List.of(
            GRADE_FRAME_PATH,
            "/jsxsd/kscj/cjcx_query",
            CJCX_PATH,
            "/jsxsd/kscj/cjcx_list.do");

    private final JwSessionManager sessionManager;
    private final JwCredentialService credentialService;
    private final GradeParser parser;
    private final JwProperties props;

    public JwGradeService(JwSessionManager sessionManager, JwCredentialService credentialService,
                          GradeParser parser, JwProperties props) {
        this.sessionManager = sessionManager;
        this.credentialService = credentialService;
        this.parser = parser;
        this.props = props;
    }

    public List<GradeVO> getGrades(Long userId, String account) {
        List<String> pages = fetch(sessionManager.getSession(userId, account));
        String html = pages.stream().filter(h -> h != null && !h.isBlank()).findFirst().orElse("");
        // 只有明确返回登录页时才销毁会话；空响应/页面改版不能触发重复 CAS 登录。
        if (!parser.supports(html) && isLoginPage(html)) {
            sessionManager.invalidate(userId, account);
            pages = fetch(sessionManager.getSession(userId, account));
            html = pages.stream().filter(h -> h != null && !h.isBlank()).findFirst().orElse("");
        }
        List<String> gradePages = pages.stream()
                .filter(h -> parser.supports(h) || parser.supportsJson(h)).toList();
        if (gradePages.isEmpty()) {
            if (isLoginPage(html)) {
                throw new BusinessException("教务登录未完成，请稍后重试；若仍失败，请重新绑定教务账号");
            }
            throw new BusinessException("学校成绩页面格式已更新，暂无法读取，请稍后重试");
        }
        credentialService.markSynced(userId, account);
        Map<String, GradeVO> merged = new LinkedHashMap<>();
        for (String page : gradePages) {
            List<GradeVO> parsed = parser.supportsJson(page) ? parser.parseJson(page) : parser.parse(page);
            for (GradeVO grade : parsed) {
                String key = String.join("|", safe(grade.getTerm()), safe(grade.getCourseNo()),
                        safe(grade.getCourseName()), safe(grade.getScore()));
                merged.putIfAbsent(key, grade);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private List<String> fetch(JwHttpSession session) {
        try {
            Map<String, String> headers = navigationHeaders(session);
            String firstNonBlank = "";
            String queryPage = "";
            for (String path : GRADE_PATHS) {
                String url = session.getJwglBase() + path;
                HttpResponse<byte[]> resp = session.getFollow(url, 6, headers);
                String html = new String(resp.body(), StandardCharsets.UTF_8);
                log.info("成绩页面抓取 method=GET path={} status={} bytes={} parserSupported={} shape={}",
                        path, resp.statusCode(), html.getBytes(StandardCharsets.UTF_8).length,
                        parser.supports(html), pageShape(html));
                if (!html.isBlank() && firstNonBlank.isBlank()) firstNonBlank = html;
                if ("/jsxsd/kscj/cjcx_query".equals(path)) queryPage = html;
                if (parser.supports(html)) return List.of(html);
            }

            // 新版 cjcx_frm 只渲染查询表单，表格由 qzTable.js 以 AJAX GET 请求加载 JSON。
            // 该请求必须带分页参数和复选框字段；对 cjcx_list 做 POST 会得到 0 字节响应。
            List<String> ajaxPages = fetchAjaxGrades(session, headers);
            if (ajaxPages != null) return ajaxPages;

            // 新版页面先展示查询表单，实际数据由表单 action /jsxsd/kscj/cjcx_list 返回。
            // 必须原样带上隐藏域和下拉框字段（尤其 kksj/xsfs），仅提交空字段会得到空响应。
            if (!queryPage.isBlank()) {
                FormSpec spec = queryForm(queryPage, session.getJwglBase());
                if (spec != null && !spec.action().isBlank()) {
                    Map<String, String> postHeaders = new LinkedHashMap<>(headers);
                    postHeaders.put("Referer", session.getJwglBase() + "/jsxsd/kscj/cjcx_query");
                    postHeaders.put("Origin", session.getJwglBase());
                    postHeaders.put("Cache-Control", "max-age=0");
                    postHeaders.put("Sec-Fetch-Dest", "document");
                    HttpResponse<String> resp = session.postForm(spec.action(), spec.values(), postHeaders);
                    String html = resp == null || resp.body() == null ? "" : resp.body();
                    log.info("成绩页面抓取 method=FORM action={} values={} formMeta={} status={} location={} contentType={} bytes={} parserSupported={} shape={}",
                            safePath(spec.action()), spec.values(), formMeta(queryPage), resp == null ? -1 : resp.statusCode(),
                            resp == null ? "" : resp.headers().firstValue("location").orElse(""),
                            resp == null ? "" : resp.headers().firstValue("content-type").orElse(""),
                            html.getBytes(StandardCharsets.UTF_8).length, parser.supports(html), pageShape(html));
                    if (parser.supports(html)) return List.of(html);
                    // 新版页面要求按 kksj（开课学期）逐项提交；空值只返回空响应。
                    List<String> gradePages = new ArrayList<>();
                    for (String term : termValues(queryPage)) {
                        Map<String, String> values = new LinkedHashMap<>(spec.values());
                        values.put("kksj", term);
                        HttpResponse<String> termResp = session.postForm(spec.action(), values, postHeaders);
                        String termHtml = termResp == null || termResp.body() == null ? "" : termResp.body();
                        log.info("成绩页面抓取 method=FORM_TERM term={} status={} bytes={} parserSupported={} shape={}",
                                term, termResp == null ? -1 : termResp.statusCode(),
                                termHtml.getBytes(StandardCharsets.UTF_8).length, parser.supports(termHtml), pageShape(termHtml));
                        if (parser.supports(termHtml)) gradePages.add(termHtml);
                        if (termHtml.isBlank()) {
                            String query = "kksj=" + java.net.URLEncoder.encode(term, StandardCharsets.UTF_8)
                                    + "&kcxz=&kcsx=&xsfs=" + java.net.URLEncoder.encode(values.getOrDefault("xsfs", "all"), StandardCharsets.UTF_8)
                                    + "&kcmc=&mold=";
                            HttpResponse<byte[]> getResp = session.getFollow(spec.action() + "?" + query, 4, postHeaders);
                            String getHtml = getResp == null || getResp.body() == null ? ""
                                    : new String(getResp.body(), StandardCharsets.UTF_8);
                            log.info("成绩页面抓取 method=GET_TERM term={} status={} bytes={} parserSupported={} shape={}",
                                    term, getResp == null ? -1 : getResp.statusCode(),
                                    getHtml.getBytes(StandardCharsets.UTF_8).length, parser.supports(getHtml), pageShape(getHtml));
                            if (parser.supports(getHtml)) gradePages.add(getHtml);
                        }
                    }
                    if (!gradePages.isEmpty()) return gradePages;
                    if (!html.isBlank() && firstNonBlank.isBlank()) firstNonBlank = html;
                }
            }

            // 当前页面的筛选表单由浏览器 POST 提交；GET 可能返回空响应或只返回页面壳。
            Map<String, String> form = new LinkedHashMap<>();
            form.put("xnxq01id", "");
            form.put("xnxqid", "");
            form.put("kclx", "");
            form.put("kcsx", "");
            form.put("kcxx", "");
            form.put("xsfs", "");
            Map<String, String> postHeaders = new LinkedHashMap<>(headers);
            postHeaders.put("Referer", session.getJwglBase() + CJCX_PATH);
            postHeaders.put("Origin", session.getJwglBase());
            postHeaders.put("X-Requested-With", "XMLHttpRequest");
            for (String path : GRADE_PATHS) {
                String url = session.getJwglBase() + path;
                HttpResponse<String> resp = session.postForm(url, form, postHeaders);
                String html = resp == null || resp.body() == null ? "" : resp.body();
                log.info("成绩页面抓取 method=POST path={} status={} location={} contentType={} bytes={} parserSupported={} shape={}",
                        path, resp == null ? -1 : resp.statusCode(),
                        resp == null ? "" : resp.headers().firstValue("location").orElse(""),
                        resp == null ? "" : resp.headers().firstValue("content-type").orElse(""),
                        html.getBytes(StandardCharsets.UTF_8).length,
                        parser.supports(html), pageShape(html));
                if (parser.supports(html)) return List.of(html);
                if (!html.isBlank() && firstNonBlank.isBlank()) firstNonBlank = html;
            }
            return firstNonBlank.isBlank() ? List.of() : List.of(firstNonBlank);
        } catch (Exception e) {
            throw new BusinessException("获取成绩失败：" + JwErrorMessages.userDetail(
                    e, "学校成绩系统暂时异常，请稍后重试"));
        }
    }

    /** 调用新版成绩页实际使用的 layui AJAX GET 接口，按 count/pageSize 拉完全部记录。 */
    private List<String> fetchAjaxGrades(JwHttpSession session, Map<String, String> headers) {
        List<String> pages = new ArrayList<>();
        Map<String, String> ajaxHeaders = new LinkedHashMap<>(headers);
        ajaxHeaders.put("Referer", session.getJwglBase() + GRADE_FRAME_PATH);
        ajaxHeaders.put("Accept", "application/json, text/plain, */*");
        ajaxHeaders.put("X-Requested-With", "XMLHttpRequest");
        ajaxHeaders.put("Sec-Fetch-Dest", "empty");
        ajaxHeaders.put("Sec-Fetch-Mode", "cors");
        ajaxHeaders.put("Sec-Fetch-Site", "same-origin");

        final int pageSize = 200;
        for (int page = 1; page <= 50; page++) {
            String query = "kksj=&kcxz=&kcsx=&kcmc=&xsfs=all"
                    + "&sfxsbcxq=1&fxkcmc=&zxsbjg=&showRdDelCj=&mold="
                    + "&pageNum=" + page + "&pageSize=" + pageSize;
            try {
                HttpResponse<byte[]> response = session.getFollow(
                        session.getJwglBase() + CJCX_PATH + "?" + query, 4, ajaxHeaders);
                String body = response == null || response.body() == null ? ""
                        : new String(response.body(), StandardCharsets.UTF_8).trim();
                boolean supported = parser.supportsJson(body);
                int count = parser.jsonCount(body);
                int itemCount = supported ? parser.parseJson(body).size() : 0;
                log.info("成绩页面抓取 method=AJAX_GET path={} page={} pageSize={} status={} bytes={} count={} items={} parserSupported={}",
                        CJCX_PATH, page, pageSize, response == null ? -1 : response.statusCode(),
                        body.getBytes(StandardCharsets.UTF_8).length, count, itemCount, supported);
                if (!supported) return pages.isEmpty() ? null : pages;
                pages.add(body);
                boolean reachedEnd = count >= 0 ? page * pageSize >= count : itemCount < pageSize;
                if (itemCount == 0 || reachedEnd) {
                    return pages;
                }
            } catch (Exception e) {
                log.warn("成绩 AJAX 接口请求失败 page={} detail={}", page,
                        JwErrorMessages.userDetail(e, "请求异常"));
                return pages.isEmpty() ? null : pages;
            }
        }
        return pages.isEmpty() ? null : pages;
    }

    private record FormSpec(String action, Map<String, String> values) {}

    private static FormSpec queryForm(String html, String base) {
        try {
            Document doc = Jsoup.parse(html);
            Element form = doc.selectFirst("form#kscjQueryForm");
            if (form == null) form = doc.select("form").stream()
                    .filter(e -> e.attr("action").contains("cjcx"))
                    .findFirst().orElse(null);
            if (form == null) return null;
            String action = form.attr("abs:action");
            if (action.isBlank()) {
                String declared = form.attr("action").trim();
                // 页面脚本在点击查询时才把 action 动态设为该路径，HTML 本身常为空。
                action = declared.isBlank()
                        ? base + CJCX_PATH
                        : java.net.URI.create(base).resolve(declared).toString();
            }
            Map<String, String> values = new LinkedHashMap<>();
            for (Element select : form.select("select[name]")) {
                Element selected = select.selectFirst("option[selected]");
                if (selected == null) selected = select.selectFirst("option");
                if (selected != null) values.put(select.attr("name"), selected.attr("value"));
            }
            for (Element input : form.select("input[name]")) {
                String name = input.attr("name").trim();
                String type = input.attr("type").trim().toLowerCase(Locale.ROOT);
                if (name.isBlank() || type.equals("button") || type.equals("submit") || type.equals("reset")
                        || type.equals("file") || ((type.equals("checkbox") || type.equals("radio"))
                        && !input.hasAttr("checked"))) continue;
                values.putIfAbsent(name, input.attr("value"));
            }
            return new FormSpec(action, values);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String safePath(String url) {
        try { return java.net.URI.create(url).getPath(); }
        catch (Exception ignored) { return "<invalid>"; }
    }

    private static String safe(String value) { return value == null ? "" : value; }

    private static List<String> termValues(String html) {
        try {
            Document doc = Jsoup.parse(html);
            Element select = doc.selectFirst("select[name=kksj]");
            if (select == null) return List.of();
            return select.select("option").stream().map(e -> e.attr("value").trim())
                    .filter(v -> v.matches("\\d{4}-\\d{4}-\\d"))
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                    .stream().toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /** 记录成绩筛选表单的字段/选项元数据，不记录用户成绩或凭据。 */
    private static String formMeta(String html) {
        if (html == null || html.isBlank()) return "empty";
        try {
            Document doc = Jsoup.parse(html);
            Element form = doc.selectFirst("form#kscjQueryForm");
            if (form == null) form = doc.selectFirst("form");
            if (form == null) return "none";
            return form.select("select[name]").stream().map(s -> {
                String name = s.attr("name");
                String options = s.select("option").stream().map(o -> o.attr("value") + ":" + o.text().trim())
                        .limit(12).collect(java.util.stream.Collectors.joining("|"));
                return name + "=[" + options + "]";
            }).limit(8).collect(java.util.stream.Collectors.joining(","));
        } catch (Exception ignored) {
            return "error";
        }
    }

    /** 仅记录页面结构和路由线索，不记录成绩正文、账号或会话信息。 */
    private static String pageShape(String html) {
        if (html == null || html.isBlank()) return "empty";
        try {
            Document doc = Jsoup.parse(html);
            Elements tables = doc.select("table");
            Elements rows = doc.select("table tr");
            String selects = doc.select("select").stream()
                    .map(e -> e.id().isBlank() ? e.attr("name") : e.id())
                    .filter(s -> !s.isBlank()).limit(12).collect(java.util.stream.Collectors.joining(","));
            String forms = doc.select("form").stream().map(e -> e.attr("action"))
                    .filter(s -> !s.isBlank()).limit(8).collect(java.util.stream.Collectors.joining(","));
            String scripts = doc.select("script[src]").stream().map(e -> e.attr("src"))
                    .filter(s -> !s.isBlank()).limit(8).collect(java.util.stream.Collectors.joining(","));
            String inline = doc.select("script:not([src])").stream().map(Element::data)
                    .filter(s -> s.contains("url") || s.contains("ajax") || s.contains("dataList"))
                    .map(s -> s.replaceAll("\\s+", " ").replaceAll(".{0,80}(?i)(url|ajax|dataList).{0,180}", "$0"))
                    .limit(3).collect(java.util.stream.Collectors.joining(" | "));
            String routes = doc.select("script:not([src])").stream().map(Element::data)
                    .flatMap(s -> java.util.regex.Pattern.compile("['\\\"]([^'\\\"]*(?:cjcx|kscj|dataList)[^'\\\"]*)['\\\"]",
                                    java.util.regex.Pattern.CASE_INSENSITIVE).matcher(s).results().map(m -> m.group(1)))
                    .distinct().limit(20).collect(java.util.stream.Collectors.joining(","));
            String text = doc.text();
            return "title=" + doc.title() + ", tables=" + tables.size() + ", rows=" + rows.size()
                    + ", selects=[" + selects + "], forms=[" + forms + "], scripts=[" + scripts + "]"
                    + ", markers={course=" + text.contains("课程") + ", score=" + text.contains("成绩")
                    + ", login=" + text.contains("登录") + "}, routes=[" + routes + "], inline="
                    + inline.substring(0, Math.min(inline.length(), 500));
        } catch (Exception e) {
            return "shape-error=" + e.getClass().getSimpleName();
        }
    }

    /** 成绩页同课表页一样由框架导航进入；新版 WebVPN 对无导航头的请求可能返回空响应。 */
    private static java.util.Map<String, String> navigationHeaders(JwHttpSession session) {
        String base = session.getJwglBase();
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("Referer", base + "/jsxsd/framework/xsMainV.htmlx");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        headers.put("Upgrade-Insecure-Requests", "1");
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "same-origin");
        headers.put("Sec-Fetch-User", "?1");
        return headers;
    }

    private static boolean isLoginPage(String html) {
        if (html == null) return false;
        String lower = html.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("name=\"loginform\"") || lower.contains("id=\"logindiv\"")
                || lower.contains("/jsxsd/xk/logintoxk") || lower.contains("欢迎登录教务系统");
    }
}
