package com.sap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sap.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 创建反馈前按账号串行化“统计未处理数量 → 插入”流程，避免并发请求同时越过额度。
     * 必须在事务内调用，行锁会在事务结束时释放。
     */
    @Select("SELECT id FROM sys_user WHERE id = #{userId} AND deleted = 0 FOR UPDATE")
    Long lockActiveUserById(@Param("userId") Long userId);
}
