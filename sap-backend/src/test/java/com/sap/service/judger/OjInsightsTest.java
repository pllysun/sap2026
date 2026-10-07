package com.sap.service.judger;
import com.sap.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OjInsightsTest {
    @Test void equalValuesDoNotInventAWinningPercentile(){
        var result=OjInsightsService.distribution(List.of(0L,0L,0L),0);
        assertEquals(0d,result.get("beatsPercent"));
        var bins=(List<Map<String,Object>>)result.get("bins");
        assertEquals(3L,bins.stream().mapToLong(b->((Number)b.get("count")).longValue()).sum());
        assertEquals(1,bins.stream().filter(b->Boolean.TRUE.equals(b.get("current"))).count());
    }
    @Test void smallerMetricsBeatOnlyStrictlyLargerMetrics(){
        assertEquals(50d,OjInsightsService.distribution(List.of(10L,10L,20L,30L),10).get("beatsPercent"));
        assertEquals(0d,OjInsightsService.distribution(List.of(10L,30L),30).get("beatsPercent"));
    }
    @Test void foreignSubmissionIsRejectedBeforeAnyStatisticsQuery(){
        var db=mock(JdbcTemplate.class);var oj=mock(OjService.class);var service=new OjInsightsService(db,oj);
        when(oj.job(1L,2L,false)).thenThrow(new BusinessException(404,"提交记录不存在"));
        assertThrows(BusinessException.class,()->service.performance(1L,2L));verifyNoInteractions(db);
    }
    @Test void rankingSelectsBestPerUserAndNeverReturnsIdsOrCode(){
        var db=mock(JdbcTemplate.class);var oj=mock(OjService.class);var service=new OjInsightsService(db,oj);when(oj.currentSignature()).thenReturn("sig");
        when(db.queryForList(anyString(),eq("sig"),eq(1L),eq("cpp"),eq("FUNCTION"))).thenReturn(List.of(
            Map.of("id",1L,"userId",9L,"nickname","A","name","甲","studentId","S001","timeMs",10L,"memoryBytes",20L),
            Map.of("id",2L,"userId",9L,"nickname","A","name","甲","studentId","S001","timeMs",12L,"memoryBytes",20L),
            Map.of("id",3L,"userId",8L,"nickname","B","name","乙","studentId","S002","timeMs",15L,"memoryBytes",30L)));
        var result=service.ranking(1L,"cpp","FUNCTION");var records=(List<Map<String,Object>>)result.get("records");assertEquals(2,records.size());assertEquals(10L,records.getFirst().get("timeMs"));assertFalse(records.getFirst().containsKey("userId"));assertFalse(records.getFirst().containsKey("code"));
        verify(db).queryForList(argThat(sql->sql.contains("s.kind='SUBMIT'")&&sql.contains("s.revision=p.revision")&&sql.contains("$.signature")&&sql.contains("u.deleted=0")&&sql.contains("AND s.problem_set_id IS NULL")&&!sql.contains("OR EXISTS")),eq("sig"),eq(1L),eq("cpp"),eq("FUNCTION"));
    }
    @Test void acLeaderboardCountsDistinctProblems(){
        var db=mock(JdbcTemplate.class);var oj=mock(OjService.class);when(oj.currentSignature()).thenReturn("sig");when(db.queryForMap(anyString(),eq("sig"))).thenReturn(Map.of("total",6,"easy",3,"medium",2,"hard",1));new OjInsightsService(db,oj).leaderboard();
        verify(db).queryForList(argThat(sql->sql.contains("COUNT(DISTINCT p.id)")&&sql.contains("ORDER BY acCount DESC")&&sql.contains("WHERE s.kind='SUBMIT' AND s.problem_set_id IS NULL")&&sql.contains("LEFT JOIN oj_problem")&&sql.contains("AS studentId")&&sql.contains("AS easyAc")&&sql.contains("AS mediumAc")&&sql.contains("AS hardAc")&&!sql.contains("OR EXISTS")),eq("sig"));
        verify(db).queryForMap(argThat(sql->sql.contains("validation_signature=?")&&sql.contains("AS total")&&sql.contains("AS easy")&&sql.contains("AS medium")&&sql.contains("AS hard")),eq("sig"));
    }

    @Test void monitorGroupsRunsAndDoesNotReturnTheFlatEventStream(){
        var db=mock(JdbcTemplate.class);var oj=mock(OjService.class);when(oj.health()).thenReturn(Map.of("available",true));when(db.queryForObject(anyString(),eq(Long.class))).thenReturn(4L);
        var result=new OjInsightsService(db,oj).monitor(1);assertFalse(result.containsKey("events"));assertEquals(4L,result.get("total"));
        verify(db).queryForList(argThat(sql->sql.contains("u.name AS name")&&sql.contains("u.student_id AS studentId")&&!sql.contains("s.code")&&!sql.contains("snapshot_json")));
        verify(db).queryForList(argThat(sql->sql.contains("u.name AS name")&&sql.contains("u.student_id AS studentId")&&!sql.contains("s.code")&&!sql.contains("snapshot_json")),eq(0));
    }
    @Test void oldTasksStillHaveAQueueStartFinishTimeline(){
        var db=mock(JdbcTemplate.class);var oj=mock(OjService.class);var task=new LinkedHashMap<String,Object>();task.put("id",1L);task.put("status","CE");task.put("createdAt","2026-10-01 12:00:00");task.put("startedAt","2026-10-01 12:00:01");task.put("finishedAt","2026-10-01 12:00:02");
        when(db.queryForList(anyString(),eq(1L))).thenAnswer(invocation->((String)invocation.getArgument(0)).contains("FROM oj_runtime_log")?List.of():List.of(task));
        var detail=new OjInsightsService(db,oj).monitorJob(1L);var timeline=(List<Map<String,Object>>)detail.get("timeline");assertEquals(List.of("QUEUED","STARTED","FINISHED"),timeline.stream().map(r->r.get("event")).toList());assertEquals("CE",timeline.getLast().get("status"));assertEquals(task.get("finishedAt"),timeline.getLast().get("createdAt"));
    }
    @Test void missingRunAndAnonymousControllerAreExplicitlyProtected(){
        var db=mock(JdbcTemplate.class);assertThrows(BusinessException.class,()->new OjInsightsService(db,mock(OjService.class)).monitorJob(1L));
        assertTrue(com.sap.controller.judger.OjController.class.isAnnotationPresent(cn.dev33.satoken.annotation.SaCheckLogin.class));
    }
}
