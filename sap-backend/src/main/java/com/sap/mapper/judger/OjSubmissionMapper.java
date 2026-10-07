package com.sap.mapper.judger;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sap.entity.judger.OjSubmission;
import com.sap.vo.judger.OjProgressSummary;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface OjSubmissionMapper extends BaseMapper<OjSubmission> {
    @Select("""
        <script>
        SELECT progress.problem_id, progress.accepted, progress.pending, latest.status AS last_status
        FROM (
            SELECT problem_id,
                MAX(CASE WHEN ever_accepted = TRUE OR status = 'AC' THEN 1 ELSE 0 END) AS accepted,
                MAX(CASE WHEN status IN ('QUEUED', 'RUNNING') THEN 1 ELSE 0 END) AS pending,
                MAX(CASE WHEN status NOT IN ('QUEUED', 'RUNNING') THEN id ELSE NULL END) AS last_id
            FROM oj_submission
            WHERE user_id = #{userId} AND kind = 'SUBMIT'
            <choose>
                <when test="problemSetId != null">AND problem_set_id = #{problemSetId}</when>
                <otherwise>AND problem_set_id IS NULL</otherwise>
            </choose>
            AND problem_id IN
            <foreach collection="problemIds" item="problemId" open="(" separator="," close=")">#{problemId}</foreach>
            GROUP BY problem_id
        ) progress
        LEFT JOIN oj_submission latest ON latest.id = progress.last_id
        </script>
        """)
    List<OjProgressSummary> progress(@Param("userId") Long userId,
                                   @Param("problemSetId") Long problemSetId,
                                   @Param("problemIds") List<Long> problemIds);
}
