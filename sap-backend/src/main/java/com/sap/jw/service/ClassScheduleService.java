package com.sap.jw.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sap.common.BusinessException;
import com.sap.entity.User;
import com.sap.mapper.UserMapper;
import com.sap.jw.client.JwHttpSession;
import com.sap.jw.client.MfaRequiredException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 新版强智「课表列表查询」采集器及班级课表查询服务。
 *
 * <p>新版页面不是 HTML 表格，而是四个 *_ifr JSON 接口。这里按浏览器实际请求顺序
 * 串行读取四个数据集，使用 pageNum/pageSize 分页，并将每个来源单独保存；班级接口本身
 * 已包含教师、教室、周次和节次，最终展示以它为主，再用其它三张表补齐字段。</p>
 */
@Service
public class ClassScheduleService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ClassScheduleService.class);
    private static final int PAGE_SIZE = 50_000;
    private static final long REQUEST_INTERVAL_MS = 1_200L;
    private static final String CLASS_PAGE = "/jsxsd/kbcx/kbxx_xzb";
    private static final String[][] TARGETS = {
            {"class", "/jsxsd/kbcx/kbxx_xzb_ifr"},
            {"teacher", "/jsxsd/kbcx/kbxx_teacher_ifr"},
            {"room", "/jsxsd/kbcx/kbxx_classroom_ifr"},
            {"course", "/jsxsd/kbcx/kbxx_kc_ifr"},
    };

    private final JdbcTemplate jdbc;
    private final JwSessionManager sessionManager;
    private final JwCredentialService credentialService;
    private final UserMapper userMapper;
    private final JwCalendarService calendarService;
    private final TransactionTemplate transactionTemplate;
    private final ReentrantLock pullLock = new ReentrantLock();
    private volatile long lastRequestAt;

    public ClassScheduleService(JdbcTemplate jdbc,
                                JwSessionManager sessionManager,
                                JwCredentialService credentialService,
                                UserMapper userMapper,
                                JwCalendarService calendarService,
                                PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.sessionManager = sessionManager;
        this.credentialService = credentialService;
        this.userMapper = userMapper;
        this.calendarService = calendarService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 版本升级时自动建表，不要求人工执行 SQL；已有数据完全保留。 */
    @PostConstruct
    public void ensureSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS jw_class_schedule_term (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, term_value VARCHAR(32) NOT NULL UNIQUE, " +
                "term_label VARCHAR(80), semester_start_date VARCHAR(16), row_count INT DEFAULT 0, " +
                "class_count INT DEFAULT 0, teacher_count INT DEFAULT 0, room_count INT DEFAULT 0, course_count INT DEFAULT 0, " +
                "last_collected_at DATETIME, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
        // 旧版本可能只有 row_count；增量升级时补齐四来源统计，已有课表数据不受影响。
        ensureColumn("jw_class_schedule_term", "class_count", "INT DEFAULT 0");
        ensureColumn("jw_class_schedule_term", "teacher_count", "INT DEFAULT 0");
        ensureColumn("jw_class_schedule_term", "room_count", "INT DEFAULT 0");
        ensureColumn("jw_class_schedule_term", "course_count", "INT DEFAULT 0");
        String common = "id BIGINT PRIMARY KEY AUTO_INCREMENT, term_value VARCHAR(32) NOT NULL, " +
                "source_key VARCHAR(96) NOT NULL, external_id VARCHAR(96), " +
                "course_no VARCHAR(64), course_name VARCHAR(255), week_range VARCHAR(128), " +
                "weekday VARCHAR(32), section VARCHAR(32), room_name VARCHAR(255), teacher_name VARCHAR(1024), " +
                // 教师/课程来源会把多个班级拼接在 bj 字段中，长度可能明显超过普通班名。
                // 查询索引在 ensureClassScheduleIndexes 中按数据库分别使用前缀/完整索引。
                "college VARCHAR(255), grade VARCHAR(32), major VARCHAR(255), class_name VARCHAR(1024), " +
                "course_dept VARCHAR(255), category VARCHAR(128), course_attr VARCHAR(128), course_nature VARCHAR(128), " +
                "exam_method VARCHAR(128), group_name VARCHAR(255), hours DECIMAL(10,2), credit DECIMAL(10,2), " +
                "raw_json LONGTEXT, updated_at DATETIME DEFAULT CURRENT_TIMESTAMP";
        for (String table : List.of("jw_class_schedule_class", "jw_class_schedule_teacher",
                "jw_class_schedule_room", "jw_class_schedule_course")) {
            // MySQL 的索引名按表隔离，而 H2 要求 schema 内唯一；使用表名派生索引名，
            // 这样本地 smoke test 与线上 MySQL 的建表行为一致，升级时也不会撞名。
            jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " (" + common + ", " +
                    "INDEX idx_" + table + "_term (term_value), " +
                    "INDEX idx_" + table + "_join (term_value, external_id, weekday, section, week_range), " +
                    "UNIQUE (term_value, source_key))");
            // class_name 已放宽到 1024；迁移旧表时先移除旧的整列索引，避免
            // MySQL 在 MODIFY COLUMN 阶段因索引长度超限而拒绝升级。
            ensureClassNameWidth(table);
            // 为班级筛选补充索引。MySQL 使用 191 字符前缀以控制 utf8mb3/utf8mb4
            // 的索引字节数，查询仍会用完整 class_name 条件做最终精确过滤；H2 使用完整列。
            ensureClassScheduleIndexes(table);
        }
        jdbc.execute("CREATE TABLE IF NOT EXISTS jw_class_schedule_pull_log (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT, batch_id VARCHAR(64) NOT NULL, term_value VARCHAR(32), " +
                "source_type VARCHAR(20), trigger_type VARCHAR(20) NOT NULL, actor_id BIGINT, actor_name VARCHAR(80), " +
                "status VARCHAR(20) NOT NULL, message VARCHAR(1000), row_count INT DEFAULT 0, " +
                "started_at DATETIME DEFAULT CURRENT_TIMESTAMP, finished_at DATETIME, " +
                "INDEX idx_pull_batch (batch_id), INDEX idx_pull_time (started_at))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS jw_class_schedule_pull_schedule (" +
                "id TINYINT PRIMARY KEY, enabled TINYINT NOT NULL DEFAULT 0, schedule_type VARCHAR(16) NOT NULL DEFAULT 'DAILY', " +
                "hour_num TINYINT NOT NULL DEFAULT 0, minute_num TINYINT NOT NULL DEFAULT 0, " +
                "day_of_week TINYINT, day_of_month TINYINT, run_at DATETIME, owner_user_id BIGINT, " +
                "jw_account VARCHAR(32), jw_password_enc VARCHAR(512), created_by BIGINT, updated_by BIGINT, last_run_key VARCHAR(80), " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)");
        ensureColumn("jw_class_schedule_pull_schedule", "last_run_key", "VARCHAR(80)");
        ensureColumn("jw_class_schedule_pull_schedule", "jw_password_enc", "VARCHAR(512)");
        try {
            jdbc.update("INSERT INTO jw_class_schedule_pull_schedule (id) SELECT 1 WHERE NOT EXISTS " +
                    "(SELECT 1 FROM jw_class_schedule_pull_schedule WHERE id=1)");
        } catch (Exception ignored) {
            // MySQL 与 H2 对 INSERT ... SELECT 的兼容性不同，下面的查询会再次兜底。
            try {
                if (jdbc.queryForObject("SELECT COUNT(*) FROM jw_class_schedule_pull_schedule", Integer.class) == 0) {
                    jdbc.update("INSERT INTO jw_class_schedule_pull_schedule (id) VALUES (1)");
                }
            } catch (Exception ignoredAgain) { log.warn("班级课表定时配置初始化失败", ignoredAgain); }
        }
    }

    /** 手动或定时完整采集。term 为空时采集页面列出的全部学期；指定 term 时只采集该学期。 */
    public Map<String, Object> pull(Long actorId, String account, String term,
                                    String triggerType, String actorName) {
        return pullAs(actorId, account, term, triggerType, actorId, actorName);
    }

    /**
     * 使用 credentialUserId 名下的教务凭据采集，同时把 actorId 作为实际触发人写入日志。
     * 管理端可以由维护者触发一个已授权负责人的教务采集，不需要把密码复制到管理端。
     */
    public Map<String, Object> pullAs(Long credentialUserId, String account, String term,
                                      String triggerType, Long actorId, String actorName) {
        return pullAs(credentialUserId, account, term, triggerType, actorId, actorName, null);
    }

    /** 使用一次性明文密码建立临时会话；密码不会保存到会员凭据或会话缓存。 */
    public Map<String, Object> pullWithCredentials(Long actorId, String account, String rawPassword,
                                                    String term, String triggerType, String actorName) {
        if (account == null || account.isBlank()) throw new BusinessException(400, "请输入教务账号");
        if (rawPassword == null || rawPassword.isBlank()) throw new BusinessException(400, "请输入教务密码");
        return pullAs(actorId, account.trim(), term, triggerType, actorId, actorName, rawPassword);
    }

    /** 自动任务读取数据库密文并在内存中解密，随后走一次性会话。 */
    public Map<String, Object> pullScheduled(Long credentialUserId, String account,
                                              String term, Long actorId, String actorName) {
        String encrypted = null;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT jw_password_enc FROM jw_class_schedule_pull_schedule WHERE id=1");
            if (!rows.isEmpty()) encrypted = text(rows.get(0), "jw_password_enc");
        } catch (Exception e) {
            log.warn("读取班级课表自动采集凭据失败", e);
        }
        String password = credentialService.decryptEncryptedPassword(encrypted);
        return pullAs(credentialUserId, account, term, "AUTO", actorId, actorName, password);
    }

    private Map<String, Object> pullAs(Long credentialUserId, String account, String term,
                                       String triggerType, Long actorId, String actorName,
                                       String rawPassword) {
        if (!pullLock.tryLock()) throw new BusinessException(409, "已有班级课表采集任务正在执行");
        String batchId = Long.toString(System.currentTimeMillis(), 36) + "-" +
                Integer.toHexString(System.identityHashCode(this));
        String displayActor = actorName;
        try {
            // 先解析触发人，再解析教务账号；即使账号未绑定、请求被拒绝，失败日志也要保留真实操作者，
            // 不能因为前置校验失败就退化成“系统”。
            User actor = actorId == null ? null : userMapper.selectById(actorId);
            displayActor = actorName == null || actorName.isBlank()
                    ? (actor == null ? "系统" : actor.getName() != null ? actor.getName() : actor.getStudentId())
                    : actorName;
            String cleanAccount = account == null || account.isBlank()
                    ? credentialService.defaultAccount(credentialUserId) : account.trim();
            if (cleanAccount == null || cleanAccount.isBlank()) throw new BusinessException("当前账号尚未绑定教务账号，无法采集");
            JwHttpSession session = rawPassword == null || rawPassword.isBlank()
                    ? sessionManager.getSession(credentialUserId, cleanAccount)
                    : sessionManager.loginEphemeral(cleanAccount, rawPassword);
            List<TermOption> terms = discoverTerms(session);
            if (term != null && !term.isBlank()) {
                String wanted = term.trim();
                terms = terms.stream().filter(t -> wanted.equals(t.value)).toList();
                if (terms.isEmpty()) terms = List.of(new TermOption(wanted, wanted));
            }
            if (terms.isEmpty()) throw new BusinessException("教务系统未返回可用学期");

            Map<String, Integer> totals = new LinkedHashMap<>();
            for (TermOption selected : terms) {
                appendLog(batchId, selected.value, null, triggerType, actorId, displayActor,
                        "RUNNING", "开始采集学期 " + selected.value, 0, null);
                Map<String, Integer> counts = collectOneTerm(session, selected, batchId,
                        triggerType, actorId, displayActor);
                counts.forEach((k, v) -> totals.merge(k, v, Integer::sum));
                upsertTerm(selected, counts, session);
            }
            appendLog(batchId, term, "all", triggerType, actorId, displayActor, "SUCCESS",
                    "采集完成：" + totals, totals.values().stream().mapToInt(Integer::intValue).sum(), LocalDateTime.now());
            return Map.of("batchId", batchId, "terms", terms.size(), "counts", totals);
        } catch (MfaRequiredException e) {
            appendLog(batchId, term, "all", triggerType, actorId, displayActor,
                    "PENDING", "等待短信二次验证", 0, LocalDateTime.now());
            throw e;
        } catch (RuntimeException e) {
            appendLog(batchId, term, "all", triggerType, actorId, displayActor, "FAILED", safe(e.getMessage()), 0, LocalDateTime.now());
            throw e;
        } catch (Exception e) {
            appendLog(batchId, term, "all", triggerType, actorId, displayActor, "FAILED", safe(e.getMessage()), 0, LocalDateTime.now());
            throw new BusinessException("班级课表采集失败：" + safe(e.getMessage()));
        } finally {
            pullLock.unlock();
        }
    }

    private Map<String, Integer> collectOneTerm(JwHttpSession session, TermOption term,
                                                 String batchId, String triggerType,
                                                 Long actorId, String actorName) throws Exception {
        Map<String, Map<String, String>> forms = new LinkedHashMap<>();
        for (String[] target : TARGETS) {
            throttle();
            String pageUrl = session.getJwglBase() + pageFor(target[0]);
            var response = session.getFollow(pageUrl, 6);
            String html = response == null || response.body() == null ? ""
                    : new String(response.body(), StandardCharsets.UTF_8);
            forms.put(target[0], selectedValues(html));
        }
        // 先把四个来源全部读完，再替换数据库中的旧快照。这样某个来源页临时 5xx、登录超时
        // 或分页不完整时，不会先删掉前面已经成功读取的来源，避免出现“半套班级课表”。
        Map<String, FetchResult> fetchedSources = new LinkedHashMap<>();
        for (String[] target : TARGETS) {
            String source = target[0];
            appendLog(batchId, term.value, source, triggerType, actorId, actorName,
                    "RUNNING", "请求新版 JSON 数据", 0, null);
            try {
                FetchResult fetched = fetchAll(session, target[1], pageFor(source),
                        forms.getOrDefault(source, Map.of()), term.value);
                fetchedSources.put(source, fetched);
                appendLog(batchId, term.value, source, triggerType, actorId, actorName,
                        "RUNNING", "读取 " + fetched.rows.size() + " 条，待写入（分页 " + fetched.pages + " 页" +
                                (fetched.reportedCount >= 0 ? "，源站总数 " + fetched.reportedCount : "") + "）",
                        fetched.rows.size(), null);
            } catch (Exception e) {
                appendLog(batchId, term.value, source, triggerType, actorId, actorName,
                        "FAILED", safe(e.getMessage()), 0, LocalDateTime.now());
                throw e;
            }
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        try {
            // 四个来源必须作为一个快照替换：网络阶段全部成功后才进入事务，任一张表
            // 写入失败都会回滚其它表，避免管理端看到“班级表已更新、教师/教室表仍是旧数据”的半套快照。
            transactionTemplate.executeWithoutResult(status -> {
                for (String[] target : TARGETS) {
                    String source = target[0];
                    FetchResult fetched = fetchedSources.get(source);
                    replaceRows(term.value, source, fetched.rows);
                    counts.put(source, fetched.rows.size());
                }
            });
        } catch (Exception e) {
            for (String[] target : TARGETS) {
                appendLog(batchId, term.value, target[0], triggerType, actorId, actorName,
                        "FAILED", "写入失败（四表事务已回滚）：" + safe(e.getMessage()), 0, LocalDateTime.now());
            }
            throw e;
        }
        for (String[] target : TARGETS) {
            String source = target[0];
            FetchResult fetched = fetchedSources.get(source);
            appendLog(batchId, term.value, source, triggerType, actorId, actorName,
                    "SUCCESS", "写入 " + fetched.rows.size() + " 条（分页 " + fetched.pages + " 页" +
                            (fetched.reportedCount >= 0 ? "，源站总数 " + fetched.reportedCount : "") + "）",
                    fetched.rows.size(), LocalDateTime.now());
        }
        return counts;
    }

    private FetchResult fetchAll(JwHttpSession session, String path, String pagePath,
                                 Map<String, String> form, String term) throws Exception {
        // 服务器可能忽略过大的 pageSize（实测会回退到 1000），所以不能用
        // data.size() < PAGE_SIZE 作为唯一结束条件；优先依据 count 继续翻页。
        Map<String, JSONObject> unique = new LinkedHashMap<>();
        int page = 1;
        int reportedCount = -1;
        int fetchedCount = 0;
        while (page <= 1000) {
            throttle();
            Map<String, String> params = new LinkedHashMap<>(form);
            params.put("xnxq01id", term);
            params.put("pageNum", String.valueOf(page));
            params.put("pageSize", String.valueOf(PAGE_SIZE));
            String url = session.getJwglBase() + path + "?" + encode(params);
            var resp = session.getFollow(url, 4, Map.of(
                    "Accept", "application/json, text/plain, */*",
                    "X-Requested-With", "XMLHttpRequest",
                    "Referer", session.getJwglBase() + pagePath,
                    "Sec-Fetch-Dest", "empty",
                    "Sec-Fetch-Mode", "cors"));
            String body = resp == null || resp.body() == null ? ""
                    : new String(resp.body(), StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(body);
            if (root == null || root.getInteger("code") == null || root.getInteger("code") != 0) {
                throw new IllegalStateException("新版课表接口返回异常: " + path);
            }
            JSONArray data = root.getJSONArray("data");
            int count = root.getIntValue("count", -1);
            if (count >= 0) reportedCount = count;
            int before = unique.size();
            if (data != null) for (Object item : data) if (item instanceof JSONObject obj) {
                fetchedCount++;
                unique.putIfAbsent(rowKey(term, path, obj), obj);
            }
            if (data == null) {
                if (reportedCount > 0) {
                    throw new IllegalStateException("新版课表接口缺少数据字段: " + path);
                }
                break;
            }
            if (data.isEmpty()) {
                // count 是源站原始行数，允许少量完全重复行被 source_key 去重；
                // 但如果空页出现在 count 尚未读完之前，说明分页被截断，不能静默写入半套数据。
                if (reportedCount >= 0 && fetchedCount < reportedCount) {
                    throw new IllegalStateException("新版课表接口分页数据不完整: " + path +
                            "（已读取 " + fetchedCount + "/" + reportedCount + "）");
                }
                break;
            }
            // 用原始行数而不是去重后的 unique.size() 对齐 count：班级页可能有少量
            // 完全重复行，教师页则可能有多位教师但共享同一教学安排。
            if (reportedCount >= 0 && fetchedCount >= reportedCount) break;
            // 没有总数时也继续请求下一页，直到收到明确空页。请求的 pageSize 可能被
            // 服务端静默压到 1000，若此处按“短于 50000”结束会把后续数据截掉；多请求
            // 一页的代价很小，而重复页保护会阻止 pageNum 被忽略时无限循环。
            // 源站返回全量数据时，count 可能包含少量重复行；再请求一页确认没有更多。
            // 如果下一页仍返回非空但完全重复，说明 pageNum 被忽略或排序不稳定，
            // 宁可失败也不写入半套数据；空页才是明确的分页结束。
            if (unique.size() == before) {
                throw new IllegalStateException("新版课表接口分页参数未生效或结果重复: " + path);
            }
            page++;
        }
        if (page > 1000) throw new IllegalStateException("新版课表接口分页超过安全上限: " + path);
        return new FetchResult(new ArrayList<>(unique.values()), Math.max(1, page), reportedCount);
    }

    private List<TermOption> discoverTerms(JwHttpSession session) throws Exception {
        throttle();
        var response = session.getFollow(session.getJwglBase() + CLASS_PAGE, 6);
        if (response == null || response.body() == null) throw new IllegalStateException("新版班级课表页面无响应");
        String page = new String(response.body(), StandardCharsets.UTF_8);
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(page);
        List<TermOption> result = new ArrayList<>();
        for (org.jsoup.nodes.Element option : doc.select("select[name=xnxq01id] option[value]")) {
            String value = option.attr("value").trim();
            if (!value.isBlank() && result.stream().noneMatch(t -> t.value.equals(value))) {
                result.add(new TermOption(value, option.text().replace('\u00a0', ' ').trim()));
            }
        }
        return result;
    }

    private static String pageFor(String source) {
        return switch (source) {
            case "class" -> "/jsxsd/kbcx/kbxx_xzb";
            case "teacher" -> "/jsxsd/kbcx/kbxx_teacher";
            case "room" -> "/jsxsd/kbcx/kbxx_classroom";
            default -> "/jsxsd/kbcx/kbxx_kc";
        };
    }

    private static Map<String, String> selectedValues(String html) {
        Map<String, String> values = new LinkedHashMap<>();
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html == null ? "" : html);
        for (org.jsoup.nodes.Element select : doc.select("select[name]")) {
            org.jsoup.nodes.Element option = select.selectFirst("option[selected]");
            if (option == null) option = select.selectFirst("option[value]");
            if (option != null) values.put(select.attr("name"), option.attr("value"));
        }
        for (org.jsoup.nodes.Element input : doc.select("input[name]")) {
            String type = input.attr("type").toLowerCase();
            if (Set.of("button", "submit", "reset", "file").contains(type)) continue;
            if (("checkbox".equals(type) || "radio".equals(type)) && !input.hasAttr("checked")) continue;
            values.putIfAbsent(input.attr("name"), input.attr("value"));
        }
        return values;
    }

    private void replaceRows(String term, String source, List<JSONObject> rows) {
        String table = "jw_class_schedule_" + source;
        jdbc.update("DELETE FROM " + table + " WHERE term_value=?", term);
        if (rows.isEmpty()) return;
        String sql = "INSERT INTO " + table +
                " (term_value,source_key,external_id,course_no,course_name,week_range,weekday,section,room_name,teacher_name," +
                "college,grade,major,class_name,course_dept,category,course_attr,course_nature,exam_method,group_name,hours,credit,raw_json)" +
                " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        jdbc.batchUpdate(sql, rows, 500, (ps, row) -> {
            ps.setString(1, term);
            ps.setString(2, rowKey(term, source, row));
            ps.setString(3, text(row, "jx0404id"));
            ps.setString(4, text(row, "kch"));
            ps.setString(5, text(row, "kcmc"));
            ps.setString(6, text(row, "kkzc"));
            ps.setString(7, text(row, "zzdweek"));
            ps.setString(8, text(row, "jc"));
            ps.setString(9, text(row, "jsmc"));
            ps.setString(10, first(text(row, "jsxm"), text(row, "xm")));
            ps.setString(11, first(text(row, "dwmc"), text(row, "kkdw")));
            ps.setString(12, text(row, "ksnd"));
            ps.setString(13, text(row, "zymc"));
            ps.setString(14, first(text(row, "bj"), text(row, "ktmc")));
            ps.setString(15, text(row, "kkdw"));
            ps.setString(16, first(text(row, "zzdkclb"), text(row, "kcdl")));
            ps.setString(17, text(row, "zzdkctype"));
            ps.setString(18, text(row, "zzdkcxz"));
            ps.setString(19, text(row, "zzdksfs"));
            ps.setString(20, text(row, "fzmc"));
            ps.setBigDecimal(21, decimal(row, "zxs", "zhxs"));
            ps.setBigDecimal(22, decimal(row, "xf"));
            ps.setString(23, row.toJSONString());
        });
    }

    private void upsertTerm(TermOption term, Map<String, Integer> counts, JwHttpSession session) {
        String start = null;
        try {
            // 教学日历也是教务请求，和四个 *_ifr 接口共用同一节流器，避免连续命中源站。
            throttle();
            start = calendarService.getSemesterStart(session, term.value);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("教学日历请求被中断 term={}", term.value);
        } catch (Exception ignored) { }
        int classCount = counts.getOrDefault("class", 0);
        int teacherCount = counts.getOrDefault("teacher", 0);
        int roomCount = counts.getOrDefault("room", 0);
        int courseCount = counts.getOrDefault("course", 0);
        int affected = jdbc.update("UPDATE jw_class_schedule_term SET term_label=?,semester_start_date=?,row_count=?,class_count=?,teacher_count=?,room_count=?,course_count=?,last_collected_at=?,updated_at=? WHERE term_value=?",
                term.label, start, classCount, classCount, teacherCount, roomCount, courseCount,
                LocalDateTime.now(), LocalDateTime.now(), term.value);
        if (affected == 0) jdbc.update("INSERT INTO jw_class_schedule_term (term_value,term_label,semester_start_date,row_count,class_count,teacher_count,room_count,course_count,last_collected_at) VALUES (?,?,?,?,?,?,?,?,?)",
                term.value, term.label, start, classCount, classCount, teacherCount, roomCount, courseCount, LocalDateTime.now());
    }

    private void throttle() throws InterruptedException {
        long wait = REQUEST_INTERVAL_MS - (System.currentTimeMillis() - lastRequestAt);
        if (wait > 0) TimeUnit.MILLISECONDS.sleep(wait);
        lastRequestAt = System.currentTimeMillis();
    }

    private void appendLog(String batchId, String term, String source, String trigger, Long actorId,
                           String actorName, String status, String message, int rows, LocalDateTime finished) {
        try {
            jdbc.update("INSERT INTO jw_class_schedule_pull_log (batch_id,term_value,source_type,trigger_type,actor_id,actor_name,status,message,row_count,finished_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    batchId, term, source, trigger == null ? "MANUAL" : trigger, actorId,
                    actorName == null ? "系统" : actorName, status, safe(message), rows, finished);
        } catch (Exception e) { log.warn("班级课表采集日志写入失败", e); }
    }

    public List<Map<String, Object>> terms() {
        return jdbc.queryForList("SELECT term_value AS value, term_label AS label, semester_start_date AS semesterStartDate, " +
                "row_count AS rowCount, class_count AS classCount, teacher_count AS teacherCount, " +
                "room_count AS roomCount, course_count AS courseCount, last_collected_at AS lastCollectedAt " +
                "FROM jw_class_schedule_term ORDER BY term_value DESC");
    }

    public List<Map<String, Object>> classes(String term, String college, String major) {
        return classes(term, college, major, null);
    }

    public List<Map<String, Object>> classes(String term, String college, String major, String grade) {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT term_value AS term, college, grade, major, class_name AS className FROM jw_class_schedule_class WHERE term_value=? AND class_name IS NOT NULL AND class_name<>''");
        List<Object> args = new ArrayList<>(); args.add(term);
        if (college != null && !college.isBlank()) { sql.append(" AND college=?"); args.add(college.trim()); }
        if (grade != null && !grade.isBlank()) { sql.append(" AND grade=?"); args.add(grade.trim()); }
        if (major != null && !major.isBlank()) { sql.append(" AND major=?"); args.add(major.trim()); }
        sql.append(" ORDER BY college,grade,major,class_name");
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    /** 以班级表为主、四表按 jx0404id + 时间维度补充，返回 App 现有 ScheduleData 兼容形状。 */
    public Map<String, Object> schedule(String term, String college, String major, String className) {
        return schedule(term, college, major, null, className);
    }

    public Map<String, Object> schedule(String term, String college, String major, String grade, String className) {
        // 班级页已经包含大部分字段。补全字段使用按关联键命中的相关子查询，
        // 让数据库先通过班级复合索引筛出目标班级，再按四表 join 索引查找，
        // 避免每次切换班级都物化并扫描三张全量聚合临时表。
        String key = "term_value=c.term_value AND external_id=c.external_id AND weekday=c.weekday " +
                "AND section=c.section AND week_range=c.week_range";
        String sql = "SELECT " +
                "COALESCE(NULLIF(c.course_name,''),(SELECT MAX(NULLIF(x.course_name,'')) FROM jw_class_schedule_teacher x WHERE " + key + ")," +
                "(SELECT MAX(NULLIF(x.course_name,'')) FROM jw_class_schedule_course x WHERE " + key + ")) AS course_name, " +
                "COALESCE(NULLIF(c.teacher_name,''),(SELECT MAX(NULLIF(x.teacher_name,'')) FROM jw_class_schedule_teacher x WHERE " + key + ")," +
                "(SELECT MAX(NULLIF(x.teacher_name,'')) FROM jw_class_schedule_course x WHERE " + key + ")) AS teacher_name, " +
                "COALESCE(NULLIF(c.room_name,''),(SELECT MAX(NULLIF(x.room_name,'')) FROM jw_class_schedule_room x WHERE " + key + ")) AS room_name, " +
                "c.weekday,c.section,COALESCE(NULLIF(c.week_range,'')," +
                "(SELECT MAX(NULLIF(x.week_range,'')) FROM jw_class_schedule_course x WHERE " + key + ")) AS week_range," +
                "c.course_no,c.raw_json FROM jw_class_schedule_class c " +
                "WHERE c.term_value=? AND c.college=? AND c.major=? " +
                (grade == null || grade.isBlank() ? "" : "AND c.grade=? ") +
                "AND c.class_name=? ORDER BY c.id";
        List<Object> scheduleArgs = new ArrayList<>();
        scheduleArgs.add(term); scheduleArgs.add(college); scheduleArgs.add(major);
        if (grade != null && !grade.isBlank()) scheduleArgs.add(grade.trim());
        scheduleArgs.add(className);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, scheduleArgs.toArray());
        List<Map<String, Object>> courses = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String dayName = value(row, "weekday");
            String section = value(row, "section");
            Map<String, Object> course = new LinkedHashMap<>();
            course.put("day", dayOf(dayName)); course.put("dayName", dayName == null ? "" : dayName);
            course.put("section", section == null ? "" : section); course.put("sectionIndex", sectionIndex(section));
            course.put("name", value(row, "course_name")); course.put("teacher", value(row, "teacher_name"));
            course.put("weeks", value(row, "week_range")); course.put("room", value(row, "room_name"));
            course.put("type", ""); courses.add(course);
        }
        String start = null;
        List<Map<String, Object>> termRows = jdbc.queryForList("SELECT semester_start_date FROM jw_class_schedule_term WHERE term_value=?", term);
        if (!termRows.isEmpty()) start = value(termRows.get(0), "semester_start_date");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("term", term); result.put("terms", terms());
        result.put("weekdays", List.of("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日"));
        result.put("courses", courses); result.put("remarks", List.of()); result.put("semesterStartDate", start);
        return result;
    }

    public List<Map<String, Object>> logs(int limit) {
        return jdbc.queryForList("SELECT id,batch_id AS batchId,term_value AS term,source_type AS sourceType,trigger_type AS triggerType,actor_id AS actorId,actor_name AS actorName,status,message,row_count AS rowCount,started_at AS startedAt,finished_at AS finishedAt FROM jw_class_schedule_pull_log ORDER BY id DESC LIMIT ?", Math.min(Math.max(limit, 1), 200));
    }

    public Map<String, Object> scheduleConfig() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,enabled,schedule_type AS scheduleType,hour_num AS hour,minute_num AS minute,day_of_week AS dayOfWeek,day_of_month AS dayOfMonth,run_at AS runAt,owner_user_id AS ownerUserId,jw_account AS jwAccount,CASE WHEN jw_password_enc IS NOT NULL AND jw_password_enc<>'' THEN 1 ELSE 0 END AS hasJwPassword,created_by AS createdBy,updated_by AS updatedBy,last_run_key AS lastRunKey,updated_at AS updatedAt FROM jw_class_schedule_pull_schedule WHERE id=1");
        return rows.isEmpty() ? Map.of("id", 1, "enabled", false, "scheduleType", "DAILY", "hour", 0, "minute", 0) : rows.get(0);
    }

    public void updateScheduleConfig(Map<String, Object> body, Long operatorId) {
        String type = String.valueOf(body.getOrDefault("scheduleType", "DAILY")).toUpperCase();
        if (!Set.of("DAILY", "WEEKLY", "MONTHLY", "ONCE").contains(type)) throw new BusinessException(400, "不支持的定时类型");
        boolean enabled = truthy(body.get("enabled"));
        Integer hour = number(body.get("hour"), 0), minute = number(body.get("minute"), 0);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) throw new BusinessException(400, "时间不合法");
        LocalDateTime runAt = parseDateTime(body.get("runAt"));
        Integer dayOfWeek = number(body.get("dayOfWeek"), null);
        Integer dayOfMonth = number(body.get("dayOfMonth"), null);
        Long ownerUserId = longNumber(body.get("ownerUserId"), null);
        String configuredAccount = text(body, "jwAccount");
        String submittedPassword = text(body, "jwPassword");
        boolean clearPassword = truthy(body.get("clearJwPassword"));
        Map<String, Object> existing = jdbc.queryForList(
                "SELECT owner_user_id,jw_account,jw_password_enc FROM jw_class_schedule_pull_schedule WHERE id=1")
                .stream().findFirst().orElse(Map.of());
        Long previousOwner = longNumber(existing.get("owner_user_id"), null);
        String previousAccount = text(existing, "jw_account");
        String previousEncrypted = text(existing, "jw_password_enc");
        String encryptedPassword = submittedPassword == null || submittedPassword.isBlank()
                ? (clearPassword ? null : previousEncrypted)
                : credentialService.encryptPassword(submittedPassword);
        String effectiveAccount = configuredAccount == null || configuredAccount.isBlank()
                ? (ownerUserId == null ? null : credentialService.defaultAccount(ownerUserId))
                : configuredAccount.trim();
        boolean accountChanged = (previousAccount != null && !previousAccount.isBlank()
                && effectiveAccount != null && !effectiveAccount.equals(previousAccount))
                || (previousOwner != null && !previousOwner.equals(ownerUserId));
        // 切换学号时，不能误用旧学号的密文。新学号若已绑定，运行时会读取绑定凭据；
        // 未绑定则要求本次填写新密码，避免把旧账号密码带给新账号。
        if (accountChanged && (submittedPassword == null || submittedPassword.isBlank())) {
            encryptedPassword = null;
        }
        if (enabled && ownerUserId == null) {
            throw new BusinessException(400, "启用定时采集前必须指定负责人用户 ID");
        }
        if (enabled && ownerUserId != null) {
            String account = effectiveAccount;
            if (account == null || account.isBlank()) {
                throw new BusinessException(400, "负责人尚未绑定教务账号，无法启用定时采集");
            }
            boolean bound = credentialService.get(ownerUserId, account) != null;
            if (!bound && (encryptedPassword == null || encryptedPassword.isBlank())) {
                throw new BusinessException(400, "该教务账号未绑定，请输入教务密码后再启用定时采集");
            }
        }
        if ("WEEKLY".equals(type) && (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > 7)) {
            throw new BusinessException(400, "每周执行日应为 1-7");
        }
        if ("MONTHLY".equals(type) && (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 31)) {
            throw new BusinessException(400, "每月执行日应为 1-31");
        }
        if (enabled && "ONCE".equals(type) && runAt == null) {
            throw new BusinessException(400, "指定时间任务必须填写执行日期和时间");
        }
        // 配置保存代表一次新的调度意图；清空上次计划时刻，避免沿用旧配置的
        // last_run_key 抑制新计划，也允许管理员修改后立即重新执行指定时间任务。
        jdbc.update("UPDATE jw_class_schedule_pull_schedule SET enabled=?,schedule_type=?,hour_num=?,minute_num=?,day_of_week=?,day_of_month=?,run_at=?,owner_user_id=?,jw_account=?,jw_password_enc=?,updated_by=?,last_run_key=NULL,updated_at=? WHERE id=1",
                enabled ? 1 : 0, type, hour, minute, dayOfWeek, dayOfMonth,
                runAt, ownerUserId, configuredAccount == null || configuredAccount.isBlank() ? null : configuredAccount.trim(),
                encryptedPassword, operatorId, LocalDateTime.now());
        // 首次保存时记录创建人；后续修改只更新 updated_by，便于审计“谁设置了这条自动任务”。
        jdbc.update("UPDATE jw_class_schedule_pull_schedule SET created_by=? WHERE id=1 AND created_by IS NULL", operatorId);
    }

    private static String rowKey(String term, String source, JSONObject row) {
        // 以新版接口的业务维度组成稳定键，而不是只按教学安排 ID 去重：同一安排可能
        // 有多个教师、教室或班级，教师页/教室页必须全部保留；完全相同的重复行仍会被
        // 折叠，避免源站偶发重复导致数据库唯一键冲突。
        String raw = String.join("|", term, source,
                text(row, "jx0404id"), text(row, "kch"), text(row, "kcmc"),
                text(row, "dwmc"), text(row, "kkdw"), text(row, "ksnd"), text(row, "zymc"),
                text(row, "bj"), text(row, "ktmc"), text(row, "zzdweek"), text(row, "jc"),
                text(row, "kkzc"), text(row, "jsmc"), first(text(row, "jsxm"), text(row, "xm")),
                text(row, "zzdkclb"), text(row, "zzdkcxz"), text(row, "zzdksfs"));
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { return Integer.toHexString(raw.hashCode()); }
    }
    private static String text(JSONObject o, String key) { Object v = o == null ? null : o.get(key); return v == null ? "" : String.valueOf(v).trim(); }
    private static String text(Map<String, Object> o, String key) { Object v = o == null ? null : o.get(key); return v == null ? "" : String.valueOf(v).trim(); }
    private static String value(Map<String, Object> o, String key) { String v = text(o, key); return v.isBlank() || "null".equals(v) ? null : v; }
    private static String first(String a, String b) { return a == null || a.isBlank() ? b : a; }
    private static java.math.BigDecimal decimal(JSONObject row, String... keys) { for (String key : keys) { String v=text(row,key); if(!v.isBlank()) try{return new java.math.BigDecimal(v);}catch(Exception ignored){}} return null; }
    private static int dayOf(String day) { if (day == null) return 0; int i = day.indexOf("一"); if (i >= 0) return 1; i=day.indexOf("二"); if(i>=0)return 2; i=day.indexOf("三");if(i>=0)return 3;i=day.indexOf("四");if(i>=0)return 4;i=day.indexOf("五");if(i>=0)return 5;i=day.indexOf("六");if(i>=0)return 6;i=day.indexOf("日");return i>=0?7:0; }
    private static int sectionIndex(String section) { try { String first=section.trim().split("[-,，]")[0]; int n=Integer.parseInt(first); return Math.max(1,(n+1)/2); } catch(Exception e){ return 1; } }
    private static Integer number(Object value, Integer fallback) { try { return value == null || String.valueOf(value).isBlank() ? fallback : Integer.valueOf(String.valueOf(value)); } catch(Exception e){ return fallback; } }
    private static Long longNumber(Object value, Long fallback) { try { return value == null || String.valueOf(value).isBlank() ? fallback : Long.valueOf(String.valueOf(value)); } catch(Exception e){ return fallback; } }
    private static boolean truthy(Object value) {
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        return "true".equalsIgnoreCase(String.valueOf(value)) || "1".equals(String.valueOf(value));
    }
    private static LocalDateTime parseDateTime(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return LocalDateTime.parse(String.valueOf(value).trim().replace(' ', 'T')); }
        catch (Exception e) { throw new BusinessException(400, "指定执行时间格式应为 YYYY-MM-DD HH:mm:ss"); }
    }
    private static String encode(Map<String,String> values) { StringBuilder b=new StringBuilder(); for(var e:values.entrySet()){if(b.length()>0)b.append('&');b.append(URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(e.getValue()==null?"":e.getValue(),StandardCharsets.UTF_8));} return b.toString(); }
    private static String safe(String message) { if(message==null)return ""; return message.length()>990?message.substring(0,990):message; }

    private void ensureColumn(String table, String column, String definition) {
        try {
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (Exception ignored) {
            // 已存在时数据库会报重复列；不同 MySQL/H2 版本的错误文本不一致，统一忽略。
        }
    }

    /** 将旧版 255 字符班级列升级到新版来源允许的长度。 */
    private void ensureClassNameWidth(String table) {
        int current = columnSize(table, "class_name");
        if (current <= 0 || current >= 1024) return;
        // 旧版索引包含完整的 class_name，先移除后再修改列宽，避免 MySQL
        // 在 MODIFY COLUMN 阶段因索引长度超限而拒绝升级。
        dropClassScheduleIndexes(table);
        try {
            if (isMysql()) {
                jdbc.execute("ALTER TABLE " + table + " MODIFY COLUMN class_name VARCHAR(1024)");
            } else {
                jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN class_name VARCHAR(1024)");
            }
        } catch (Exception e) {
            log.warn("班级课表表 {} 扩展 class_name 字段失败", table, e);
        }
    }

    /** 创建班级筛选索引；MySQL 前缀索引避免 1024 字符列超过 InnoDB key 长度。 */
    private void ensureClassScheduleIndexes(String table) {
        String classColumns = isMysql()
                ? "(term_value, college, major, class_name(191))"
                : "(term_value, college, major, class_name)";
        String filterColumns = isMysql()
                ? "(term_value, college, grade, major, class_name(191))"
                : "(term_value, college, grade, major, class_name)";
        ensureIndex(table, "idx_" + table + "_class", classColumns);
        ensureIndex(table, "idx_" + table + "_filter", filterColumns);
    }

    private void dropClassScheduleIndexes(String table) {
        dropIndex(table, "idx_" + table + "_class");
        dropIndex(table, "idx_" + table + "_filter");
    }

    private void dropIndex(String table, String index) {
        try {
            if (isMysql()) jdbc.execute("DROP INDEX " + index + " ON " + table);
            else jdbc.execute("DROP INDEX " + index);
        } catch (Exception ignored) {
            // 新安装尚无该索引，或数据库已在前一次启动中完成迁移。
        }
    }

    private int columnSize(String table, String column) {
        try (java.sql.Connection connection = jdbc.getDataSource().getConnection()) {
            try (java.sql.ResultSet columns = connection.getMetaData().getColumns(null, null, table, column)) {
                return columns.next() ? columns.getInt("COLUMN_SIZE") : 0;
            }
        } catch (Exception ignored) {
            return 0;
        }
    }

    private boolean isMysql() {
        try (java.sql.Connection connection = jdbc.getDataSource().getConnection()) {
            return connection.getMetaData().getDatabaseProductName().toLowerCase().contains("mysql");
        } catch (Exception ignored) {
            return false;
        }
    }

    private void ensureIndex(String table, String index, String columns) {
        try {
            jdbc.execute("CREATE INDEX " + index + " ON " + table + " " + columns);
        } catch (Exception ignored) {
            // 索引已存在，或当前数据库在 CREATE INDEX 时报告重复对象；不影响已有查询。
        }
    }
    private record TermOption(String value, String label) {}
    private record FetchResult(List<JSONObject> rows, int pages, int reportedCount) {}
}
