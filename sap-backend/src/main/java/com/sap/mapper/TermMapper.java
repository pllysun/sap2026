package com.sap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sap.entity.Term;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TermMapper extends BaseMapper<Term> {
    @org.apache.ibatis.annotations.Select({"<script>SELECT COUNT(DISTINCT t.user_id) FROM sys_term t JOIN sys_user u ON u.id=t.user_id AND u.deleted=0 WHERE t.deleted=0",
        "<if test='grade != null'> AND t.grade=#{grade}</if></script>"})
    Long countMembers(@org.apache.ibatis.annotations.Param("grade") String grade);

    @org.apache.ibatis.annotations.Select("SELECT t.grade,COUNT(DISTINCT t.user_id) AS count FROM sys_term t JOIN sys_user u ON u.id=t.user_id AND u.deleted=0 WHERE t.deleted=0 GROUP BY t.grade ORDER BY t.grade")
    java.util.List<java.util.Map<String,Object>> countsByGrade();
}
