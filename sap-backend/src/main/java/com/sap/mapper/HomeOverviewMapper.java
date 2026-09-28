package com.sap.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

/** 首页仅提供汇总数据，不复用含财务信息的管理端仪表盘。 */
@Mapper
public interface HomeOverviewMapper {
    @Select("""
        SELECT COUNT(*) FROM sys_user u WHERE u.deleted = 0
        """)
    long registeredCount();

    @Select("""
        SELECT COUNT(*) FROM sys_user u
        WHERE u.deleted = 0 AND u.status = 1 AND EXISTS (
            SELECT 1 FROM sys_user_role r WHERE r.user_id = u.id AND r.role_code BETWEEN 0 AND 3)
        """)
    long memberCount();

    @Select("""
        SELECT u.grade AS grade, COUNT(*) AS count FROM sys_user u
        WHERE u.deleted = 0 AND u.status = 1 AND u.grade IS NOT NULL AND TRIM(u.grade) <> ''
          AND EXISTS (SELECT 1 FROM sys_user_role r
                      WHERE r.user_id = u.id AND r.role_code BETWEEN 0 AND 3)
        GROUP BY u.grade ORDER BY u.grade
        """)
    List<Map<String, Object>> membersByGrade();

    @Select("""
        SELECT grade AS grade, COUNT(DISTINCT user_id) AS count FROM sys_term
        WHERE deleted = 0 AND grade IS NOT NULL AND TRIM(grade) <> ''
        GROUP BY grade ORDER BY grade
        """)
    List<Map<String, Object>> memberArchiveByTerm();
}
