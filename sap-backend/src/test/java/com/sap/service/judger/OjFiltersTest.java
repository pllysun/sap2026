package com.sap.service.judger;

import com.baomidou.mybatisplus.core.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sap.config.judger.*;
import com.sap.dto.judger.ProblemPack;
import com.sap.mapper.judger.*;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OjFiltersTest {
    private SqlSession session; private JdbcTemplate db; private OjProblemMapper problems; private OjService service;
    @BeforeEach void setup() throws Exception {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        try(var c=ds.getConnection()){H2JudgeCompatibility.register(c);}
        db=new JdbcTemplate(ds);db.execute("CREATE TABLE oj_problem(id BIGINT PRIMARY KEY,slug VARCHAR(100),title VARCHAR(200),difficulty VARCHAR(30),status VARCHAR(30),sort_order BIGINT,revision BIGINT,pack_json LONGTEXT,validation_signature VARCHAR(255),validation_job_id BIGINT,created_at TIMESTAMP,updated_at TIMESTAMP)");
        var config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);config.setEnvironment(new Environment("catalog",new JdbcTransactionFactory(),ds));config.addMapper(OjProblemMapper.class);
        session=new MybatisSqlSessionFactoryBuilder().build(config).openSession(true);problems=spy(session.getMapper(OjProblemMapper.class));
        var languages=mock(OjLanguageMapper.class);when(languages.selectList(any())).thenReturn(List.of());
        service=new OjService(problems,languages,mock(OjSubmissionMapper.class),new ObjectMapper(),mock(JudgeEngine.class),mock(GoJudgeClient.class),new JudgerProperties(),mock(OjRuntimeLogService.class),mock(JudgerNodes.class),OjTestSupport.snapshots(),OjTestSupport.activeUsers());
    }
    @AfterEach void close(){service.shutdown();session.close();}
    private void add(long id,String difficulty,String source,List<String> tags,List<String> modes,String status,boolean valid) {
        ProblemPack p=new ProblemPack();p.setTags(tags);p.setSourcePlatform(source);p.setModes(modes);p.setDescription("PRIVATE_STATEMENT");p.setConstraints("PRIVATE_TESTS_"+"X".repeat(100000));p.setReferences(Map.of("java",Map.of("STDIO","PRIVATE_SOURCE")));
        String sig=valid?ReflectionTestUtils.invokeMethod(service,"signature"):"old signature";
        db.update("INSERT INTO oj_problem(id,slug,title,difficulty,status,sort_order,revision,pack_json,validation_signature) VALUES(?,?,?,?,?,?,1,?,?)",id,"problem-"+id,"题目 "+id,difficulty,status,id,service.write(p),sig);session.clearCache();
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> rows(Map<String,Object> page){return (List<Map<String,Object>>)page.get("records");}
    @Test void combinedFiltersAreAppliedBeforePaginationAndPreserveSavedOrder() {
        add(1,"EASY","洛谷",List.of("数组"),List.of("STDIO"),"PUBLISHED",true);add(2,"EASY","LeetCode",List.of("数组","二分查找"),List.of("STDIO","FUNCTION"),"PUBLISHED",true);add(3,"MEDIUM","LeetCode",List.of("数组"),List.of("FUNCTION"),"PUBLISHED",true);add(4,"EASY","LeetCode",List.of("数组"),List.of("FUNCTION"),"PUBLISHED",true);
        var result=service.list(false,null,"EASY","数组","LeetCode","FUNCTION",2,1);assertEquals(2,result.get("total"));assertEquals(4L,((Number)rows(result).getFirst().get("id")).longValue());
        assertEquals(0,service.list(false,null,null,"数组前缀","LeetCode",null,1,20).get("total"));assertEquals(1,service.list(false,"题目 2",null,"数组","LeetCode","FUNCTION",1,20).get("total"));assertEquals(0,service.list(false,null,null,null,null,"INVALID",1,20).get("total"));assertEquals(0,service.list(false,null,null,null,"leetcode",null,1,20).get("total"));assertEquals(0,service.list(false,null,"easy",null,null,null,1,20).get("total"));verify(problems,never()).selectList(any());
    }
    @Test void filterOptionsAndRowsExcludeContestOnlyDisabledDraftAndStaleValidation() {
        add(1,"EASY","LeetCode",List.of("数组"),List.of("STDIO","FUNCTION"),"PUBLISHED",true);add(2,"EASY","LeetCode",List.of("数组","排序"),List.of("STDIO"),"PUBLISHED",true);add(3,"EASY","Private draft",List.of("DRAFT_TAG"),List.of("STDIO"),"DRAFT",true);add(4,"EASY","Disabled",List.of("DISABLED_TAG"),List.of("STDIO"),"DISABLED",true);add(5,"EASY","Old validation",List.of("STALE_TAG"),List.of("STDIO"),"PUBLISHED",false);add(6,"EASY","Private contest",List.of("CONTEST_TAG"),List.of("STDIO"),"CONTEST_ONLY",true);
        var filters=service.filters();assertEquals(Set.of("数组","排序"),new HashSet<>((Collection<?>)filters.get("tags")));assertEquals(Set.of("LeetCode"),new HashSet<>((Collection<?>)filters.get("sources")));assertEquals(Set.of("STDIO","FUNCTION"),new HashSet<>((Collection<?>)filters.get("modes")));
        var result=service.list(false,null,null,1,20);assertEquals(2,result.get("total"));assertFalse(service.write(result).contains("PRIVATE_"));assertEquals(List.of(1L,2L),service.visibleCatalogIds(List.of(1L,2L,3L,4L,5L,6L,999L)));verify(problems,never()).selectList(any());assertEquals(6,service.list(true,null,null,1,20).get("total"));
    }
    @Test void publicCacheDoesNotRetainPersonalProgressAndDisablingInvalidatesIt() {
        add(1,"EASY","LeetCode",List.of("数组"),List.of("STDIO"),"PUBLISHED",true);rows(service.list(false,null,null,1,20)).getFirst().put("progress",Map.of("state","AC"));
        var cached=rows(service.list(false,null,null,1,20)).getFirst();assertFalse(cached.containsKey("progress"));assertInstanceOf(Long.class,cached.get("id"));assertInstanceOf(Long.class,cached.get("sortOrder"));assertInstanceOf(Long.class,cached.get("revision"));
        service.filters();service.status(1L,"DISABLED");assertEquals(0,service.list(false,null,null,1,20).get("total"));assertTrue(((Collection<?>)service.filters().get("tags")).isEmpty());
    }
    @Test void searchTreatsWildcardsAndSqlTextLiterallyAndHandlesHugePages() {
        add(1,"EASY","LeetCode",List.of("数组"),List.of("STDIO"),"PUBLISHED",true);db.update("UPDATE oj_problem SET title=? WHERE id=1","100%_完成");session.clearCache();assertEquals(1,service.list(false,"%_",null,1,20).get("total"));assertEquals(0,service.list(false,"' OR 1=1 --",null,1,20).get("total"));var result=service.list(false,null,null,Integer.MAX_VALUE,50);assertEquals(1,result.get("total"));assertTrue(rows(result).isEmpty());
    }
    @Test void cachedPagesFollowReorderingImmediately() {
        add(1,"EASY","原创",List.of("数组"),List.of("STDIO"),"PUBLISHED",true);add(2,"EASY","原创",List.of("数组"),List.of("STDIO"),"PUBLISHED",true);assertEquals(1L,((Number)rows(service.list(false,null,null,1,20)).getFirst().get("id")).longValue());service.reorder(List.of(1L,2L),List.of(2L,1L));assertEquals(2L,((Number)rows(service.list(false,null,null,1,20)).getFirst().get("id")).longValue());
    }
}
