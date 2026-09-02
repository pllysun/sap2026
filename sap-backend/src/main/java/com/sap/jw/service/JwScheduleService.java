package com.sap.jw.service;

import com.sap.common.BusinessException;
import com.sap.jw.client.JwHttpSession;
import com.sap.jw.config.JwProperties;
import com.sap.jw.parser.ScheduleParser;
import com.sap.jw.util.JwErrorMessages;
import com.sap.jw.vo.ScheduleVO;
import com.sap.jw.vo.TermVO;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 课表服务：用会员的教务会话抓取并解析课表。
 */
@Service
public class JwScheduleService {

    private static final Logger log = LoggerFactory.getLogger(JwScheduleService.class);

    private static final String XSKB_PATH = "/jsxsd/xskb/xskb_list.do";
    /** 强智不同部署的课表路由后缀并不完全一致，按优先级兼容，不改变旧版首选路径。 */
    private static final List<String> XSKB_PATH_ALIASES = List.of(
            // 新版框架菜单中的“课表列表查询”页面：部分节点已将可解析课表
            // 从旧的 xskb 页面迁移到该路由。
            "/jsxsd/kbcx/kbxxMain",
            "/jsxsd/kbcx/kbxx_xzb_ifr",
            "/jsxsd/kbcx/kbxx_xzb",
            "/jsxsd/kbcx/kbxx_teacher",
            "/jsxsd/kbcx/kbxx_classroom",
            "/jsxsd/kbcx/kbxx_kc",
            "/jsxsd/xskb/xskb_list",
            "/jsxsd/xskb/xskb_list.htmlx",
            "/jsxsd/xskb/xskb_list.html"
    );
    private static final Pattern SELECTED_TERM = Pattern.compile(
            "(?is)<select[^>]+name=[\\\"']xnxq01id[\\\"'][^>]*>.*?<option[^>]+value=[\\\"']([^\\\"']+)[\\\"'][^>]*selected");

    /** 新版课表由“父查询页 + 异步数据页”组成，不能让异步响应覆盖父页学期元数据。 */
    private record FetchResult(String html, List<TermVO> terms, String selectedTerm) {}

    private final JwSessionManager sessionManager;
    private final JwCredentialService credentialService;
    private final ScheduleParser parser;
    private final JwCalendarService calendarService;
    private final JwProperties props;

    public JwScheduleService(JwSessionManager sessionManager, JwCredentialService credentialService,
                             ScheduleParser parser, JwCalendarService calendarService, JwProperties props) {
        this.sessionManager = sessionManager;
        this.credentialService = credentialService;
        this.parser = parser;
        this.calendarService = calendarService;
        this.props = props;
    }

    /**
     * 获取指定学期课表；term 为空则取教务默认（当前）学期。
     * 会话中途失效时自动重登一次。
     */
    public ScheduleVO getSchedule(Long userId, String account, String term) {
        FetchResult fetched = fetch(sessionManager.getSession(userId, account), term);
        if (!parser.supports(fetched.html())) {
            // 只有明确拿到登录页时才重登；页面结构更新、网关错误等情况不要
            // 销毁仍然有效的会话，避免每次点击“重试”都再走一遍 CAS。
            if (isLoginPage(fetched.html())) {
                sessionManager.invalidate(userId, account);
                fetched = fetch(sessionManager.getSession(userId, account), term);
            }
        }
        if (!parser.supports(fetched.html())) {
            if (isLoginPage(fetched.html())) {
                throw new BusinessException("教务登录未完成，请稍后重试；若仍失败，请重新绑定教务账号");
            }
            throw new BusinessException("学校课表页面格式已更新，暂无法读取，请稍后重试");
        }
        credentialService.markSynced(userId, account);
        ScheduleVO vo = parser.parse(fetched.html());
        mergeTermMetadata(vo, fetched.terms(), fetched.selectedTerm(), term);

        // 开学日期：对所抓学期取教学周历开学日。已缓存(DB)的直接命中、不重复抓，
        // 故重新扫描各学期时每个学期仅首次抓一次，之后永久复用，风控可控。
        if (vo.getTerm() != null) {
            vo.setSemesterStartDate(calendarService.getSemesterStart(userId, account, vo.getTerm()));
        }
        return vo;
    }

