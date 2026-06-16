package com.sap.service;

import com.sap.mapper.ApiRequestStatMapper;
import com.sap.mapper.CosTrafficMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流量/接口统计聚合服务。窗口 = 最近 N 天（含今天）。趋势按日补零，便于前端折线连续。
 */
@Service
public class StatsService {

    @Autowired
    private CosTrafficMapper cosTrafficMapper;
    @Autowired
    private ApiRequestStatMapper apiRequestStatMapper;

    private LocalDate startOf(int days) {
        if (days <= 0) days = 7;
        return LocalDate.now().minusDays(days - 1L);
    }

    private static long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o == null) return 0L;
        try { return Long.parseLong(o.toString()); } catch (Exception e) { return 0L; }
    }

    /** 概览卡片：总上传/下载字节、总请求数、活跃用户数。 */
    public Map<String, Object> overview(int days) {
        LocalDate start = startOf(days);
        Map<String, Object> cos = cosTrafficMapper.totals(start);
        Map<String, Object> r = new HashMap<>();
        r.put("uploadBytes", cos != null ? toLong(cos.get("uploadBytes")) : 0L);
        r.put("downloadBytes", cos != null ? toLong(cos.get("downloadBytes")) : 0L);
        Long total = apiRequestStatMapper.totalRequests(start);
        Long active = apiRequestStatMapper.activeUsers(start);
        r.put("totalRequests", total != null ? total : 0L);
        r.put("activeUsers", active != null ? active : 0L);
        r.put("days", days <= 0 ? 7 : days);
        return r;
    }

    public List<Map<String, Object>> cosByUser(int days) {
        return cosTrafficMapper.aggByUser(startOf(days));
    }

    /** COS 趋势：返回 {dates:[], upload:[], download:[]}（字节，按日补零）。 */
    public Map<String, Object> cosTrend(int days) {
        int n = days <= 0 ? 7 : days;
        LocalDate start = startOf(n);
        Map<String, long[]> byDate = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) byDate.put(start.plusDays(i).toString(), new long[]{0, 0});
        for (Map<String, Object> row : cosTrafficMapper.trend(start)) {
            String d = String.valueOf(row.get("statDate"));
            long[] v = byDate.get(d);
            if (v != null) {
                v[0] = toLong(row.get("uploadBytes"));
                v[1] = toLong(row.get("downloadBytes"));
            }
        }
        List<String> dates = new ArrayList<>();
        List<Long> upload = new ArrayList<>();
        List<Long> download = new ArrayList<>();
        byDate.forEach((d, v) -> { dates.add(d); upload.add(v[0]); download.add(v[1]); });
        Map<String, Object> r = new HashMap<>();
        r.put("dates", dates);
        r.put("upload", upload);
        r.put("download", download);
        return r;
    }

    public List<Map<String, Object>> apiTop(int days, int limit) {
        return apiRequestStatMapper.topEndpoints(startOf(days), limit <= 0 ? 20 : limit);
    }

    /** 接口请求趋势：返回 {dates:[], counts:[]}（按日补零）。 */
    public Map<String, Object> apiTrend(int days) {
        int n = days <= 0 ? 7 : days;
        LocalDate start = startOf(n);
        Map<String, Long> byDate = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) byDate.put(start.plusDays(i).toString(), 0L);
        for (Map<String, Object> row : apiRequestStatMapper.trend(start)) {
            String d = String.valueOf(row.get("statDate"));
            if (byDate.containsKey(d)) byDate.put(d, toLong(row.get("cnt")));
        }
        Map<String, Object> r = new HashMap<>();
        r.put("dates", new ArrayList<>(byDate.keySet()));
        r.put("counts", new ArrayList<>(byDate.values()));
        return r;
    }

    public List<Map<String, Object>> apiByUser(Long userId, int days) {
        return apiRequestStatMapper.byUser(startOf(days), userId);
    }

    public List<Map<String, Object>> apiByEndpoint(String endpoint, int days) {
        return apiRequestStatMapper.byEndpoint(startOf(days), endpoint);
    }

    public List<Map<String, Object>> users() {
        return apiRequestStatMapper.distinctUsers();
    }
}
