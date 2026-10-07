package com.sap.config.judger;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.Connection;

@Component @RequiredArgsConstructor
public class H2JudgeCompatibility {
    private final DataSource dataSource;
    @PostConstruct public void initialize() throws Exception {
        try(Connection connection=dataSource.getConnection()) {register(connection);}
    }
    public static void register(Connection connection) throws Exception {
        if(!"H2".equals(connection.getMetaData().getDatabaseProductName()))return;
        try(var statement=connection.createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS JSON_EXTRACT FOR 'com.sap.util.judger.H2JsonFunctions.extract'");
            statement.execute("CREATE ALIAS IF NOT EXISTS JSON_UNQUOTE FOR 'com.sap.util.judger.H2JsonFunctions.unquote'");
            statement.execute("CREATE ALIAS IF NOT EXISTS JSON_CONTAINS FOR 'com.sap.util.judger.H2JsonFunctions.contains'");
            statement.execute("CREATE DOMAIN IF NOT EXISTS UNSIGNED AS BIGINT");
        }
    }
}
