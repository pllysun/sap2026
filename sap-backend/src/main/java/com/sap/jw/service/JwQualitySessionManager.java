package com.sap.jw.service;

import com.sap.jw.client.JwHttpSession;
import com.sap.jw.client.JwQualityAuthClient;
import com.sap.jw.client.JwQualitySession;
import com.sap.jw.config.JwProperties;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/** 按会员与学号复用教学质量保障系统的 bearer 会话。 */
@Service
public class JwQualitySessionManager {

    private final JwSessionManager jwSessionManager;
    private final JwQualityAuthClient authClient;
    private final JwProperties props;
    private final ConcurrentHashMap<String, JwQualitySession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public JwQualitySessionManager(JwSessionManager jwSessionManager, JwQualityAuthClient authClient,
                                   JwProperties props) {
        this.jwSessionManager = jwSessionManager;
        this.authClient = authClient;
        this.props = props;
    }

    public JwQualitySession getSession(Long userId, String account) {
        String key = key(userId, account);
        JwQualitySession cached = sessions.get(key);
        if (cached != null && !cached.isExpired(props.getQualitySessionTtlMinutes())) return cached;
        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
            cached = sessions.get(key);
            if (cached != null && !cached.isExpired(props.getQualitySessionTtlMinutes())) return cached;
            JwHttpSession casSession = jwSessionManager.getSession(userId, account);
            JwQualitySession created = authClient.login(casSession);
            sessions.put(key, created);
            return created;
        }
    }

    public void invalidate(Long userId, String account) {
        sessions.remove(key(userId, account));
    }

    private String key(Long userId, String account) {
        return userId + ":" + account;
    }
}
