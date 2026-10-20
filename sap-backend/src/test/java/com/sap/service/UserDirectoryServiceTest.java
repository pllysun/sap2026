package com.sap.service;

import com.sap.common.BusinessException;
import com.sap.entity.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** 真实 SQL 验证多角色、跨届任职、重复档案、筛选与稳定分页，避免内存先分页再过滤。 */
class UserDirectoryServiceTest {
    JdbcTemplate db;UserDirectoryService service;
    private static final UserDirectoryService.Filters ALL=new UserDirectoryService.Filters(null,null,null,null,"highest",null,null);

    @BeforeEach void setup() {
        var ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        db=new JdbcTemplate(ds);service=new UserDirectoryService(db);
        for(Class<?> type:List.of(User.class,UserRole.class,Term.class,Position.class)) {
            var columns=new ArrayList<String>();
            for(var field:type.getDeclaredFields()) {
                var annotation=field.getAnnotation(jakarta.persistence.Column.class);
                String name=annotation==null||annotation.name().isBlank()?field.getName():annotation.name();
                String sqlType=field.getType()==Long.class?"BIGINT":field.getType()==Integer.class?"INT":field.getType()==LocalDateTime.class?"TIMESTAMP":"VARCHAR(1000)";
                columns.add(name+" "+sqlType+(name.equals("deleted")?" DEFAULT 0":""));
            }
            db.execute("CREATE TABLE "+type.getAnnotation(jakarta.persistence.Table.class).name()+"("+String.join(",",columns)+")");
        }
        db.update("INSERT INTO sys_position(id,position_name,role_code,sort_order) VALUES(1,'会长',1,1),(2,'副会长',2,3),(3,'技术部长',2,6),(4,'宣传部长',2,7),(5,'成员',3,10),(6,'已删除职级',0,0)");
        db.update("UPDATE sys_position SET deleted=1 WHERE id=6");
        add(1,"admin","管理员","2024",1,1,0);
        add(2,"20260002","会长同学","2026",1,1,1,3,4);
        add(3,"20250003","技术同学","2025",1,1,2,3,4);
        add(4,"20260004","成员同学","2026",0,1,3,4);
        add(5,"20260005","游客同学","2026",1,1,4);
        add(6,"20250006","旧届游客","2025",0,0,4);
        add(7,"20260007","无权限同学","2026",null,1);
        add(8,"deleted","已删除用户","2026",1,1,0);
        add(9,"20260009","失效档案同学","2026",null,1,3);
        add(10,"super2","额外超管","2025",1,1,0,2);
        add(11,"test_100","同名同学","2026",1,1,3);
        add(12,"testX100","同名同学","2026",1,1,3);
        add(13,"20260013","未知权限","2026",null,1,99);
        db.update("UPDATE sys_user SET deleted=1 WHERE id=8");
        db.update("INSERT INTO sys_term(id,user_id,grade,position_id,deleted) VALUES"
            +"(1,1,'2026',1,0),(2,2,'2025',2,0),(3,2,'2026',1,0),(4,2,'2026',1,0),(5,2,'2027',5,0),"
            +"(6,3,'2025',2,0),(7,3,'2026',3,0),(8,3,'2026',4,0),(9,3,'2027',5,0),(10,3,'2028',1,1),"
            +"(11,4,'2025',2,0),(12,4,'2026',5,0),(13,8,'2099',1,0),(14,9,'2030',6,0)");
    }

    private void add(long id,String account,String name,String grade,Integer gender,int status,int... roles) {
        db.update("INSERT INTO sys_user(id,student_id,name,nickname,qq,grade,gender,status,password,created_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
            id,account,name,"昵称"+id,"1000000"+id,grade,gender,status,"NEVER_EXPOSE_CREDENTIAL",LocalDateTime.of(2026,10,1,12,0));
        for(int role:roles)db.update("INSERT INTO sys_user_role(id,user_id,role_code) VALUES(?,?,?)",id*100+role,id,role);
    }

    @SuppressWarnings("unchecked") private List<Map<String,Object>> rows(Map<String,Object> page) { return (List<Map<String,Object>>)page.get("records"); }
    private Map<String,Object> row(long id) { return rows(service.list(1,200,ALL)).stream().filter(r->((Number)r.get("id")).longValue()==id).findFirst().orElseThrow(); }
    private Set<Long> ids(UserDirectoryService.Filters filters) { return new HashSet<>(rows(service.list(1,200,filters)).stream().map(r->((Number)r.get("id")).longValue()).toList()); }
    private UserDirectoryService.Filters permission(int code,String match) { return new UserDirectoryService.Filters(null,null,null,code,match,null,null); }

    @Test void picksHighestPositionBeforeLatestYearAndBreaksEqualLevelTiesDeterministically() {
        assertEquals("2026届 · 会长",row(2).get("positionLabel"));
        assertEquals("2026届 · 技术部长",row(3).get("positionLabel"));
        assertEquals("2025届 · 副会长",row(4).get("positionLabel"));
        assertEquals(3,row(4).get("highestRoleCode"));
        assertEquals(2,row(4).get("positionRoleCode"));
        assertEquals(0,row(1).get("highestRoleCode"));
        assertEquals("会长",row(1).get("positionName"));
        assertEquals(12L,service.list(1,20,ALL).get("total"));
    }

