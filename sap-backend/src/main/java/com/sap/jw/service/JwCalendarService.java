package com.sap.jw.service;

import com.sap.jw.client.JwHttpSession;
import com.sap.jw.config.JwProperties;
import com.sap.entity.Setting;
import com.sap.jw.parser.CalendarParser;
import com.sap.service.SettingService;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 教学周历服务：从强智「教学周历查看」(/jsxsd/jxzl/jxzl_query) 推出学期开学日期(第 1 周周一)。
 * <p>周历对全校统一，故只需取一份；解析结果按学期落 sys_setting 缓存（key {@code term_start_<term>}），
 * 命中后不再抓（懒加载 + 一次取到永久复用）。全程 best-effort——任何失败都返回 null，由 App 回退到手动设置。</p>
 */
@Service
public class JwCalendarService {

    private static final String JXZL_PATH = "/jsxsd/jxzl/jxzl_query";
    /** 学期开学日期缓存的 setting key 前缀，拼上学期值，如 {@code term_start_2025-2026-2}。 */
    private static final String CACHE_KEY_PREFIX = "term_start_";

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwCalendarService.class);

    private final JwSessionManager sessionManager;
    private final CalendarParser parser;
    private final JwProperties props;
    private final SettingService settingService;

    public JwCalendarService(JwSessionManager sessionManager, CalendarParser parser,
                             JwProperties props, SettingService settingService) {
        this.sessionManager = sessionManager;
        this.parser = parser;
        this.props = props;
        this.settingService = settingService;
    }

    /**
     * 取某学期开学日期(ISO，如 "2026-03-09")；取不到返回 null。
     * <p>先查 DB 缓存：命中直接返回，不再抓教学周历；未命中才用会话抓取并解析，
     * 解析成功后写入缓存（仅缓存成功结果，失败不写以便下次重试）。</p>
     */
    public String getSemesterStart(Long userId, String account, String term) {
        if (term == null || term.isBlank()) return null;
        String normTerm = term.trim();

        String cached = settingService.getValue(CACHE_KEY_PREFIX + normTerm);
        if (cached != null && !cached.isBlank()) return cached;

        String start = fetchAndParse(userId, account, normTerm);
        if (start != null) {
            cache(normTerm, start);
        }
        return start;
    }

    /** 用教务会话抓教学周历并解析；任何失败返回 null。 */
    private String fetchAndParse(Long userId, String account, String term) {
        try {
            JwHttpSession session = sessionManager.getSession(userId, account);
            // jsxsd 全站学期参数为 xnxq01id（与课表 xskb_list.do 一致）；旧用 xnxqh 会被忽略→回默认学期→对不上→null
            String url = props.getJwglBase() + JXZL_PATH
                    + "?xnxq01id=" + URLEncoder.encode(term, StandardCharsets.UTF_8);
            HttpResponse<byte[]> resp = session.getFollow(url, 6);
            String body = decode(resp.body());
            String start = parser.parseSemesterStart(body, term);
            log.info("[教学周历] term={} status={} len={} hasTable={} -> start={}",
                    term, resp.statusCode(), body == null ? 0 : body.length(),
                    body != null && body.contains("周次"), start);
            return start;
        } catch (Exception e) {
            log.warn("[教学周历] term={} 抓取/解析失败: {}", term, e.toString());
            return null;
        }
    }

    /** 落 sys_setting 缓存；写库失败不影响本次返回。 */
    private void cache(String term, String start) {
        try {
            Setting s = new Setting();
            s.setSettingKey(CACHE_KEY_PREFIX + term);
            s.setSettingValue(start);
            s.setDescription("学期开学日期(第1周周一)缓存");
            settingService.updateSetting(s);
        } catch (Exception ignore) {
            // 缓存写失败无所谓，下次再抓
        }
    }

    /** jsxsd 各页编码不统一(课表 UTF-8，部分页 GBK)：选能解出中文标记的那种。 */
    private static String decode(byte[] body) {
        if (body == null) return null;
        String utf8 = new String(body, StandardCharsets.UTF_8);
        if (utf8.contains("周次") || utf8.contains("星期")) return utf8;
        try {
            String gbk = new String(body, Charset.forName("GBK"));
            if (gbk.contains("周次") || gbk.contains("星期")) return gbk;
        } catch (Exception ignore) {
            // ignore，落回 UTF-8
        }
        return utf8;
    }
}
