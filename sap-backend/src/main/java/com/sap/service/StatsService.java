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
 *
 * <p><b>注意</b>：不同数据库/驱动返回的结果集列标签大小写不一致（H2 返回小写
 * statdate/httpmethod/uploadbytes，MySQL 通常保留别名驼峰）。因此本类读取结果 Map 一律
 * 用 {@link #ci} 做<b>大小写无关</b>取值，并把对外返回的 Map 统一<b>规范化为驼峰 key</b>，
 * 保证趋势日期匹配、前端字段访问在任何数据库下都正确。</p>
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

    /** 大小写无关地从结果 Map 取值，兼容不同数据库的列标签大小写。 */
    private static Object ci(Map<String, Object> row, String key) {
        if (row == null) return null;
        Object v = row.get(key);
        if (v != null || row.containsKey(key)) return v;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        return null;
    }

    /** 日期值统一转为 "yyyy-MM-dd"，兼容 java.sql.Date / LocalDate / 字符串。 */
    private static String dateKey(Object o) {
        if (o == null) return "";
        String s = String.valueOf(o);
        return s.length() >= 10 ? s.substring(0, 10) : s;
    }

    /** 概览卡片：总上传/下载字节、总请求数、活跃用户数。 */
    public Map<String, Object> overview(int days) {
        LocalDate start = startOf(days);
        Map<String, Object> cos = cosTrafficMapper.totals(start);
        Map<String, Object> r = new HashMap<>();
        r.put("uploadBytes", toLong(ci(cos, "uploadBytes")));
        r.put("downloadBytes", toLong(ci(cos, "downloadBytes")));
        Long total = apiRequestStatMapper.totalRequests(start);
        Long active = apiRequestStatMapper.activeUsers(start);
        r.put("totalRequests", total != null ? total : 0L);
        r.put("activeUsers", active != null ? active : 0L);
        r.put("days", days <= 0 ? 7 : days);
        return r;
    }

    public List<Map<String, Object>> cosByUser(int days) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : cosTrafficMapper.aggByUser(startOf(days))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", toLong(ci(row, "userId")));
            m.put("userName", ci(row, "userName"));
            m.put("uploadBytes", toLong(ci(row, "uploadBytes")));
            m.put("downloadBytes", toLong(ci(row, "downloadBytes")));
            m.put("uploadCount", toLong(ci(row, "uploadCount")));
            m.put("downloadCount", toLong(ci(row, "downloadCount")));
            out.add(m);
        }
        return out;
    }

    /** COS 趋势：返回 {dates:[], upload:[], download:[]}（字节，按日补零）。 */
    public Map<String, Object> cosTrend(int days) {
        int n = days <= 0 ? 7 : days;
        LocalDate start = startOf(n);
        Map<String, long[]> byDate = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) byDate.put(start.plusDays(i).toString(), new long[]{0, 0});
        for (Map<String, Object> row : cosTrafficMapper.trend(start)) {
            long[] v = byDate.get(dateKey(ci(row, "statDate")));
            if (v != null) {
                v[0] = toLong(ci(row, "uploadBytes"));
                v[1] = toLong(ci(row, "downloadBytes"));
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
        return mapEndpointRows(apiRequestStatMapper.topEndpoints(startOf(days), limit <= 0 ? 20 : limit));
    }

    /** 接口请求趋势：返回 {dates:[], counts:[]}（按日补零）。 */
    public Map<String, Object> apiTrend(int days) {
        int n = days <= 0 ? 7 : days;
        LocalDate start = startOf(n);
        Map<String, Long> byDate = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) byDate.put(start.plusDays(i).toString(), 0L);
        for (Map<String, Object> row : apiRequestStatMapper.trend(start)) {
            String d = dateKey(ci(row, "statDate"));
            if (byDate.containsKey(d)) byDate.put(d, toLong(ci(row, "cnt")));
        }
        Map<String, Object> r = new HashMap<>();
        r.put("dates", new ArrayList<>(byDate.keySet()));
        r.put("counts", new ArrayList<>(byDate.values()));
        return r;
    }

    public List<Map<String, Object>> apiByUser(Long userId, int days) {
        return mapEndpointRows(apiRequestStatMapper.byUser(startOf(days), userId));
    }

    /** 用户×接口 全量明细（规范化驼峰），供前端统一搜索/排序表格。 */
    public List<Map<String, Object>> apiDetail(int days, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : apiRequestStatMapper.detail(startOf(days), limit <= 0 ? 1000 : limit)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", toLong(ci(row, "userId")));
            m.put("userName", ci(row, "userName"));
            m.put("endpoint", ci(row, "endpoint"));
            m.put("httpMethod", ci(row, "httpMethod"));
            m.put("cnt", toLong(ci(row, "cnt")));
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> apiByEndpoint(String endpoint, int days) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : apiRequestStatMapper.byEndpoint(startOf(days), endpoint)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", toLong(ci(row, "userId")));
            m.put("userName", ci(row, "userName"));
            m.put("cnt", toLong(ci(row, "cnt")));
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> users() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : apiRequestStatMapper.distinctUsers()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", toLong(ci(row, "userId")));
            m.put("userName", ci(row, "userName"));
            out.add(m);
        }
        return out;
    }

    /** 把 endpoint/httpMethod/cnt 行规范化为驼峰 key。 */
    private List<Map<String, Object>> mapEndpointRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("endpoint", ci(row, "endpoint"));
            m.put("httpMethod", ci(row, "httpMethod"));
            m.put("cnt", toLong(ci(row, "cnt")));
            out.add(m);
        }
        return out;
    }
}
