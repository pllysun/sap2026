package com.sap.mapper;

import com.sap.service.ApiLogExclusionService;
import org.apache.ibatis.builder.annotation.ProviderContext;
import java.util.Map;

/** SQL 结构由代码定义，过滤表达式仍通过 MyBatis 绑定参数，绝不拼入用户输入。 */
public class ApiRequestStatSql {
    public String select(Map<String,Object> params,ProviderContext context) {
        var filter=(ApiLogExclusionService.Sql)params.get("filter");
        String condition=filter.text();
        for(int i=0;i<filter.args().size();i++)condition=condition.replaceFirst("\\?","#{filter.args["+i+"]}");
        String from=" FROM stat_api_request WHERE stat_date >= #{start}"+condition;
        return switch(context.getMapperMethod().getName()) {
            case "topEndpoints" -> "SELECT endpoint,http_method httpMethod,SUM(count) cnt"+from+" GROUP BY endpoint,http_method ORDER BY cnt DESC LIMIT #{limit}";
            case "trend" -> "SELECT stat_date statDate,SUM(count) cnt"+from+" GROUP BY stat_date ORDER BY stat_date";
            case "byUser" -> "SELECT endpoint,http_method httpMethod,SUM(count) cnt"+from+" AND user_id=#{userId} GROUP BY endpoint,http_method ORDER BY cnt DESC";
            case "detail" -> "SELECT user_id userId,user_name userName,endpoint,http_method httpMethod,SUM(count) cnt"+from+" GROUP BY user_id,user_name,endpoint,http_method ORDER BY cnt DESC LIMIT #{limit}";
            case "byEndpoint" -> "SELECT user_id userId,user_name userName,SUM(count) cnt"+from+" AND endpoint=#{endpoint} GROUP BY user_id,user_name ORDER BY cnt DESC";
            case "totalRequests" -> "SELECT COALESCE(SUM(count),0)"+from;
            case "activeUsers" -> "SELECT COUNT(DISTINCT user_id)"+from+" AND user_id>0";
            case "distinctUsers" -> "SELECT DISTINCT user_id userId,user_name userName FROM stat_api_request WHERE user_id>0"+condition+" ORDER BY user_name";
            default -> throw new IllegalArgumentException("未知的接口统计查询");
        };
    }
}
