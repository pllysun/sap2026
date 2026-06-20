package com.sap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sap.entity.CosTraffic;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface CosTrafficMapper extends BaseMapper<CosTraffic> {

    /** 原子 upsert：同 (日,用户,方向) 累加字节与笔数，避免并发丢更新。 */
    @Insert("INSERT INTO stat_cos_traffic(stat_date,user_id,user_name,direction,bytes,count) " +
            "VALUES(#{statDate},#{userId},#{userName},#{direction},#{bytes},1) " +
            "ON DUPLICATE KEY UPDATE bytes = bytes + #{bytes}, count = count + 1, user_name = #{userName}")
    int upsert(@Param("statDate") LocalDate statDate,
               @Param("userId") Long userId,
               @Param("userName") String userName,
               @Param("direction") String direction,
               @Param("bytes") long bytes);

    /** 按用户聚合上传/下载字节与次数（窗口内），按总字节倒序。 */
    @Select("SELECT user_id userId, user_name userName, " +
            "COALESCE(SUM(CASE WHEN direction='UPLOAD' THEN bytes ELSE 0 END),0) uploadBytes, " +
            "COALESCE(SUM(CASE WHEN direction='DOWNLOAD' THEN bytes ELSE 0 END),0) downloadBytes, " +
            "COALESCE(SUM(CASE WHEN direction='UPLOAD' THEN count ELSE 0 END),0) uploadCount, " +
            "COALESCE(SUM(CASE WHEN direction='DOWNLOAD' THEN count ELSE 0 END),0) downloadCount " +
            "FROM stat_cos_traffic WHERE stat_date >= #{start} " +
            "GROUP BY user_id, user_name ORDER BY SUM(bytes) DESC")
    List<Map<String, Object>> aggByUser(@Param("start") LocalDate start);

    /** 按日聚合上传/下载字节，用于趋势。 */
    @Select("SELECT stat_date statDate, " +
            "COALESCE(SUM(CASE WHEN direction='UPLOAD' THEN bytes ELSE 0 END),0) uploadBytes, " +
            "COALESCE(SUM(CASE WHEN direction='DOWNLOAD' THEN bytes ELSE 0 END),0) downloadBytes " +
            "FROM stat_cos_traffic WHERE stat_date >= #{start} GROUP BY stat_date ORDER BY stat_date")
    List<Map<String, Object>> trend(@Param("start") LocalDate start);

    /** 某用户当日上传累计(字节与次数)，用于上传前配额校验防盗刷。 */
    @Select("SELECT COALESCE(SUM(bytes),0) bytes, COALESCE(SUM(count),0) cnt FROM stat_cos_traffic " +
            "WHERE stat_date = #{date} AND user_id = #{userId} AND direction = 'UPLOAD'")
    Map<String, Object> userDailyUpload(@Param("date") LocalDate date, @Param("userId") Long userId);

    /** 窗口内总上传/下载字节，用于概览卡片。 */
    @Select("SELECT COALESCE(SUM(CASE WHEN direction='UPLOAD' THEN bytes ELSE 0 END),0) uploadBytes, " +
            "COALESCE(SUM(CASE WHEN direction='DOWNLOAD' THEN bytes ELSE 0 END),0) downloadBytes " +
            "FROM stat_cos_traffic WHERE stat_date >= #{start}")
    Map<String, Object> totals(@Param("start") LocalDate start);
}
