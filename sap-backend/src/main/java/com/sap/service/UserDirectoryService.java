package com.sap.service;

import com.sap.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/** 平台用户目录。任职用于展示，账号实际权限始终来自 sys_user_role，不由历史档案授予权限。 */
@Service
public class UserDirectoryService {
    private final JdbcTemplate db;

    public UserDirectoryService(JdbcTemplate db) { this.db=db; }

    public record Filters(String keyword,String grade,String position,Integer roleCode,String roleMatch,
                          Integer gender,Integer status) {}

    private static final List<String> ROLE_NAMES=List.of("超级管理员","会长","管理员","成员","游客");

    private record Query(String sql,List<Object> args) {}

    public Map<String,Object> list(int current,int size,Filters filters) {
        current=Math.max(1,current);size=Math.max(1,Math.min(200,size));
        Query query=query(filters);
        Long count=db.queryForObject("SELECT COUNT(*)"+query.sql+where(filters),Long.class,arguments(query,filters).toArray());
        long total=count==null?0:count;
        var args=arguments(query,filters);args.add(size);args.add((long)(current-1)*size);
        var records=db.queryForList("SELECT d.*"+query.sql+where(filters)+" ORDER BY d.createdAt DESC,d.id DESC LIMIT ? OFFSET ?",args.toArray());
        for(var row:records) {
            String grade=Objects.toString(row.get("positionGrade"),"");
            row.put("positionLabel",(grade.isBlank()?"未记录届别 · ":grade+"届 · ")+row.get("positionName"));
            Object code=row.get("highestRoleCode");
            row.put("highestRoleName",code instanceof Number n?ROLE_NAMES.get(n.intValue()):"未分配权限");
        }
        return Map.of("records",records,"current",current,"size",size,"total",total);
    }

    public Map<String,Object> options() {
        var grades=db.queryForList("""
            SELECT grade FROM (
                SELECT grade FROM sys_user WHERE deleted=0
                UNION
                SELECT t.grade FROM sys_term t JOIN sys_user u ON u.id=t.user_id AND u.deleted=0
                    JOIN sys_position p ON p.id=t.position_id AND p.deleted=0 WHERE t.deleted=0
            ) grades WHERE grade IS NOT NULL AND TRIM(grade)<>'' ORDER BY grade DESC
            """,String.class);
        var positions=db.queryForList("SELECT id,position_name AS name,role_code AS roleCode FROM sys_position WHERE deleted=0 AND role_code BETWEEN 0 AND 4 ORDER BY role_code,COALESCE(sort_order,999),id");
        var values=new ArrayList<Map<String,Object>>();
        for(var position:positions)values.add(Map.of("value","position:"+position.get("id"),"label",position.get("name"),"roleCode",position.get("roleCode")));
        // 没有对应任职档案时按真实权限展示；游客通常只存在注册账号，不存在任职记录。
        for(int code=0;code<=4;code++)values.add(Map.of("value","role:"+code,"label",ROLE_NAMES.get(code)+"（无对应任职）","roleCode",code));
        values.add(Map.of("value","unassigned","label","未分配权限","roleCode",-1));
        return Map.of("grades",grades,"positions",values);
    }

    private Query query(Filters filters) {
        validate(filters);
        String grade=trim(filters.grade());
        var args=new ArrayList<Object>();
        String termFilter="",userFilter="";
        if(grade!=null) {
            termFilter=" AND t.grade=?";args.add(grade);
            userFilter=" AND (t.user_id IS NOT NULL OR u.grade=?)";args.add(grade);
        }
        // 同级优先最近一届；同届才按职位排序号及稳定 ID 破同分。不跨页查询，不逐人查库。
        String terms="""
                SELECT t.user_id,t.grade,p.id AS position_id,p.position_name,p.role_code,
                    ROW_NUMBER() OVER (PARTITION BY t.user_id ORDER BY p.role_code,t.grade DESC,
                        COALESCE(p.sort_order,999),p.id,t.id DESC) AS rn
                FROM sys_term t JOIN sys_position p ON p.id=t.position_id AND p.deleted=0
                WHERE t.deleted=0 AND p.role_code BETWEEN 0 AND 4
            """+termFilter;
        String chosen="""
                SELECT u.id,u.student_id AS studentId,u.name,u.nickname,u.gender,u.qq,u.grade,u.avatar,u.status,
                    u.created_at AS createdAt,u.updated_at AS updatedAt,r.role_code AS highestRoleCode,
                    t.grade AS termGrade,t.position_id,t.position_name,t.role_code AS positionRoleCode,
                    CASE WHEN t.user_id IS NOT NULL THEN 1 ELSE 0 END AS useTerm
                FROM sys_user u LEFT JOIN (
                    SELECT user_id,MIN(role_code) AS role_code FROM sys_user_role
                    WHERE role_code BETWEEN 0 AND 4 GROUP BY user_id
                ) r ON r.user_id=u.id LEFT JOIN (
            """+terms+"""
                ) t ON t.user_id=u.id AND t.rn=1
                WHERE u.deleted=0
            """+userFilter;
        String directory="""
                SELECT id,studentId,name,nickname,gender,qq,grade,avatar,status,createdAt,updatedAt,highestRoleCode,
                    COALESCE(termGrade,grade) AS positionGrade,
                    CASE WHEN useTerm=1 THEN position_id ELSE NULL END AS positionId,
                    CASE WHEN useTerm=1 THEN positionRoleCode ELSE highestRoleCode END AS positionRoleCode,
                    CASE WHEN useTerm=1 THEN position_name ELSE CASE highestRoleCode
                        WHEN 0 THEN '超级管理员' WHEN 1 THEN '会长' WHEN 2 THEN '管理员'
                        WHEN 3 THEN '成员' WHEN 4 THEN '游客' ELSE '未分配权限' END END AS positionName,
                    CASE WHEN useTerm=1 THEN 'term' ELSE 'permission' END AS positionSource
                FROM (
            """+chosen+") chosen";
        return new Query(" FROM ("+directory+") d",args);
    }

