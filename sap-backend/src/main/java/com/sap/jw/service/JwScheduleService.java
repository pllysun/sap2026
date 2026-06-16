package com.sap.jw.service;

import com.sap.common.BusinessException;
import com.sap.jw.client.JwHttpSession;
import com.sap.jw.config.JwProperties;
import com.sap.jw.parser.ScheduleParser;
import com.sap.jw.vo.ScheduleVO;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 课表服务：用会员的教务会话抓取并解析课表。
 */
@Service
public class JwScheduleService {

    private static final String XSKB_PATH = "/jsxsd/xskb/xskb_list.do";
    /** 学期下拉只保留最近 N 学年（含当前），覆盖大一~大四，过滤掉一长串历史学期。 */
    private static final int RECENT_ACADEMIC_YEARS = 4;
    private static final Pattern YEAR = Pattern.compile("(\\d{4})");

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
        String html = fetch(sessionManager.getSession(userId, account), term);
        if (!html.contains("kbtable")) {
            // 会话可能已失效（拿到登录页），强制重登一次
            sessionManager.invalidate(userId, account);
            html = fetch(sessionManager.getSession(userId, account), term);
        }
        if (!html.contains("kbtable")) {
            throw new BusinessException("获取课表失败，请稍后重试");
        }
        credentialService.markSynced(userId, account);
        ScheduleVO vo = parser.parse(html);
        filterRecentTerms(vo);

        // 开学日期：对所抓学期取教学周历开学日。已缓存(DB)的直接命中、不重复抓，
        // 故重新扫描各学期时每个学期仅首次抓一次，之后永久复用，风控可控。
        if (vo.getTerm() != null) {
            vo.setSemesterStartDate(calendarService.getSemesterStart(userId, account, vo.getTerm()));
        }
        return vo;
    }

    /** 学期下拉收敛到最近 {@link #RECENT_ACADEMIC_YEARS}+1 学年（按起始年），过滤海量历史学期。 */
    private void filterRecentTerms(ScheduleVO vo) {
        Integer cur = leadingYear(vo.getTerm());
        if (cur == null || vo.getTerms() == null) return;
        int min = cur - RECENT_ACADEMIC_YEARS;
        vo.getTerms().removeIf(t -> {
            Integer y = leadingYear(t.getValue());
            return y == null || y < min;
        });
    }

    private static Integer leadingYear(String term) {
        if (term == null) return null;
        Matcher m = YEAR.matcher(term);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private String fetch(JwHttpSession session, String term) {
        String url = props.getJwglBase() + XSKB_PATH;
        if (term != null && !term.isBlank()) {
            url += "?xnxq01id=" + URLEncoder.encode(term.trim(), StandardCharsets.UTF_8);
        }
        try {
            HttpResponse<byte[]> resp = session.getFollow(url, 6);
            return new String(resp.body(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("获取课表失败：" + e.getMessage());
        }
    }
}
