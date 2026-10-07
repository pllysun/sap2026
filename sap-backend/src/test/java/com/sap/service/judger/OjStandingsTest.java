package com.sap.service.judger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.config.judger.JudgerProperties;
import com.sap.entity.judger.*;
import com.sap.mapper.judger.*;
import com.sap.mapper.UserMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

class OjStandingsTest {
    static {var a=new org.apache.ibatis.builder.MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(),"");a.setCurrentNamespace("standings");com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(a,OjProblemSetItem.class);}
    @Test void realSqlKeepsAcmReceiptOrderAndUsesTwoQueriesFor1000Participants() throws Exception {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE");var db=spy(new JdbcTemplate(ds));
        ds.setURL(ds.getURL()+";DB_CLOSE_DELAY=-1");
        db.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY,name VARCHAR,nickname VARCHAR,student_id VARCHAR,status INT,deleted INT)");
        db.execute("CREATE TABLE oj_problem_set_participant(problem_set_id BIGINT,user_id BIGINT,disqualified BOOLEAN)");
        db.execute("CREATE TABLE oj_submission(id BIGINT,user_id BIGINT,problem_set_id BIGINT,problem_set_item_id BIGINT,revision BIGINT,validation_signature VARCHAR,mode VARCHAR,kind VARCHAR,status VARCHAR,accepted_at BIGINT)");
        long starts=1000000;
        for(long uid=1;uid<=1000;uid++) {
            db.update("INSERT INTO sys_user VALUES(?,?,?, ?,1,0)",uid,"User "+uid,"nick","S"+uid);db.update("INSERT INTO oj_problem_set_participant VALUES(1,?,FALSE)",uid);
            int order=0;for(String status:List.of("WA","CE","SYSTEM_ERROR","AC","WA","RUNNING"))db.update("INSERT INTO oj_submission VALUES(?,?,1,7,1,'sig','STDIO','SUBMIT',?,?)",uid*10+order++,uid,status,starts+uid*60000);
            // Wrong revision and non-formal runs never participate.
            db.update("INSERT INTO oj_submission VALUES(?,?,1,7,2,'sig','STDIO','SUBMIT','AC',?)",uid*10+8,uid,starts);db.update("INSERT INTO oj_submission VALUES(?,?,1,7,1,'sig','STDIO','RUN','AC',?)",uid*10+9,uid,starts);
        }
        var sets=mock(OjProblemSetMapper.class);var items=mock(OjProblemSetItemMapper.class);var users=mock(UserMapper.class);var oj=mock(OjService.class);
        var set=new OjProblemSet();set.setId(1L);set.setRevision(1L);set.setStatus("PUBLISHED");set.setMode("CONTEST");set.setStartsAt(starts);set.setEndsAt(starts+1001*60000);
        when(sets.selectById(1L)).thenReturn(set);var item=new OjProblemSetItem();item.setId(7L);item.setRevision(1L);item.setValidationSignature("sig");item.setModesJson("[\"STDIO\"]");item.setActive(true);item.setDifficulty("EASY");when(items.selectList(any())).thenReturn(List.of(item));
        when(oj.read(anyString(),eq(List.class))).thenAnswer(i->new ObjectMapper().readValue((String)i.getArgument(0),List.class));
        var service=new OjProblemSetService(sets,items,mock(OjProblemSetParticipantMapper.class),mock(OjProblemSetAuditMapper.class),mock(OjSubmissionMapper.class),users,oj,new OjContestScoringService(),new JudgerProperties(),db);
        assertEquals(6000,db.queryForObject("SELECT COUNT(*) FROM oj_submission j WHERE j.problem_set_id=? AND j.kind='SUBMIT' AND j.accepted_at IS NOT NULL AND (j.problem_set_item_id=? AND j.revision=? AND j.validation_signature=? AND j.mode IN (?)) AND j.accepted_at>=? AND j.accepted_at<?",Integer.class,1L,7L,1L,"sig","STDIO",set.getStartsAt(),set.getEndsAt()));
        clearInvocations(db);var result=service.standings(1L,1L,true,1,false);var rows=(List<Map<String,Object>>)result.get("records");
        assertEquals(1000,result.get("total"));assertEquals(50,rows.size());assertEquals(1,rows.getFirst().get("rank"));assertEquals(21L,rows.getFirst().get("penalty"),rows.getFirst().toString());
        var cell=((Map<Long,com.sap.vo.judger.ProblemSetScore.Cell>)rows.getFirst().get("cells")).get(7L);assertEquals(1,cell.wrong());assertEquals(1,cell.pending());assertTrue(cell.accepted());
        verify(db,times(2)).queryForList(anyString(),any(Object[].class));verifyNoInteractions(users);
        clearInvocations(db);service.standings(1L,1L,true,2,false);verifyNoInteractions(db);
        when(oj.activityVersion()).thenReturn(1L);service.standings(1L,1L,true,1,true);verify(db,times(2)).queryForList(anyString(),any(Object[].class));
    }
}
