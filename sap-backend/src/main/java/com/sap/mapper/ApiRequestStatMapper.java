package com.sap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sap.entity.ApiRequestStat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface ApiRequestStatMapper extends BaseMapper<ApiRequestStat> {

    /** 原子 upsert：同 (日,用户,接口,方法) 次数 +1。 */
    @Insert("INSERT INTO stat_api_request(stat_date,user_id,user_name,endpoint,http_method,count) " +
            "VALUES(#{statDate},#{userId},#{userName},#{endpoint},#{httpMethod},1) " +
            "ON DUPLICATE KEY UPDATE count = count + 1, user_name = #{userName}")
    int upsert(@Param("statDate") LocalDate statDate,
               @Param("userId") Long userId,
               @Param("userName") String userName,
               @Param("endpoint") String endpoint,
               @Param("httpMethod") String httpMethod);

    /** 全部接口总计 Top N（按方法分组）。 */
    @Select("SELECT endpoint, http_method httpMethod, SUM(count) cnt FROM stat_api_request " +
            "WHERE stat_date >= #{start} GROUP BY endpoint, http_method ORDER BY cnt DESC LIMIT #{limit}")
    List<Map<String, Object>> topEndpoints(@Param("start") LocalDate start, @Param("limit") int limit);

    /** 按日总请求数，用于趋势。 */
    @Select("SELECT stat_date statDate, SUM(count) cnt FROM stat_api_request " +
            "WHERE stat_date >= #{start} GROUP BY stat_date ORDER BY stat_date")
    List<Map<String, Object>> trend(@Param("start") LocalDate start);

    /** 指定用户的逐接口请求数。 */
    @Select("SELECT endpoint, http_method httpMethod, SUM(count) cnt FROM stat_api_request " +
            "WHERE stat_date >= #{start} AND user_id = #{userId} GROUP BY endpoint, http_method ORDER BY cnt DESC")
    List<Map<String, Object>> byUser(@Param("start") LocalDate start, @Param("userId") Long userId);

    /** 用户×接口×方法 全量明细（窗口内，按次数倒序，限 N 行），供前端统一搜索/排序表格。 */
    @Select("SELECT user_id userId, user_name userName, endpoint, http_method httpMethod, SUM(count) cnt " +
            "FROM stat_api_request WHERE stat_date >= #{start} " +
            "GROUP BY user_id, user_name, endpoint, http_method ORDER BY cnt DESC LIMIT #{limit}")
    List<Map<String, Object>> detail(@Param("start") LocalDate start, @Param("limit") int limit);

    /** 指定接口的逐用户请求数。 */
    @Select("SELECT user_id userId, user_name userName, SUM(count) cnt FROM stat_api_request " +
            "WHERE stat_date >= #{start} AND endpoint = #{endpoint} GROUP BY user_id, user_name ORDER BY cnt DESC")
    List<Map<String, Object>> byEndpoint(@Param("start") LocalDate start, @Param("endpoint") String endpoint);

    /** 窗口内总请求数。 */
    @Select("SELECT COALESCE(SUM(count),0) FROM stat_api_request WHERE stat_date >= #{start}")
    Long totalRequests(@Param("start") LocalDate start);

    /** 窗口内活跃用户数（排除匿名 0）。 */
    @Select("SELECT COUNT(DISTINCT user_id) FROM stat_api_request WHERE stat_date >= #{start} AND user_id <> 0")
    Long activeUsers(@Param("start") LocalDate start);

    /** 有统计记录的用户列表（供下钻选择器）。 */
    @Select("SELECT DISTINCT user_id userId, user_name userName FROM stat_api_request " +
            "WHERE user_id <> 0 ORDER BY user_name")
    List<Map<String, Object>> distinctUsers();
}
