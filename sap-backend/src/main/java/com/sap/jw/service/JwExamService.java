package com.sap.jw.service;

import com.sap.common.BusinessException;
import com.sap.jw.client.JwHttpSession;
import com.sap.jw.config.JwProperties;
import com.sap.jw.parser.ExamParser;
import com.sap.jw.vo.ExamVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 考试安排服务：按学期抓取并解析考试安排。
 */
@Service
public class JwExamService {

    private static final String LIST_PATH = "/jsxsd/xsks/xsksap_list";
    private static final String QUERY_PATH = "/jsxsd/xsks/xsksap_query";
    private static final Pattern SELECTED_TERM =
            Pattern.compile("<option value=\"([^\"]+)\"[^>]*selected");

    private final JwSessionManager sessionManager;
    private final JwCredentialService credentialService;
    private final ExamParser parser;
    private final JwProperties props;

    public JwExamService(JwSessionManager sessionManager, JwCredentialService credentialService,
                         ExamParser parser, JwProperties props) {
        this.sessionManager = sessionManager;
        this.credentialService = credentialService;
        this.parser = parser;
        this.props = props;
    }

    /** 获取指定学期考试安排；term 为空时取考试查询页默认学期。 */
    public List<ExamVO> getExams(Long userId, String account, String term) {
        JwHttpSession session = sessionManager.getSession(userId, account);
        String t = (term == null || term.isBlank()) ? defaultTerm(session) : term.trim();

        String html = fetchList(session, t);
        if (!html.contains("dataList")) {
            sessionManager.invalidate(userId, account);
            session = sessionManager.getSession(userId, account);
            if (t.isBlank()) t = defaultTerm(session);
            html = fetchList(session, t);
        }
        if (!html.contains("dataList")) {
            throw new BusinessException("获取考试安排失败，请稍后重试");
        }
        credentialService.markSynced(userId, account);
        return parser.parse(html);
    }

    private String fetchList(JwHttpSession session, String term) {
        try {
            return session.postForm(props.getJwglBase() + LIST_PATH,
                    Map.of("xnxqid", term == null ? "" : term, "xqlb", "")).body();
        } catch (Exception e) {
            throw new BusinessException("获取考试安排失败：" + e.getMessage());
        }
    }

    private String defaultTerm(JwHttpSession session) {
        try {
            String html = session.get(props.getJwglBase() + QUERY_PATH).body();
            Matcher m = SELECTED_TERM.matcher(html);
            return m.find() ? m.group(1) : "";
        } catch (Exception e) {
            return "";
        }
    }
}
