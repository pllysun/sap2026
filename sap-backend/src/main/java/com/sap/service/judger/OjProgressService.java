package com.sap.service.judger;

import com.sap.mapper.judger.OjSubmissionMapper;
import com.sap.vo.judger.OjProgressSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OjProgressService {
    private final OjSubmissionMapper submissions;

    public Map<String,Object> library(Map<String,Object> page, Long userId) {
        enrich(page, "records", "id", userId, null);
        return page;
    }
    public Map<String,Object> libraryStatus(List<Long> ids,Long userId) {
        List<Map<String,Object>> rows=new ArrayList<>();
        for(Long id:ids) {Map<String,Object> row=new LinkedHashMap<>();row.put("id",id);rows.add(row);}
        Map<String,Object> page=new LinkedHashMap<>();page.put("records",rows);
        return library(page,userId);
    }

    // Called only after the set service has checked visibility and removed hidden questions.
    public Map<String,Object> problemSet(Map<String,Object> detail, Long userId, Long setId) {
        enrich(detail, "items", "problemId", userId, Objects.requireNonNull(setId));
        return detail;
    }

    @SuppressWarnings("unchecked")
    private void enrich(Map<String,Object> view, String rowsKey, String idKey, Long userId, Long setId) {
        var rows = (List<Map<String,Object>>) view.get(rowsKey);
        if (rows == null || rows.isEmpty()) return;
        var ids = rows.stream().map(row -> ((Number)row.get(idKey)).longValue()).distinct().toList();
        var progress = new HashMap<Long,OjProgressSummary>();
        for (var row : submissions.progress(Objects.requireNonNull(userId), setId, ids)) {
            progress.put(row.getProblemId(), row);
        }
        for (var row : rows) row.put("progress", badge(progress.get(((Number)row.get(idKey)).longValue())));
    }

    private Map<String,Object> badge(OjProgressSummary row) {
        boolean accepted = row != null && Integer.valueOf(1).equals(row.getAccepted());
        boolean pending = row != null && Integer.valueOf(1).equals(row.getPending());
        String verdict = row == null || row.getLastStatus() == null ? "" : row.getLastStatus();
        String state = accepted ? "AC" : "WA".equals(verdict) ? "WRONG"
            : !verdict.isEmpty() ? "ISSUE" : pending ? "PENDING" : "NONE";
        return Map.of("state",state,"lastVerdict",verdict,"pending",pending);
    }
}