    private String where(Filters filters) {
        StringBuilder sql=new StringBuilder(" WHERE 1=1");
        if(trim(filters.keyword())!=null)sql.append(" AND (d.name LIKE ? ESCAPE '!' OR d.studentId LIKE ? ESCAPE '!' OR d.qq LIKE ? ESCAPE '!' OR d.nickname LIKE ? ESCAPE '!')");
        String position=trim(filters.position());
        if(position!=null) {
            if(position.startsWith("position:"))sql.append(" AND d.positionId=?");
            else if(position.startsWith("role:"))sql.append(" AND d.positionSource='permission' AND d.highestRoleCode=?");
            else sql.append(" AND d.positionSource='permission' AND d.highestRoleCode IS NULL");
        }
        if(filters.roleCode()!=null) {
            if(filters.roleCode()==-1)sql.append(" AND d.highestRoleCode IS NULL");
            else if("contains".equals(filters.roleMatch()))sql.append(" AND EXISTS (SELECT 1 FROM sys_user_role r WHERE r.user_id=d.id AND r.role_code=?)");
            else sql.append(" AND d.highestRoleCode=?");
        }
        if(filters.gender()!=null)sql.append(filters.gender()==-1?" AND (d.gender IS NULL OR d.gender NOT IN (0,1))":" AND d.gender=?");
        if(filters.status()!=null)sql.append(" AND d.status=?");
        return sql.toString();
    }

    private ArrayList<Object> arguments(Query query,Filters filters) {
        var args=new ArrayList<>(query.args);
        String keyword=trim(filters.keyword());
        if(keyword!=null) {
            String value="%"+keyword.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            for(int i=0;i<4;i++)args.add(value);
        }
        String position=trim(filters.position());
        if(position!=null && position.startsWith("position:"))args.add(Long.parseLong(position.substring(9)));
        else if(position!=null && position.startsWith("role:"))args.add(Integer.parseInt(position.substring(5)));
        if(filters.roleCode()!=null && filters.roleCode()!=-1)args.add(filters.roleCode());
        if(filters.gender()!=null && filters.gender()!=-1)args.add(filters.gender());
        if(filters.status()!=null)args.add(filters.status());
        return args;
    }

    private static String trim(String value) { return value==null || value.isBlank()?null:value.trim(); }

    private static void validate(Filters filters) {
        if(filters==null)throw new BusinessException(400,"缺少筛选条件");
        if(filters.keyword()!=null && filters.keyword().length()>50)throw new BusinessException(400,"搜索内容不能超过 50 个字符");
        String grade=trim(filters.grade()),position=trim(filters.position());
        if(grade!=null && grade.length()>10)throw new BusinessException(400,"届别格式无效");
        if(position!=null && !position.matches("position:[1-9][0-9]{0,8}|role:[0-4]|unassigned"))throw new BusinessException(400,"职级筛选无效");
        if(filters.roleCode()!=null && (filters.roleCode()<-1 || filters.roleCode()>4))throw new BusinessException(400,"权限等级须为 0–4");
        if(filters.roleMatch()!=null && !Set.of("highest","contains").contains(filters.roleMatch()))throw new BusinessException(400,"权限匹配方式无效");
        if(filters.gender()!=null && (filters.gender()<-1 || filters.gender()>1))throw new BusinessException(400,"性别筛选无效");
        if(filters.status()!=null && filters.status()!=0 && filters.status()!=1)throw new BusinessException(400,"账号状态无效");
    }
}