    private FetchResult fetch(JwHttpSession session, String term) {
        String url = session.getJwglBase() + XSKB_PATH;
        // 新版个人课表页是一个外层标签壳，真正的表格位于同一路由的
        // `viweType=0` iframe（源站拼写即为 viweType）。不带该参数会再次
        // 返回外层壳，导致解析器永远看不到课程表。
        url += "?viweType=0";
        if (term != null && !term.isBlank()) {
            url += "&xnxq01id=" + URLEncoder.encode(term.trim(), StandardCharsets.UTF_8);
        }
        try {
            List<TermVO> discoveredTerms = new ArrayList<>();
            String selected = null;
            Map<String, String> headers = navigationHeaders(session);
            // 新版强智把功能页挂在框架壳下；安全记录框架中声明的实际路由，便于
            // 在节点切换时发现课表入口，而不记录页面正文或任何会话信息。
            String frameworkUrl = session.getJwglBase() + "/jsxsd/framework/xsMainV.htmlx";
            HttpResponse<byte[]> framework = session.getFollow(frameworkUrl, 6, headers);
            logFetch(frameworkUrl, framework, body(framework), "framework-probe");
            HttpResponse<byte[]> resp = session.getFollow(url, 6, headers);
            String html = body(resp);
            mergeTermOptions(discoveredTerms, html);
            selected = selectedTermOrFirst(html, discoveredTerms, selected);
            logFetch(url, resp, html, "primary");

            // 个人课表入口是自嵌套 iframe：外层只负责加载页面资源，浏览器随后
            // 以 iframe 导航头再次请求同一路由。服务端抓取也必须复现这第二跳，
            // 否则永远只能拿到 3KB 的空壳页面。
            if (!parser.supports(html)) {
                Map<String, String> iframeHeaders = new LinkedHashMap<>(headers);
                iframeHeaders.put("Referer", url);
                iframeHeaders.put("Sec-Fetch-Dest", "iframe");
                iframeHeaders.put("Sec-Fetch-Mode", "navigate");
                HttpResponse<byte[]> iframeResp = session.getFollow(url, 6, iframeHeaders);
                String iframeHtml = body(iframeResp);
                mergeTermOptions(discoveredTerms, iframeHtml);
                logFetch(url, iframeResp, iframeHtml, "primary-iframe");
                if (parser.supports(iframeHtml)) return new FetchResult(iframeHtml, discoveredTerms, selected);

                // 源站外层壳声明的参数拼写为 viweType，但部分节点使用修正后的
                // viewType；同时历史学期需要把 xnxq01id 一并带到 iframe 请求。
                // 逐一尝试这几个等价变体，避免把浏览器可正常显示的课表误判为空壳。
                String termQuery = term == null || term.isBlank() ? ""
                        : "&xnxq01id=" + URLEncoder.encode(term.trim(), StandardCharsets.UTF_8);
                List<String> iframeVariants = List.of(
                        session.getJwglBase() + XSKB_PATH + "?viweType=0" + termQuery,
                        session.getJwglBase() + XSKB_PATH + "?viewType=0" + termQuery);
                for (String variant : iframeVariants) {
                    if (variant.equals(url)) continue;
                    iframeHeaders.put("Referer", url);
                    HttpResponse<byte[]> variantResp = session.getFollow(variant, 6, iframeHeaders);
                    String variantHtml = body(variantResp);
                    mergeTermOptions(discoveredTerms, variantHtml);
                    selected = selectedTermOrFirst(variantHtml, discoveredTerms, selected);
                    logFetch(variant, variantResp, variantHtml, "primary-iframe-variant");
                    if (parser.supports(variantHtml)) {
                        return new FetchResult(variantHtml, discoveredTerms, selected);
                    }
                }
                if (term != null && !term.isBlank()) {
                    Map<String, String> iframeForm = Map.of("viweType", "0", "xnxq01id", term.trim());
                    HttpResponse<String> postIframe = session.postForm(
                            session.getJwglBase() + XSKB_PATH, iframeForm, iframeHeaders);
                    String postHtml = postIframe == null ? "" : postIframe.body();
                    logFetchStatus(session.getJwglBase() + XSKB_PATH, postIframe == null ? -1 : postIframe.statusCode(),
                            postHtml, "primary-iframe-post");
                    if (parser.supports(postHtml)) {
                        return new FetchResult(postHtml, discoveredTerms, term.trim());
                    }
                }
            }

            // 2026 年新版强智部分节点去掉 .do 后缀；先在同一已认证会话内尝试路由别名，
            // 不要因为页面路由变化就立即销毁会话并重复 CAS 登录。
            if (!parser.supports(html)) {
                for (String alias : XSKB_PATH_ALIASES) {
                    String aliasUrl = session.getJwglBase() + alias;
                    if (term != null && !term.isBlank()) {
                        aliasUrl += "?xnxq01id=" + URLEncoder.encode(term.trim(), StandardCharsets.UTF_8);
                    }
                    HttpResponse<byte[]> aliasResp = session.getFollow(aliasUrl, 6, headers);
                    String aliasHtml = body(aliasResp);
                    mergeTermOptions(discoveredTerms, aliasHtml);
                    selected = selectedTermOrFirst(aliasHtml, discoveredTerms, selected);
                    logFetch(aliasUrl, aliasResp, aliasHtml, "route-alias");
                    if (parser.supports(aliasHtml)) {
                        return new FetchResult(aliasHtml, discoveredTerms, selected);
                    }
                    // 父查询页（kbxx_xzb）把实际表格声明为 *_ifr；其 src 由浏览器
                    // 在提交筛选表单后加载。服务端直接访问父页时也要主动跟随这个
                    // iframe，并提交当前学期，否则只会得到筛选表单本身。
                    if (alias.endsWith("/kbxx_xzb")) {
                        String requestedTerm = term != null && !term.isBlank() ? term.trim() : selected;
                        if (requestedTerm != null && !requestedTerm.isBlank()) {
                            String iframeUrl = session.getJwglBase() + alias + "_ifr";
                            Map<String, String> params = selectedFormValues(aliasHtml);
                            params.put("xnxq01id", requestedTerm);
                            Map<String, String> ajaxHeaders = new LinkedHashMap<>(headers);
                            ajaxHeaders.put("Referer", aliasUrl);
                            ajaxHeaders.put("X-Requested-With", "XMLHttpRequest");
                            HttpResponse<String> iframePost = session.postForm(iframeUrl, params, ajaxHeaders);
                            String iframeHtml = iframePost == null ? "" : iframePost.body();
                            logFetchStatus(iframeUrl, iframePost == null ? -1 : iframePost.statusCode(), iframeHtml, "route-iframe-post");
                            log.info("课表异步接口响应 path={} apiShape={}", safePath(iframeUrl), apiShape(iframeHtml));
                            if (parser.supports(iframeHtml)) return new FetchResult(iframeHtml, discoveredTerms, requestedTerm);
                        }
                    }
                    // 新版课表查询页把数据表放在 *_ifr 子路由中，页面脚本以 POST
                    // 提交学期筛选；GET 会得到 404 或空壳。复用同一会话补发最小
                    // 学期参数，成功后交给同一解析器判断，避免猜测页面内容。
                    if (alias.endsWith("_ifr")) {
                        String requestedTerm = selectedTermOrFirst(aliasHtml, discoveredTerms, selected);
                        if (requestedTerm != null && !requestedTerm.isBlank()) {
                            HttpResponse<String> post = session.postForm(aliasUrl,
                                    Map.of("xnxq01id", requestedTerm));
                            String postHtml = post == null ? "" : post.body();
                            logFetchStatus(aliasUrl, -1, postHtml, "route-alias-post");
                            if (parser.supports(postHtml)) return new FetchResult(postHtml, discoveredTerms, requestedTerm);
                        }
                    }
                }
            }

            // 新版强智从首页进入课表页时，默认学期偶尔不会随首次 GET 写入上下文，
            // 页面只返回学期下拉而不返回 #kbtable。按页面已选学期补发一次明确查询，
            // 避免把正常会话误判为失效并重复登录。
            if (!parser.supports(html) && (term == null || term.isBlank())) {
                String requestedTerm = selectedTermOrFirst(html, discoveredTerms, selected);
                if (requestedTerm != null && !requestedTerm.isBlank()) {
                    // 与主请求保持一致：新版页面只有带 viweType=0 才会返回真实
                    // 课表内容；仅传学期参数仍会回到外层 iframe 壳。
                    String retryUrl = session.getJwglBase() + XSKB_PATH + "?viweType=0&xnxq01id="
                            + URLEncoder.encode(requestedTerm.trim(), StandardCharsets.UTF_8);
                    HttpResponse<byte[]> retry = session.getFollow(retryUrl, 6, headers);
                    String retryHtml = body(retry);
                    logFetch(retryUrl, retry, retryHtml, "selected-term");
                    if (parser.supports(retryHtml)) return new FetchResult(retryHtml, discoveredTerms, requestedTerm);
                    html = retryHtml;
                }
            }
            return new FetchResult(html, discoveredTerms, selected);
        } catch (Exception e) {
            throw new BusinessException("获取课表失败：" + JwErrorMessages.userDetail(
                    e, "学校课表系统暂时异常，请稍后重试"));
        }
    }

