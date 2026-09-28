package com.sap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sap.entity.LogStats;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LogStatsMapper extends BaseMapper<LogStats> {
    @org.apache.ibatis.annotations.Update("UPDATE log_stats SET count = count + 1 WHERE id = #{id}")
    int increment(@org.apache.ibatis.annotations.Param("id") Long id);
}
