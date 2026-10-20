package io.sapoj.service.judger;

import com.baomidou.mybatisplus.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapoj.common.BusinessException;
import io.sapoj.dto.judger.LearningAction;
import io.sapoj.entity.judger.OjSubmission;
import io.sapoj.mapper.judger.*;
import jakarta.validation.Validation;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OjLearningServiceTest {
    private JdbcTemplate db;
    private SqlSession session;
    private OjLearningService learning;
    private OjService oj;
    private OjSubmissionMapper submissions;
    private OjLearningProfileMapper profiles;
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();

    @BeforeEach void setup(){
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");db=new JdbcTemplate(ds);
        var columns=new ArrayList<String>();
        for(var field:OjSubmission.class.getDeclaredFields()){
            var annotation=field.getAnnotation(jakarta.persistence.Column.class);String name=annotation==null?field.getName():annotation.name();
            String type=field.getType()==Long.class?"BIGINT":field.getType()==Integer.class?"INT":field.getType()==Boolean.class?"BOOLEAN":field.getType()==LocalDateTime.class?"TIMESTAMP":"LONGTEXT";
            columns.add(name+" "+type+(name.equals("id")?" PRIMARY KEY":""));
        }
        db.execute("CREATE TABLE oj_submission("+String.join(",",columns)+")");
        db.execute("CREATE TABLE oj_learning_profile(user_id BIGINT PRIMARY KEY,revision BIGINT NOT NULL,state_json LONGTEXT NOT NULL,updated_at TIMESTAMP NOT NULL)");
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.setEnvironment(new Environment("learning",new JdbcTransactionFactory(),ds));
        config.addMapper(OjSubmissionMapper.class);config.addMapper(OjLearningProfileMapper.class);
        session=new MybatisSqlSessionFactoryBuilder().build(config).openSession(true);submissions=session.getMapper(OjSubmissionMapper.class);profiles=session.getMapper(OjLearningProfileMapper.class);
        oj=mock(OjService.class);
        when(oj.learningCatalog()).thenAnswer(ignored->{var rows=new ArrayList<Map<String,Object>>();for(long id:List.of(1L,2L)){var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("title","题目"+id);row.put("tags",List.of("数组"));row.put("sortOrder",id);row.put("status","PUBLISHED");rows.add(row);}return Map.of("records",rows,"total",2);});
        when(oj.visibleCatalogIds(anyList())).thenAnswer(call->((List<Long>)call.getArgument(0)).stream().filter(id->id!=null&&(id==1||id==2)).toList());
        learning=new OjLearningService(oj,new OjProgressService(submissions),submissions,profiles,json);
    }
    @AfterEach void close(){session.close();}
    private LearningAction action(long version,String type){var a=new LearningAction();a.setVersion(version);a.setAction(type);return a;}
    private void job(long id,long user,Long set,long problem,String kind,String status,boolean accepted,String date){
        db.update("INSERT INTO oj_submission(id,user_id,problem_set_id,problem_id,kind,status,ever_accepted,created_at,code,snapshot_json,result_json) VALUES(?,?,?,?,?,?,?,?,'PRIVATE_CODE','HIDDEN_TESTS','PRIVATE_RESULT')",id,user,set,problem,kind,status,accepted,LocalDateTime.parse(date));session.clearCache();
    }
    @Test void stateIsPrivatePerAccountAndConcurrentUpdatesCannotOverwrite(){
        var note=action(0,"NOTE");note.setProblemId(1L);note.setText("我的私人思路");learning.update(10L,note);session.clearCache();
        assertTrue(learning.overview(10L).toString().contains("我的私人思路"));assertFalse(learning.overview(11L).toString().contains("我的私人思路"));
        var stale=action(0,"TRACK");stale.setTrackId("icpc");assertEquals(409,assertThrows(BusinessException.class,()->learning.update(10L,stale)).getCode());
        var valid=action(1,"TRACK");valid.setTrackId("structure");learning.update(10L,valid);session.clearCache();assertEquals(2L,profiles.selectById(10L).getRevision());
    }
    @Test void beginnerTracksReplaceLegacyDirectionsWithoutLosingPrivateWork() throws Exception {
        assertEquals("starter",((OjLearningState)learning.overview(10L).get("state")).getTrackId());
        for(var entry:Map.of("lanqiao","starter","gplt","sequence","icpc","explore","noip","structure","nowcoder","methods").entrySet()) {
            var old=new OjLearningState();old.setTrackId(entry.getKey());old.getNotes().put("1","保留我的笔记");old.getBookmarks().add(1L);
            old.setPlan(new OjLearningState.Plan("保留计划",2,List.of(0),List.of(1L,2L),"2026-10-10"));
            var profile=new io.sapoj.entity.judger.OjLearningProfile();profile.setUserId(10L);profile.setRevision(3L);profile.setStateJson(json.writeValueAsString(old));profile.setUpdatedAt(LocalDateTime.now());
            profiles.deleteById(10L);profiles.insert(profile);session.clearCache();
            var migrated=(OjLearningState)learning.overview(10L).get("state");assertEquals(entry.getValue(),migrated.getTrackId());assertEquals(old.getNotes(),migrated.getNotes());assertEquals(old.getPlan(),migrated.getPlan());assertEquals(old.getBookmarks(),migrated.getBookmarks());
            // Reads normalize the returned view without silently rewriting persisted data.
            assertTrue(profiles.selectById(10L).getStateJson().contains(entry.getKey()));assertEquals(3L,profiles.selectById(10L).getRevision());
            var note=action(3,"NOTE");note.setProblemId(2L);note.setText("新增笔记");learning.update(10L,note);session.clearCache();
            var saved=json.readValue(profiles.selectById(10L).getStateJson(),OjLearningState.class);assertEquals(entry.getValue(),saved.getTrackId());assertEquals("保留我的笔记",saved.getNotes().get("1"));assertEquals(old.getPlan(),saved.getPlan());
        }
        var bad=action(4,"TRACK");bad.setTrackId("unknown");assertThrows(BusinessException.class,()->learning.update(10L,bad));
    }
    @Test void newlyHiddenProblemsAreExcludedFromNotesBookmarksReviewsAndPlans(){
        var state=new OjLearningState();state.getNotes().put("9","HIDDEN_NOTE");state.getBookmarks().add(9L);state.getReviews().put("9",new OjLearningState.Review(1,"2026-10-10"));
        state.setPlan(new OjLearningState.Plan("Plan",2,List.of(),List.of(1L,9L),"2026-10-10"));
        var profile=new io.sapoj.entity.judger.OjLearningProfile();profile.setUserId(10L);profile.setRevision(1L);profile.setUpdatedAt(LocalDateTime.now());
        try{profile.setStateJson(json.writeValueAsString(state));}catch(Exception e){throw new RuntimeException(e);}profiles.insert(profile);session.clearCache();
        var view=learning.overview(10L);assertFalse(view.toString().contains("HIDDEN_NOTE"));var visible=(OjLearningState)view.get("state");assertTrue(visible.getBookmarks().isEmpty());assertTrue(visible.getReviews().isEmpty());assertEquals(List.of(1L),visible.getPlan().problemIds());
        var note=action(1,"NOTE");note.setProblemId(9L);note.setText("hidden");assertEquals(404,assertThrows(BusinessException.class,()->learning.update(10L,note)).getCode());
    }
    @SuppressWarnings("unchecked")
    @Test void aggregateReadsOnlyFormalLibraryWorkAndNeverSourceOrSnapshots(){
        job(1,10,null,1,"SUBMIT","WA",false,"2026-10-01T09:00:00");job(2,10,null,1,"SUBMIT","AC",true,"2026-10-01T10:00:00");
        job(3,10,7L,1,"SUBMIT","AC",true,"2026-10-02T10:00:00");job(4,10,null,2,"RUN","AC",true,"2026-10-03T10:00:00");job(5,11,null,2,"SUBMIT","AC",true,"2026-10-03T10:00:00");
        job(6,10,null,9,"SUBMIT","AC",true,"2026-10-03T10:00:00");job(7,10,null,1,"SUBMIT","SYSTEM_ERROR",false,"2026-10-03T10:00:00");
        var stats=submissions.learningStats(10L,List.of(1L,2L));assertEquals(1,stats.size());assertEquals(2,stats.getFirst().getAttempts());assertEquals(1,stats.getFirst().getAcAttempts());assertEquals(LocalDateTime.parse("2026-10-01T10:00:00"),stats.getFirst().getFirstAcceptedAt());
        var view=learning.overview(10L);assertFalse(view.toString().contains("PRIVATE_"));assertFalse(view.toString().contains("HIDDEN_TESTS"));
        var rows=(List<Map<String,Object>>)view.get("problems");assertEquals("AC",((Map<?,?>)rows.getFirst().get("progress")).get("state"));assertEquals("NONE",((Map<?,?>)rows.get(1).get("progress")).get("state"));assertFalse(rows.getFirst().containsKey("sortOrder"));
        var days=submissions.learningDays(10L,List.of(1L,2L),LocalDateTime.parse("2026-09-01T00:00:00"));assertEquals(1,days.size());assertEquals(2L,((Number)days.getFirst().get("attempts")).longValue());
    }
    @Test void reviewRequiresRealFormalAcAndFeedbackReschedulesFromTheServerDate(){
        job(1,10,null,1,"RUN","AC",true,"2026-10-01T10:00:00");var review=action(0,"REVIEW");review.setProblemId(1L);review.setEase("ok");assertThrows(BusinessException.class,()->learning.update(10L,review));
        job(2,10,null,1,"SUBMIT","AC",true,"2026-10-01T11:00:00");learning.update(10L,review);session.clearCache();
        var state=(OjLearningState)learning.overview(10L).get("state");assertEquals(2,state.getReviews().get("1").stage());assertEquals(LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(3).toString(),state.getReviews().get("1").due());
        assertEquals(1,OjLearningService.nextStage(1,"hard"));assertEquals(4,OjLearningService.nextStage(2,"easy"));assertEquals(6,OjLearningService.nextStage(6,"ok"));
    }
    @Test void noteDeletionAndPlanValidationPreserveOtherData(){
        var note=action(0,"NOTE");note.setProblemId(1L);note.setText("Keep");learning.update(10L,note);session.clearCache();
        var plan=action(1,"PLAN");plan.setDailyTarget(2);plan.setRestDays(List.of(0,6));plan.setProblemIds(List.of(1L,1L));assertThrows(BusinessException.class,()->learning.update(10L,plan));
        plan.setProblemIds(List.of(1L,9L));assertThrows(BusinessException.class,()->learning.update(10L,plan));plan.setProblemIds(List.of(1L,2L));learning.update(10L,plan);session.clearCache();
        note.setVersion(2L);note.setText(" ");learning.update(10L,note);session.clearCache();var state=(OjLearningState)learning.overview(10L).get("state");assertTrue(state.getNotes().isEmpty());assertEquals(List.of(1L,2L),state.getPlan().problemIds());
    }
    @Test void dtoBoundsRejectUnboundedInputs(){
        try(var factory=Validation.buildDefaultValidatorFactory()){
            var validator=factory.getValidator();var action=action(0,"NOTE");action.setText("x".repeat(6001));assertFalse(validator.validate(action).isEmpty());
            action.setText("ok");action.setDailyTarget(11);assertFalse(validator.validate(action).isEmpty());action.setDailyTarget(2);action.setRestDays(List.of(0,1,2,3,4,5,6));assertFalse(validator.validate(action).isEmpty());
            action.setRestDays(Arrays.asList(0,null));assertFalse(validator.validate(action).isEmpty());action.setRestDays(List.of());
            action.setProblemIds(Arrays.asList(1L,null));assertFalse(validator.validate(action).isEmpty());
        }
    }
    @Test void disabledAccountsAreRejectedBeforeAnyMetadataOrStateRead(){
        doThrow(new BusinessException(403,"账号不可用")).when(oj).requireActiveAccount(10L);
        assertEquals(403,assertThrows(BusinessException.class,()->learning.overview(10L)).getCode());verify(oj,never()).learningCatalog();assertThrows(BusinessException.class,()->learning.update(10L,action(0,"PLAN_CLEAR")));
    }
}
