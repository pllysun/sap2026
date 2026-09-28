package com.sap.jw.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** 仅访问本地数据库，不触发教务采集；同一批次使用一致性快照。 */
@Service
public class ClassScheduleSyncService {
    private final ClassScheduleService schedules;
    private final ObjectMapper json;

    public ClassScheduleSyncService(ClassScheduleService schedules, ObjectMapper json) {
        this.schedules = schedules;
        this.json = json;
    }

    public record Selection(String key, String term, String college, String grade,
                            String major, String className, String revision) {}
    public record Request(List<Selection> selections, boolean force) {}
    public record Item(String key, String revision, Map<String, Object> data) {}
    public record Response(boolean changed, List<Item> items) {}

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Response sync(Request request) {
        if (request == null || request.selections() == null || request.selections().isEmpty()
                || request.selections().size() > 100) throw new BusinessException(400, "每批校验 1 至 100 份班级课表");
        Set<String> keys = new HashSet<>();
        for (Selection s : request.selections()) {
            if (s == null || !valid(s.key(), 160) || !valid(s.term(), 20)
                    || !valid(s.college(), 255) || !valid(s.major(), 255) || !valid(s.className(), 255)
                    || s.grade() == null || s.grade().length() > 20
                    || s.revision() != null && !s.revision().matches("[a-f0-9]{64}")
                    || !keys.add(s.key())) throw new BusinessException(400, "班级课表校验参数无效");
        }
        Set<String> terms = new HashSet<>();
        schedules.terms().forEach(t -> terms.add(String.valueOf(t.get("value"))));
        List<Item> items = new ArrayList<>();
        boolean changed = request.force();
        for (Selection s : request.selections()) {
            // 未采集/移除的学期不应被当作空课表覆盖本地缓存。
            if (!terms.contains(s.term())) throw new BusinessException(409, "学期数据暂不可用，请保留本地课表");
            Map<String, Object> data = schedules.scheduleForSync(s.term(), s.college(), s.major(), s.grade(), s.className());
            String revision = fingerprint(data);
            changed |= !revision.equals(s.revision());
            items.add(new Item(s.key(), revision, data));
        }
        if (!changed) items = items.stream().map(i -> new Item(i.key(), i.revision(), null)).toList();
        return new Response(changed, items);
    }

    /** 排序后计算内容指纹：重新采集的 ID、行顺序和采集时间不造成误刷新。 */
    String fingerprint(Map<String, Object> data) {
        try {
            List<String> courses = new ArrayList<>();
            for (Object row : (List<?>) data.getOrDefault("courses", List.of())) {
                @SuppressWarnings("unchecked") Map<String, Object> course = (Map<String, Object>) row;
                courses.add(json.writeValueAsString(new TreeMap<>(course)));
            }
            Collections.sort(courses);
            String content = json.writeValueAsString(Arrays.asList(data.get("term"), data.get("semesterStartDate"), courses));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("无法计算班级课表版本", e); }
    }

    private static boolean valid(String value, int max) {
        return value != null && !value.isBlank() && value.length() <= max;
    }
}
