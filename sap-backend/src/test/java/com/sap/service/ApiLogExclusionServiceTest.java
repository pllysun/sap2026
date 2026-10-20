package com.sap.service;

import com.sap.common.BusinessException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiLogExclusionServiceTest {
    JdbcTemplate db;DataSourceTransactionManager manager;ApiLogExclusionService service;
    @BeforeEach void setup(){var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");db=spy(new JdbcTemplate(ds));manager=new DataSourceTransactionManager(ds);service=new ApiLogExclusionService(db,manager);service.initialize();}
    @Test void defaultsMatchOnlyWholeEndpointsAndUseNoDatabasePerRequest(){
        clearInvocations(db);
        assertTrue(service.excludes("/api/ping","GET"));assertTrue(service.excludes("/api/oj-nodes/worker-1/heartbeat","POST"));
        assertTrue(service.excludes("/api/oj-nodes/{id}/heartbeat","POST"));
        assertFalse(service.excludes("/api/ping-extra","GET"));assertFalse(service.excludes("/api/ping/details","GET"));
        assertFalse(service.excludes("/api/oj-nodes/a/b/heartbeat","POST"));
        var request=new MockHttpServletRequest("POST","/sap/api/oj-nodes/23/heartbeat");request.setContextPath("/sap");assertTrue(service.excludes(request));
        for(int i=0;i<1000;i++)assertTrue(service.excludes("/api/ping","GET"));verifyNoInteractions(db);
    }
    @Test void removingRulesPersistsAcrossRestartAndRestoresRecording(){
        service.replace(List.of());assertFalse(service.excludes("/api/ping","GET"));
        var restart=new ApiLogExclusionService(db,manager);restart.initialize();assertTrue(restart.rules().isEmpty());assertFalse(restart.excludes("/api/ping","GET"));
        restart.replace(List.of(new ApiLogExclusionService.Rule("/api/task/{id}","post")));
        assertTrue(restart.excludes("/api/task/42","POST"));assertFalse(restart.excludes("/api/task/42","GET"));
    }
    @Test void invalidOrOversizedUpdatesLeaveBothDatabaseAndCacheUntouched(){
        var original=service.rules();
        for(String invalid:List.of("/api/**","/api/../auth","/api/ping?x=1","https://example.test/api/ping","/api/(.*)","/api/ping' OR 1=1"))
            assertThrows(BusinessException.class,()->service.replace(List.of(new ApiLogExclusionService.Rule(invalid,"*"))));
        assertThrows(BusinessException.class,()->service.replace(Collections.nCopies(51,new ApiLogExclusionService.Rule("/api/a","GET"))));
        assertThrows(BusinessException.class,()->service.replace(Collections.nCopies(2,new ApiLogExclusionService.Rule("/api/a","GET"))));
        assertEquals(original,service.rules());var restart=new ApiLogExclusionService(db,manager);restart.initialize();assertEquals(original,restart.rules());
    }
    @Test void sqlAndMemoryAgreeForPathsAndMethodsIncludingCase(){
        service.replace(List.of(new ApiLogExclusionService.Rule("/api/task/{id}","POST"),new ApiLogExclusionService.Rule("/api/ping","*")));
        db.execute("CREATE TABLE sample(endpoint VARCHAR(255),http_method VARCHAR(10))");
        for(String path:List.of("/api/task/1","/api/task/{id}","/api/task/1/child","/api/Task/1","/api/ping","/api/ping/x"))for(String method:List.of("GET","POST"))db.update("INSERT INTO sample VALUES(?,?)",path,method);
        var sql=service.sql("endpoint","http_method");
        var visible=db.queryForList("SELECT * FROM sample WHERE 1=1"+sql.text(),sql.args().toArray());
        assertTrue(visible.stream().noneMatch(row->service.excludes((String)row.get("endpoint"),(String)row.get("http_method"))));
        assertEquals(12-db.queryForList("SELECT * FROM sample").stream().filter(row->service.excludes((String)row.get("endpoint"),(String)row.get("http_method"))).count(),visible.size());
    }
}
