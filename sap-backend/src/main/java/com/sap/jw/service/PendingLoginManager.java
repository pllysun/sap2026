package com.sap.jw.service;

import com.sap.jw.client.PendingCas;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 验证码人工回退的挂起登录池：challengeId → 挂起上下文（会话+账密+CAS要素），TTL 5 分钟。
 */
@Service
public class PendingLoginManager {

    private static final long TTL_MS = 5 * 60 * 1000;

    /** 挂起的一次绑定登录。 */
    public static class Entry {
        public final Long userId;
        public final String account;
        public final String rawPassword;
        public final PendingCas cas;
        public final long expiresAt;

        Entry(Long userId, String account, String rawPassword, PendingCas cas, long expiresAt) {
            this.userId = userId;
            this.account = account;
            this.rawPassword = rawPassword;
            this.cas = cas;
            this.expiresAt = expiresAt;
        }
    }

    private final ConcurrentHashMap<String, Entry> map = new ConcurrentHashMap<>();

    public String put(Long userId, String account, String rawPassword, PendingCas cas) {
        purge();
        String id = UUID.randomUUID().toString().replace("-", "");
        map.put(id, new Entry(userId, account, rawPassword, cas, System.currentTimeMillis() + TTL_MS));
        return id;
    }

    public Entry get(String id) {
        if (id == null) return null;
        Entry e = map.get(id);
        if (e == null) return null;
        if (e.expiresAt < System.currentTimeMillis()) {
            map.remove(id);
            return null;
        }
        return e;
    }

    public void remove(String id) {
        map.remove(id);
    }

    private void purge() {
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(en -> en.getValue().expiresAt < now);
    }
}
