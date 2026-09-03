package com.sap.jw.service;

import com.sap.common.BusinessException;
import com.sap.jw.client.CaptchaRequiredException;
import com.sap.jw.client.MfaRequiredException;
import com.sap.jw.client.JwAuthClient;
import com.sap.jw.client.JwHttpSession;
import com.sap.jw.config.JwProperties;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 教务登录会话的内存缓存与复用。
 * <p>学校 CAS/教务会话存在有效期，故按 (会员id, 教务学号) 缓存已登录会话（默认 25 分钟 TTL），
 * 期间复用免重复登录；过期或首次访问时用该学号的账密自动重登。同一 key 的登录串行化。</p>
 */
@Service
public class JwSessionManager {

    private final JwAuthClient authClient;
    private final JwCredentialService credentialService;
    private final JwProperties props;
    private final PendingLoginManager pendingManager;

    private final ConcurrentHashMap<String, JwHttpSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public JwSessionManager(JwAuthClient authClient, JwCredentialService credentialService,
                            JwProperties props, PendingLoginManager pendingManager) {
        this.authClient = authClient;
        this.credentialService = credentialService;
        this.props = props;
        this.pendingManager = pendingManager;
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
            } catch (MfaRequiredException e) {
                // 短信已发出：登记待验证会话并抛 MFA-pending（全局异常处理返回 428），
                // App 弹短信输入框、用户输码调 /api/jw/bind/mfa 续登缓存会话后重试本请求。
                String cid = pendingManager.put(userId, account, password, e.getPending());
                throw new com.sap.jw.client.JwMfaPendingException(cid, e.getPhone());
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

    /**
     * 用一次性账密建立不写入会话缓存的教务会话。
     *
     * <p>班级课表管理端的手动采集和自动采集配置使用该入口，密码只在当前请求/任务的
     * 内存中存在，避免把管理员输入的密码误写入会员绑定表或长期会话缓存。图形验证码仍需
     * 在 App 的教务账号页完成绑定；短信二次验证会原样抛出，由管理端采集弹窗继续完成。</p>
     */
    public JwHttpSession loginEphemeral(String account, String rawPassword) {
        try {
            return authClient.login(account, rawPassword);
        } catch (CaptchaRequiredException e) {
            throw new BusinessException("教务登录需要验证码，请先在 App 的教务账号页完成绑定后再采集");
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
