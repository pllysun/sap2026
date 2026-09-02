package com.sap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sap.entity.AppFeedbackComment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppFeedbackCommentMapper extends BaseMapper<AppFeedbackComment> {

    /** 管理端永久删除 Issue 时同步物理删除完整讨论时间线。 */
    @Delete("DELETE FROM app_feedback_comment WHERE issue_id = #{issueId}")
    int hardDeleteByIssueId(@Param("issueId") Long issueId);
}
