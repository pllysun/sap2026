package com.sap.config.judger;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;

/** Only the catalogue and personal progress tables; audit/statistics tables are untouched. */
@Component @RequiredArgsConstructor @DependsOn("entityManagerFactory")
public class OjCatalogIndexes {
    private final DataSource dataSource;
    @PostConstruct public void initialize() throws Exception {
        try(var connection=dataSource.getConnection()) {
            for(String[] index:new String[][] {
                {"oj_problem","idx_oj_catalog","status,validation_signature,sort_order,id DESC"},
                {"oj_submission","idx_oj_personal_progress","user_id,problem_set_id,kind,problem_id,id"}
            }) {
                boolean exists=false;
                try(var keys=connection.getMetaData().getIndexInfo(connection.getCatalog(),null,index[0],false,false)) {
                    while(keys.next())if(index[1].equalsIgnoreCase(keys.getString("INDEX_NAME"))){exists=true;break;}
                }
                if(!exists)try(var statement=connection.createStatement()) {
                    statement.execute("CREATE INDEX "+index[1]+" ON "+index[0]+"("+index[2]+")");
                }
            }
        }
    }
}
