package com.sap.service.judger;

import com.sap.config.judger.H2JudgeCompatibility;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OjH2CompatibilityTest {
    @Test void defaultDockerDatabaseSupportsSnapshotSignaturesAndJudgeMetrics() throws Exception {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:judgeJson;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try(var connection=ds.getConnection()){H2JudgeCompatibility.register(connection);H2JudgeCompatibility.register(connection);}
        var db=new JdbcTemplate(ds);
        assertEquals(128L,db.queryForObject("SELECT CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(?,'$.memoryBytes')),'0') AS UNSIGNED)",Long.class,"{\"memoryBytes\":128}"));
        assertEquals(0L,db.queryForObject("SELECT CAST(COALESCE(JSON_UNQUOTE(JSON_EXTRACT(?,'$.timeMs')),'0') AS UNSIGNED)",Long.class,"{}"));
        assertEquals("sig",db.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(?,'$.signature'))",String.class,"{\"signature\":\"sig\",\"packRef\":\"abc\"}"));
        assertEquals("a\n\"b",db.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(?,'$.signature'))",String.class,"{\"signature\":\"a\\n\\\"b\"}"));
    }
}
