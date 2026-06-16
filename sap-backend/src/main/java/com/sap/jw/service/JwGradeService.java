package com.sap.jw.service;

import com.sap.common.BusinessException;
import com.sap.jw.client.JwHttpSession;
import com.sap.jw.config.JwProperties;
import com.sap.jw.parser.GradeParser;
import com.sap.jw.vo.GradeVO;
import org.springframework.stereotype.Service;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 成绩服务：抓取并解析学生全部课程成绩。
 */
@Service
public class JwGradeService {

    private static final String CJCX_PATH = "/jsxsd/kscj/cjcx_list";

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
        String html = fetch(sessionManager.getSession(userId, account));
        if (!html.contains("dataList")) {
            sessionManager.invalidate(userId, account);
            html = fetch(sessionManager.getSession(userId, account));
        }
        if (!html.contains("dataList")) {
            throw new BusinessException("获取成绩失败，请稍后重试");
        }
        credentialService.markSynced(userId, account);
        return parser.parse(html);
    }

    private String fetch(JwHttpSession session) {
        try {
            HttpResponse<byte[]> resp = session.getFollow(props.getJwglBase() + CJCX_PATH, 6);
            return new String(resp.body(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("获取成绩失败：" + e.getMessage());
        }
    }
}
