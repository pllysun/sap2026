package io.sapoj.mapper.judger;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.sapoj.entity.judger.OjSubmission;
import io.sapoj.vo.judger.OjProgressSummary;
import io.sapoj.vo.judger.OjLearningStats;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface OjSubmissionMapper extends BaseMapper<OjSubmission> {
    @Select("""
        <script>
        SELECT problem_id,
          SUM(CASE WHEN status NOT IN ('QUEUED','RUNNING','SYSTEM_ERROR') THEN 1 ELSE 0 END) AS attempts,
          SUM(CASE WHEN status='AC' OR ever_accepted=TRUE THEN 1 ELSE 0 END) AS ac_attempts,
          MIN(CASE WHEN status='AC' OR ever_accepted=TRUE THEN created_at ELSE NULL END) AS first_accepted_at,
          MAX(created_at) AS last_submitted_at
        FROM oj_submission WHERE user_id=#{userId} AND kind='SUBMIT' AND problem_set_id IS NULL
        AND problem_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        GROUP BY problem_id
        </script>
        """)
    List<OjLearningStats> learningStats(@Param("userId") Long userId,@Param("ids") List<Long> ids);

    @Select("""
        <script>
        SELECT CAST(created_at AS DATE) AS activity_date, COUNT(*) AS attempts,
          SUM(CASE WHEN status='AC' OR ever_accepted=TRUE THEN 1 ELSE 0 END) AS accepted
        FROM oj_submission WHERE user_id=#{userId} AND kind='SUBMIT' AND problem_set_id IS NULL
          AND status NOT IN ('QUEUED','RUNNING','SYSTEM_ERROR') AND created_at &gt;= #{since}
          AND problem_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        GROUP BY CAST(created_at AS DATE) ORDER BY activity_date
        </script>
        """)
    List<java.util.Map<String,Object>> learningDays(@Param("userId") Long userId,@Param("ids") List<Long> ids,
                                                   @Param("since") java.time.LocalDateTime since);
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
