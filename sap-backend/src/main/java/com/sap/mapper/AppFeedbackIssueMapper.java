package com.sap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sap.entity.AppFeedbackIssue;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AppFeedbackIssueMapper extends BaseMapper<AppFeedbackIssue> {

    /**
     * 未处理 = 仍开放且从未收到维护者回复。关闭或维护者回复都会立即释放一条额度；
     * 逻辑删除/彻底删除的记录均不计入。
     */
    @Select("""
            SELECT COUNT(*)
              FROM app_feedback_issue i
             WHERE i.reporter_id = #{reporterId}
               AND i.status = 'OPEN'
               AND i.deleted = 0
               AND NOT EXISTS (
                    SELECT 1
                      FROM app_feedback_comment c
                     WHERE c.issue_id = i.id
                       AND c.admin_reply = 1
                       AND c.deleted = 0
               )
            """)
    long countUnprocessedByReporter(@Param("reporterId") Long reporterId);

    /** 管理端永久删除，明确绕过 @TableLogic。 */
    @Delete("DELETE FROM app_feedback_issue WHERE id = #{issueId}")
    int hardDeleteById(@Param("issueId") Long issueId);
}
