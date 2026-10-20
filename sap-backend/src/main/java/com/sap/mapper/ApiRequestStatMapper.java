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

    @org.apache.ibatis.annotations.SelectProvider(type=ApiRequestStatSql.class,method="select")
    List<Map<String,Object>> topEndpoints(@Param("start") LocalDate start,@Param("limit") int limit,@Param("filter") com.sap.service.ApiLogExclusionService.Sql filter);
    @org.apache.ibatis.annotations.SelectProvider(type=ApiRequestStatSql.class,method="select")
    List<Map<String,Object>> trend(@Param("start") LocalDate start,@Param("filter") com.sap.service.ApiLogExclusionService.Sql filter);
    @org.apache.ibatis.annotations.SelectProvider(type=ApiRequestStatSql.class,method="select")
    List<Map<String,Object>> byUser(@Param("start") LocalDate start,@Param("userId") Long userId,@Param("filter") com.sap.service.ApiLogExclusionService.Sql filter);
    @org.apache.ibatis.annotations.SelectProvider(type=ApiRequestStatSql.class,method="select")
    List<Map<String,Object>> detail(@Param("start") LocalDate start,@Param("limit") int limit,@Param("filter") com.sap.service.ApiLogExclusionService.Sql filter);
    @org.apache.ibatis.annotations.SelectProvider(type=ApiRequestStatSql.class,method="select")
    List<Map<String,Object>> byEndpoint(@Param("start") LocalDate start,@Param("endpoint") String endpoint,@Param("filter") com.sap.service.ApiLogExclusionService.Sql filter);
    @org.apache.ibatis.annotations.SelectProvider(type=ApiRequestStatSql.class,method="select")
    Long totalRequests(@Param("start") LocalDate start,@Param("filter") com.sap.service.ApiLogExclusionService.Sql filter);
    @org.apache.ibatis.annotations.SelectProvider(type=ApiRequestStatSql.class,method="select")
    Long activeUsers(@Param("start") LocalDate start,@Param("filter") com.sap.service.ApiLogExclusionService.Sql filter);
    @org.apache.ibatis.annotations.SelectProvider(type=ApiRequestStatSql.class,method="select")
    List<Map<String,Object>> distinctUsers(@Param("filter") com.sap.service.ApiLogExclusionService.Sql filter);
}
