package com.sap.mapper.judger;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sap.entity.judger.OjProblem;
import com.sap.vo.judger.OjCatalogRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface OjProblemMapper extends BaseMapper<OjProblem> {
    String CATALOG_WHERE = """
        WHERE status='PUBLISHED' AND validation_signature=#{signature}
        <if test="keyword != null">
          AND LOCATE(#{keyword},LOWER(CONCAT(COALESCE(title,''),' ',
              JSON_EXTRACT(pack_json,'$.tags'),' ',
              JSON_UNQUOTE(JSON_EXTRACT(pack_json,'$.sourcePlatform'))))) &gt; 0
        </if>
        <if test="difficulty != null">AND difficulty=#{difficulty}</if>
        <if test="tagJson != null">AND JSON_CONTAINS(JSON_EXTRACT(pack_json,'$.tags'),#{tagJson})=1</if>
        <if test="sourceJson != null">AND JSON_CONTAINS(JSON_EXTRACT(pack_json,'$.sourcePlatform'),#{sourceJson})=1</if>
        <if test="modeJson != null">AND JSON_CONTAINS(JSON_EXTRACT(pack_json,'$.modes'),#{modeJson})=1</if>
        """;

    @Select("<script> SELECT COUNT(*) FROM oj_problem " + CATALOG_WHERE + " </script>")
    long countCatalog(@Param("signature") String signature, @Param("keyword") String keyword,
                      @Param("difficulty") String difficulty, @Param("tagJson") String tagJson,
                      @Param("sourceJson") String sourceJson, @Param("modeJson") String modeJson);

    @Select("""
        <script> SELECT id,slug,title,difficulty,status,sort_order,revision,
          JSON_EXTRACT(pack_json,'$.tags') AS tags_json,
          JSON_EXTRACT(pack_json,'$.modes') AS modes_json,
          JSON_UNQUOTE(JSON_EXTRACT(pack_json,'$.sourcePlatform')) AS source_platform
        FROM oj_problem
        """ + CATALOG_WHERE + """
        ORDER BY sort_order IS NULL,sort_order ASC,id DESC LIMIT #{size} OFFSET #{offset}
        </script>
        """)
    List<OjCatalogRow> catalog(@Param("signature") String signature, @Param("keyword") String keyword,
                              @Param("difficulty") String difficulty, @Param("tagJson") String tagJson,
                              @Param("sourceJson") String sourceJson, @Param("modeJson") String modeJson,
                              @Param("size") int size, @Param("offset") long offset);

    @Select("""
        SELECT JSON_EXTRACT(pack_json,'$.tags') AS tags_json,
          JSON_EXTRACT(pack_json,'$.modes') AS modes_json,
          JSON_UNQUOTE(JSON_EXTRACT(pack_json,'$.sourcePlatform')) AS source_platform
        FROM oj_problem WHERE status='PUBLISHED' AND validation_signature=#{signature}
        """)
    List<OjCatalogRow> catalogFilters(@Param("signature") String signature);

    @Select("""
        <script> SELECT id FROM oj_problem WHERE status='PUBLISHED' AND validation_signature=#{signature}
        AND id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        </script>
        """)
    List<Long> visibleIds(@Param("signature") String signature,@Param("ids") List<Long> ids);

    @Select("SELECT id,sort_order,slug,title,difficulty,status,revision FROM oj_problem ORDER BY sort_order IS NULL,sort_order ASC,id DESC")
    List<OjProblem> orderedMetadata();
}