    @Test void guestsAndAccountsWithoutAssignedRolesUseHonestFallbacks() {
        assertEquals("2026届 · 游客",row(5).get("positionLabel"));assertEquals("permission",row(5).get("positionSource"));
        assertNull(row(7).get("highestRoleCode"));assertEquals("未分配权限",row(7).get("highestRoleName"));
        assertNull(row(13).get("highestRoleCode"));
        assertEquals("成员",row(9).get("positionName"));assertEquals("2026",row(9).get("positionGrade"));
    }

    @Test void everyPermissionLevelMatchesCurrentHighestRatherThanAnyLowerRole() {
        assertEquals(Set.of(1L,10L),ids(permission(0,"highest")));
        assertEquals(Set.of(2L),ids(permission(1,"highest")));
        assertEquals(Set.of(3L),ids(permission(2,"highest")));
        assertEquals(Set.of(4L,9L,11L,12L),ids(permission(3,"highest")));
        assertEquals(Set.of(5L,6L),ids(permission(4,"highest")));
        assertEquals(Set.of(7L,13L),ids(permission(-1,"highest")));
        assertEquals(Set.of(2L,3L,4L,9L,11L,12L),ids(permission(3,"contains")));
        assertEquals(Set.of(3L,10L),ids(permission(2,"contains")));
    }

    @Test void gradeScopeChoosesThatYearsHighestAppointmentAndIncludesRegisteredGuests() {
        var scoped=new UserDirectoryService.Filters(null,"2027",null,null,"highest",null,null);
        var pageScope=service.list(1,20,scoped);var rows=rows(pageScope);assertEquals(2,rows.size(),pageScope.toString());
        assertTrue(rows.stream().allMatch(r->"2027届 · 成员".equals(r.get("positionLabel"))));
        var guests=new UserDirectoryService.Filters(null,"2026",null,4,"highest",null,null);
        assertEquals(Set.of(5L),ids(guests));
        var historic=new UserDirectoryService.Filters("技术","2025","position:2",2,"highest",1,1);
        var page=service.list(1,20,historic);assertEquals(1L,page.get("total"));assertEquals("2025届 · 副会长",rows(page).getFirst().get("positionLabel"));
        assertEquals(Set.of(3L),ids(new UserDirectoryService.Filters(null,null,"position:3",null,"highest",null,null)));
        assertEquals(Set.of(5L,6L),ids(new UserDirectoryService.Filters(null,null,"role:4",null,"highest",null,null)));
    }

    @Test void genderStatusAndLiteralKeywordFiltersCombineBeforePagination() {
        assertEquals(Set.of(6L),ids(new UserDirectoryService.Filters(null,null,null,4,"highest",0,0)));
        assertEquals(Set.of(7L,9L,13L),ids(new UserDirectoryService.Filters(null,null,null,null,"highest",-1,null)));
        assertEquals(Set.of(11L),ids(new UserDirectoryService.Filters("test_100",null,null,null,"highest",null,null)));
        assertEquals(Set.of(3L),ids(new UserDirectoryService.Filters("10000003",null,null,null,"highest",null,null)));
        assertEquals(Set.of(11L,12L),ids(new UserDirectoryService.Filters("同名同学",null,null,null,"highest",null,null)));
        assertTrue(ids(new UserDirectoryService.Filters("%' OR 1=1 --",null,null,null,"highest",null,null)).isEmpty());
    }

    @Test void stableUniquePagesExcludeDeletedRowsAndNeverReturnCredentials() {
        var first=service.list(1,5,ALL);var second=service.list(2,5,ALL);
        var ids=new ArrayList<>(rows(first).stream().map(r->r.get("id")).toList());ids.addAll(rows(second).stream().map(r->r.get("id")).toList());
        assertEquals(10,new HashSet<>(ids).size());assertEquals(12L,first.get("total"));
        assertEquals(200,service.list(0,100000,ALL).get("size"));
        assertTrue(rows(service.list(Integer.MAX_VALUE,20,ALL)).isEmpty());
        String json=com.alibaba.fastjson2.JSON.toJSONString(service.list(1,200,ALL));
        assertFalse(json.toLowerCase().contains("password"));assertFalse(json.contains("NEVER_EXPOSE_CREDENTIAL"));assertFalse(json.contains("已删除用户"));
    }

    @SuppressWarnings("unchecked") @Test void optionsIncludeLiveGradesPositionsAndAllFallbackLevelsWithoutDeletedData() {
        var options=service.options();var grades=(List<String>)options.get("grades");
        assertEquals(List.of("2027","2026","2025","2024"),grades);
        var positions=(List<Map<String,Object>>)options.get("positions");
        for(int code=0;code<=4;code++){String value="role:"+code;assertTrue(positions.stream().anyMatch(p->value.equals(p.get("value"))));}
        assertFalse(positions.stream().anyMatch(p->"position:6".equals(p.get("value"))));
    }

    @Test void invalidFilterValuesCannotTurnIntoSqlOrUnexpectedPermissionMatches() {
        for(var filter:List.of(new UserDirectoryService.Filters(null,null,"position:1 OR 1=1",null,"highest",null,null),
            permission(5,"highest"),permission(2,"bogus"),new UserDirectoryService.Filters(null,"x".repeat(11),null,null,"highest",null,null),
            new UserDirectoryService.Filters(null,null,null,null,"highest",2,null),new UserDirectoryService.Filters(null,null,null,null,"highest",null,2))) {
            assertEquals(400,assertThrows(BusinessException.class,()->service.list(1,20,filter)).getCode());
        }
    }
}
