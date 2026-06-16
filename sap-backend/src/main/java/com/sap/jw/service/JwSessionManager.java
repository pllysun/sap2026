package com.sap.jw.service;

import com.sap.common.BusinessException;
import com.sap.jw.client.CaptchaRequiredException;
import com.sap.jw.client.JwAuthClient;
import com.sap.jw.client.JwHttpSession;
import com.sap.jw.config.JwProperties;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 教务登录会话的内存缓存与复用。
 * <p>学校 WebVPN 会话约 30 分钟，故按 (会员id, 教务学号) 缓存已登录会话（默认 25 分钟 TTL），
 * 期间复用免重复登录；过期或首次访问时用该学号的账密自动重登。同一 key 的登录串行化。</p>
 */
@Service
public class JwSessionManager {

    private final JwAuthClient authClient;
    private final JwCredentialService credentialService;
    private final JwProperties props;

    private final ConcurrentHashMap<String, JwHttpSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public JwSessionManager(JwAuthClient authClient, JwCredentialService credentialService, JwProperties props) {
        this.authClient = authClient;
        this.credentialService = credentialService;
        this.props = props;
    }

    /** 取（或建立）某学号的教务会话；未绑定则由 credentialService 抛业务异常。 */
    public JwHttpSession getSession(Long userId, String account) {
        String key = key(userId, account);
        JwHttpSession cached = sessions.get(key);
        if (cached != null && !cached.isExpired(props.getSessionTtlMinutes())) {
            return cached;
        }
        synchronized (lockFor(key)) {
            cached = sessions.get(key);
            if (cached != null && !cached.isExpired(props.getSessionTtlMinutes())) {
                return cached;
            }
            String password = credentialService.getDecryptedPassword(userId, account);
            JwHttpSession s;
            try {
                s = authClient.login(account, password);
            } catch (CaptchaRequiredException e) {
                // 后台拉取无法交互输入验证码，提示用户重新绑定
                throw new BusinessException("教务登录需要验证码，请到「我的」重新绑定该学号");
            }
            sessions.put(key, s);
            return s;
        }
    }

    /** 用指定账密直接登录并缓存（绑定时校验用，不读库）。需验证码时抛 CaptchaRequiredException。 */
    public JwHttpSession loginAndCache(Long userId, String account, String rawPassword) {
        String key = key(userId, account);
        synchronized (lockFor(key)) {
            JwHttpSession s = authClient.login(account, rawPassword);
            sessions.put(key, s);
            return s;
        }
    }

    /** 缓存一个已认证的会话（人工验证码续登成功后调用）。 */
    public void cache(Long userId, String account, JwHttpSession session) {
        sessions.put(key(userId, account), session);
    }

    public void invalidate(Long userId, String account) {
        sessions.remove(key(userId, account));
    }

    private String key(Long userId, String account) {
        return userId + ":" + account;
    }

    private Object lockFor(String key) {
        return locks.computeIfAbsent(key, k -> new Object());
    }
}
