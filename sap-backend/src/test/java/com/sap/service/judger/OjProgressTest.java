package com.sap.service.judger;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.config.judger.JudgerProperties;
import com.sap.entity.judger.OjSubmission;
import com.sap.mapper.judger.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OjProgressTest {
    private JdbcTemplate jdbc;
    private SqlSession session;
    private OjSubmissionMapper mapper;
    private OjProgressService progress;

    @BeforeEach void setup() {
        var ds=new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        jdbc=new JdbcTemplate(ds);
        var columns=new ArrayList<String>();
        for(var field:OjSubmission.class.getDeclaredFields()) {
            var column=field.getAnnotation(jakarta.persistence.Column.class);
            String name=column==null?field.getName():column.name();
            String type=field.getType()==Long.class?"BIGINT":field.getType()==Integer.class?"INT":
                field.getType()==Boolean.class?"BOOLEAN":field.getType()==LocalDateTime.class?"TIMESTAMP":"LONGTEXT";
            columns.add(name+" "+type+(name.equals("id")?" PRIMARY KEY":""));
        }
        jdbc.execute("CREATE TABLE oj_submission("+String.join(",",columns)+")");
        var config=new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new Environment("progress",new JdbcTransactionFactory(),ds));
        config.addMapper(OjSubmissionMapper.class);
        session=new MybatisSqlSessionFactoryBuilder().build(config).openSession(true);
        mapper=session.getMapper(OjSubmissionMapper.class);
        progress=new OjProgressService(mapper);
    }
    @AfterEach void close(){session.close();}

    private void job(long id,long user,Long set,long problem,String kind,String status,Boolean ever) {
        jdbc.update("INSERT INTO oj_submission(id,user_id,problem_set_id,problem_id,kind,status,ever_accepted,code,snapshot_json,result_json) VALUES(?,?,?,?,?,?,?,'PRIVATE_CODE','PRIVATE_TESTS','PRIVATE_RESULTS')",id,user,set,problem,kind,status,ever);
        session.clearCache();
    }
    @SuppressWarnings("unchecked")
    private Map<String,Object> badge(long user,Long set,long problem) {
        var row=new LinkedHashMap<String,Object>();row.put(set==null?"id":"problemId",problem);
        var view=new LinkedHashMap<String,Object>();view.put(set==null?"records":"items",List.of(row));
        if(set==null)progress.library(view,user);else progress.problemSet(view,user,set);
        String serialized=view.toString();
        assertFalse(serialized.contains("PRIVATE_"));
        return (Map<String,Object>)row.get("progress");
    }
    private String state(long user,Long set,long problem){return (String)badge(user,set,problem).get("state");}

    @Test void progressIsIndependentForEachAccountLibraryAndEachSet() {
        job(1,10,null,1,"SUBMIT","WA",false);
        job(2,10,7L,1,"SUBMIT","AC",true);
        job(3,11,null,1,"SUBMIT","AC",true);
        job(4,10,8L,1,"SUBMIT","CE",false);
        assertEquals("WRONG",state(10,null,1));
        assertEquals("AC",state(10,7L,1));
        assertEquals("ISSUE",state(10,8L,1));
        assertEquals("AC",state(11,null,1));
        assertEquals("NONE",state(11,7L,1));
        assertEquals("NONE",state(12,null,1));
    }
    @Test void sampleRunsAndPlatformValidationNeverCountAsSolvedOrFailed() {
        job(1,10,null,1,"RUN","AC",true);
        job(2,10,7L,1,"RUN","RE",false);
        job(3,10,null,1,"VALIDATE","AC",true);
        assertEquals("NONE",state(10,null,1));
        assertEquals("NONE",state(10,7L,1));
    }
    @Test void latestFormalResultSwitchesBetweenWrongAndExecutionIssue() {
        job(1,10,7L,1,"SUBMIT","WA",false);assertEquals("WRONG",state(10,7L,1));
        job(2,10,7L,1,"SUBMIT","RE",false);assertEquals("ISSUE",state(10,7L,1));
        assertEquals("RE",badge(10,7L,1).get("lastVerdict"));
        job(3,10,7L,1,"SUBMIT","WA",false);assertEquals("WRONG",state(10,7L,1));
        for(String verdict:List.of("CE","TLE","MLE","OLE","SYSTEM_ERROR")) {
            job(10+jdbc.queryForObject("SELECT COUNT(*) FROM oj_submission",Long.class),10,7L,1,"SUBMIT",verdict,false);
            assertEquals("ISSUE",state(10,7L,1),verdict);
        }
    }
    @Test void pendingDoesNotErasePreviousResultAndOlderLatePassStillWins() {
        job(1,10,null,1,"SUBMIT","WA",false);
        job(2,10,null,1,"SUBMIT","RUNNING",false);
        assertEquals("WRONG",state(10,null,1));assertEquals(true,badge(10,null,1).get("pending"));
        job(3,10,null,2,"SUBMIT","QUEUED",false);assertEquals("PENDING",state(10,null,2));
        jdbc.update("UPDATE oj_submission SET status='AC',ever_accepted=TRUE WHERE id=2");session.clearCache();
        job(4,10,null,1,"SUBMIT","WA",false);assertEquals("AC",state(10,null,1));
    }
    @Test void acceptedIsPermanentAcrossLaterFailuresAndRejudging() {
        job(1,10,7L,1,"SUBMIT","AC",true);
        job(2,10,7L,1,"SUBMIT","WA",false);
        job(3,10,7L,1,"SUBMIT","SYSTEM_ERROR",false);
        assertEquals("AC",state(10,7L,1));
        jdbc.update("UPDATE oj_submission SET status='QUEUED' WHERE id=1");session.clearCache();
        assertEquals("AC",state(10,7L,1));
        jdbc.update("UPDATE oj_submission SET status='WA' WHERE id=1");session.clearCache();
        assertEquals("AC",state(10,7L,1));
    }
    private OjService judgeService() {
        var problems=mock(OjProblemMapper.class);when(problems.selectList(any())).thenAnswer(ignored -> new ArrayList<>());
        return new OjService(problems,mock(OjLanguageMapper.class),mapper,new ObjectMapper(),mock(JudgeEngine.class),
            mock(GoJudgeClient.class),new JudgerProperties(),mock(OjRuntimeLogService.class),mock(JudgerNodes.class),OjTestSupport.snapshots(),OjTestSupport.activeUsers());
    }
    @Test void finishingAcceptedJobPersistsTheMarkerWithoutModifyingSavedSource() {
        job(1,10,null,1,"SUBMIT","RUNNING",null);
        var service=judgeService();
        try {
            var submission=mapper.selectById(1L);submission.setStatus("AC");
            ReflectionTestUtils.invokeMethod(service,"finish",submission);session.clearCache();
            assertTrue(mapper.selectById(1L).getEverAccepted());
            assertEquals("PRIVATE_CODE",mapper.selectById(1L).getCode());
            submission.setStatus("WA");submission.setEverAccepted(null);
            ReflectionTestUtils.invokeMethod(service,"finish",submission);session.clearCache();
            assertTrue(mapper.selectById(1L).getEverAccepted());assertEquals("AC",state(10,null,1));
        } finally {service.shutdown();}
    }
    @Test void startupBackfillsOnlyHistoricalFormalPassesAndIsIdempotent() {
        job(1,10,null,1,"SUBMIT","AC",null);job(2,10,7L,1,"SUBMIT","AC",false);
        job(3,10,null,2,"RUN","AC",null);job(4,10,null,3,"SUBMIT","WA",null);
        var service=judgeService();
        try {
            service.initialize();service.initialize();session.clearCache();
            assertTrue(mapper.selectById(1L).getEverAccepted());assertTrue(mapper.selectById(2L).getEverAccepted());
            assertNull(mapper.selectById(3L).getEverAccepted());assertNull(mapper.selectById(4L).getEverAccepted());
        } finally {service.shutdown();}
    }
    @Test void emptyOrHiddenProblemPageDoesNotQuerySubmissionHistory() {
        var unused=mock(OjSubmissionMapper.class);var service=new OjProgressService(unused);
        service.library(new LinkedHashMap<>(Map.of("records",List.of())),10L);
        service.problemSet(new LinkedHashMap<>(Map.of("items",List.of())),10L,7L);
        verifyNoInteractions(unused);
    }
}