    private static String body(HttpResponse<byte[]> response) {
        return response == null || response.body() == null
                ? "" : new String(response.body(), StandardCharsets.UTF_8);
    }

    private static String selectedTerm(String html) {
        if (html == null) return null;
        Matcher matcher = SELECTED_TERM.matcher(html);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** 从新版课表查询父页提取完整学期列表；异步接口响应本身不包含这些选项。 */
    private static void mergeTermOptions(List<TermVO> target, String html) {
        if (html == null || html.isBlank()) return;
        try {
            Document doc = Jsoup.parse(html);
            for (Element option : doc.select("select[name=xnxq01id] option[value]")) {
                String value = option.attr("value").trim();
                if (value.isBlank()) continue;
                TermVO existing = target.stream().filter(t -> value.equals(t.getValue())).findFirst().orElse(null);
                if (existing == null) {
                    TermVO term = new TermVO();
                    term.setValue(value);
                    term.setLabel(option.text().replace('\u00a0', ' ').trim());
                    term.setCurrent(option.hasAttr("selected"));
                    target.add(term);
                } else if (option.hasAttr("selected")) {
                    existing.setCurrent(true);
                }
            }
        } catch (Exception ignored) {
            // 页面结构异常时仍保留其它抓取路径，调用方会按请求学期兜底。
        }
    }

    private static String selectedTermOrFirst(String html, List<TermVO> terms, String current) {
        if (current != null && !current.isBlank()) return current;
        String selected = selectedTerm(html);
        if (selected != null && !selected.isBlank()) return selected.trim();
        return terms.stream().findFirst().map(TermVO::getValue).orElse(null);
    }

    /** 合并父查询页学期元数据，并让请求学期成为异步响应的明确归属。 */
    private static void mergeTermMetadata(ScheduleVO vo, List<TermVO> discovered,
                                           String selected, String requested) {
        Map<String, TermVO> merged = new LinkedHashMap<>();
        if (vo.getTerms() != null) {
            for (TermVO term : vo.getTerms()) {
                if (term != null && term.getValue() != null && !term.getValue().isBlank()) {
                    merged.putIfAbsent(term.getValue().trim(), term);
                }
            }
        }
        if (discovered != null) {
            for (TermVO term : discovered) {
                if (term != null && term.getValue() != null && !term.getValue().isBlank()) {
                    merged.putIfAbsent(term.getValue().trim(), term);
                }
            }
        }
        String effective = requested != null && !requested.isBlank() ? requested.trim()
                : vo.getTerm() != null && !vo.getTerm().isBlank() ? vo.getTerm().trim()
                : selected;
        if (effective != null && !effective.isBlank()) {
            vo.setTerm(effective);
            merged.values().forEach(t -> t.setCurrent(effective.equals(t.getValue())));
        }
        vo.setTerms(new ArrayList<>(merged.values()));
    }

    /** 复现新版筛选页提交的所有下拉字段，而不是只提交学期。 */
    private static Map<String, String> selectedFormValues(String html) {
        Map<String, String> values = new LinkedHashMap<>();
        if (html == null || html.isBlank()) return values;
        try {
            Document doc = Jsoup.parse(html);
            for (org.jsoup.nodes.Element select : doc.select("select[name]")) {
                String name = select.attr("name").trim();
                if (name.isBlank() || values.containsKey(name)) continue;
                org.jsoup.nodes.Element option = select.selectFirst("option[selected]");
                if (option == null) option = select.selectFirst("option[value]");
                if (option != null) values.put(name, option.attr("value"));
            }
            // form.val('search') 还会序列化隐藏筛选字段；只提交 select 会让部分节点
            // 返回空分页。排除按钮/文件字段，保留隐藏与已勾选控件。
            for (org.jsoup.nodes.Element input : doc.select("input[name]")) {
                String name = input.attr("name").trim();
                String type = input.attr("type").trim().toLowerCase(java.util.Locale.ROOT);
                if (name.isBlank() || values.containsKey(name)
                        || type.equals("button") || type.equals("submit") || type.equals("reset")
                        || type.equals("file")) continue;
                if ((type.equals("checkbox") || type.equals("radio")) && !input.hasAttr("checked")) continue;
                values.put(name, input.attr("value"));
            }
        } catch (Exception ignored) {
            // 结构诊断失败不影响主链路；调用方仍会提交学期字段。
        }
        return values;
    }

    /** 仅记录 JSON 接口的键和数组规模，不记录课程名称、教师、地点等业务内容。 */
    private static Map<String, Object> apiShape(String body) {
        if (body == null || body.isBlank()) return Map.of("empty", true);
        try {
            Object value = com.alibaba.fastjson2.JSON.parse(body);
            if (!(value instanceof java.util.Map<?, ?> map)) return Map.of("type", value.getClass().getSimpleName());
            Map<String, Object> out = new LinkedHashMap<>();
            for (Object key : map.keySet()) {
                String k = String.valueOf(key);
                Object v = map.get(key);
                if (v instanceof java.util.Collection<?> c) out.put(k, "array(" + c.size() + ")");
                else if (v instanceof java.util.Map<?, ?> m) out.put(k, "object(" + m.size() + ")");
                else out.put(k, v == null ? "null" : v.getClass().getSimpleName());
            }
            return out;
        } catch (Exception ignored) {
            return Map.of("type", "text", "length", body.length());
        }
    }

    private static boolean isLoginPage(String html) {
        if (html == null) return false;
        String lower = html.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("name=\"loginform\"") || lower.contains("id=\"logindiv\"")
                || lower.contains("/jsxsd/xk/logintoxk") || lower.contains("欢迎登录教务系统")
                || lower.contains("/cas/login") && lower.contains("password");
    }

    /** 课表页由浏览器导航进入，带上同源来源与页面导航头，兼容新版 WebVPN 风控。 */
    private static Map<String, String> navigationHeaders(JwHttpSession session) {
        String base = session.getJwglBase();
        Map<String, String> headers = new LinkedHashMap<>();
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

    /** 只记录状态和结构特征，不记录页面正文、Cookie、账号或票据。 */
    private void logFetch(String url, HttpResponse<byte[]> response, String html, String phase) {
        logFetchStatus(url, response == null ? -1 : response.statusCode(), html, phase);
    }

    private void logFetchStatus(String url, int status, String html, String phase) {
        String path;
        try {
            java.net.URI uri = java.net.URI.create(url);
            path = uri.getPath();
        } catch (Exception ignored) {
            path = "<invalid>";
        }
        log.info("课表页面抓取 phase={} path={} status={} bytes={} parserSupported={} hasKbMarker={} selectedTerm={} shape={}",
                phase, path, status,
                html == null ? 0 : html.length(), parser.supports(html),
                html != null && html.toLowerCase(java.util.Locale.ROOT).contains("kbtable"),
                selectedTerm(html) != null, pageShape(html));
    }

    /** 仅输出页面结构特征，帮助定位源站改版，不记录正文、账号、Cookie 或票据。 */
    private static Map<String, Object> pageShape(String html) {
        if (html == null || html.isBlank()) return Map.of("empty", true);
        try {
            Document doc = Jsoup.parse(html);
            String lower = html.toLowerCase(java.util.Locale.ROOT);
            List<String> formActions = doc.select("form[action]").stream()
                    .map(e -> safePath(e.attr("abs:action").isBlank() ? e.attr("action") : e.attr("abs:action")))
                    .filter(s -> !s.isBlank()).distinct().limit(6).toList();
            List<String> frameSources = doc.select("iframe[src], frame[src]").stream()
                    .map(e -> safePath(e.attr("abs:src").isBlank() ? e.attr("src") : e.attr("abs:src")))
                    .filter(s -> !s.isBlank()).distinct().limit(6).toList();
            List<String> frameDetails = doc.select("iframe, frame").stream().limit(6)
                    .map(e -> e.tagName() + "#" + e.id() + " src=" + safePath(e.attr("src"))
                            + " name=" + e.attr("name") + " class=" + e.attr("class"))
                    .toList();
            List<String> selectNames = doc.select("select[name]").stream()
                    .map(e -> e.attr("name")).filter(s -> !s.isBlank()).distinct().limit(8).toList();
            List<String> selectValues = doc.select("select[name]").stream()
                    .limit(8)
                    .map(e -> {
                        Element selected = e.selectFirst("option[selected]");
                        if (selected == null) selected = e.selectFirst("option[value]");
                        return e.attr("name") + "=" + (selected == null ? "" : selected.attr("value"));
                    }).toList();
            List<String> linkPaths = doc.select("a[href]").stream()
                    .map(e -> safePath(e.attr("abs:href").isBlank() ? e.attr("href") : e.attr("abs:href")))
                    .filter(s -> !s.isBlank()).distinct().limit(12).toList();
            List<String> scriptPaths = doc.select("script[src]").stream()
                    .map(e -> safePath(e.attr("abs:src").isBlank() ? e.attr("src") : e.attr("abs:src")))
                    .filter(s -> !s.isBlank()).distinct().limit(12).toList();
            List<String> inputNames = doc.select("input[name]").stream()
                    .map(e -> e.attr("name")).filter(s -> !s.isBlank()).distinct().limit(12).toList();
            List<String> inputValues = doc.select("input[name]").stream().limit(12)
                    .map(e -> e.attr("name") + "=" + e.attr("value")).toList();
            List<String> rootChildren = doc.body() == null ? List.of() : doc.body().children().stream()
                    .map(e -> e.tagName() + (e.id().isBlank() ? "" : "#" + e.id()))
                    .limit(12).toList();
            List<String> dataRoutes = doc.select("[data-src]").stream()
                    .map(e -> e.attr("data-src") + "|" + e.text().replaceAll("\\s+", " ").trim())
                    .filter(s -> !s.startsWith("|"))
                    .distinct().limit(20).toList();
            List<String> inlineRoutes = java.util.regex.Pattern.compile("['\\\"](/jsxsd/[^'\\\"\\s?#]+)")
                    .matcher(html).results().map(m -> m.group(1)).distinct().limit(20).toList();
            List<String> ajaxHints = doc.select("script:not([src])").stream()
                    .map(Element::data)
                    .filter(s -> s.contains("_ifr") || s.contains("kbxx_"))
                    .map(s -> s.replaceAll("\\s+", " ").trim())
                    .map(s -> s.substring(0, Math.min(300, s.length())))
                    .distinct().limit(4).toList();
            Map<String, Object> shape = new LinkedHashMap<>();
            String title = doc.title() == null ? "" : doc.title().replaceAll("\\s+", " ").trim();
            shape.put("title", title.substring(0, Math.min(40, title.length())));
            shape.put("forms", formActions);
            shape.put("frames", frameSources);
            shape.put("frameDetails", frameDetails);
            shape.put("selects", selectNames);
            shape.put("selectValues", selectValues);
            shape.put("links", linkPaths);
            shape.put("scripts", scriptPaths);
            shape.put("inputs", inputNames);
            shape.put("inputValues", inputValues);
            shape.put("root", rootChildren);
            shape.put("ajaxHints", ajaxHints);
            shape.put("dataRoutes", dataRoutes);
            shape.put("inlineRoutes", inlineRoutes);
            shape.put("tables", doc.select("table").size());
            shape.put("divs", doc.select("div").size());
            shape.put("markers", Map.of(
                    "login", lower.contains("loginform") || lower.contains("logindiv"),
                    "logout", lower.contains("退出系统") || lower.contains("注销"),
                    "course", lower.contains("课程") || lower.contains("课表"),
                    "error", lower.contains("error") || lower.contains("错误") || lower.contains("异常")));
            return shape;
        } catch (Exception ignored) {
            return Map.of("parseError", true);
        }
    }

    private static String safePath(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            java.net.URI uri = java.net.URI.create(value);
            return uri.getPath() == null ? "" : uri.getPath();
        } catch (Exception ignored) {
            int query = value.indexOf('?');
            return query < 0 ? value : value.substring(0, query);
        }
    }
}
