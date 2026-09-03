package com.sap.jw.service;

import com.sap.jw.client.PendingCas;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理端班级课表采集的短信二次验证上下文。
 *
 * <p>上下文只在当前实例内存中保留五分钟，密码仍只存在 CAS 已加密字段中；
 * challengeId 只能由发起该采集的管理员继续使用。</p>
 */
@Service
public class PendingClassScheduleManager {

    private static final long TTL_MS = 5 * 60 * 1000L;

    public static final class Entry {
        public final Long userId;
        public final String account;
        public final String term;
        public final PendingCas cas;
        public final String phone;
        public final long expiresAt;

        private Entry(Long userId, String account, String term, PendingCas cas,
                      String phone, long expiresAt) {
            this.userId = userId;
            this.account = account;
            this.term = term;
            this.cas = cas;
            this.phone = phone;
            this.expiresAt = expiresAt;
        }
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public String put(Long userId, String account, String term, PendingCas cas, String phone) {
        purge();
        String id = UUID.randomUUID().toString().replace("-", "");
        entries.put(id, new Entry(userId, account, term, cas, phone,
                System.currentTimeMillis() + TTL_MS));
        return id;
    }

    public Entry get(String id) {
        if (id == null || id.isBlank()) return null;
        Entry entry = entries.get(id);
        if (entry == null) return null;
        if (entry.expiresAt < System.currentTimeMillis()) {
            entries.remove(id);
            return null;
        }
        return entry;
    }

    public void remove(String id) {
        if (id != null) entries.remove(id);
    }

    private void purge() {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(item -> item.getValue().expiresAt < now);
    }
}
