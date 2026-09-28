package com.sap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sap.entity.EmailTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EmailTemplateMapper extends BaseMapper<EmailTemplate> {

    /** 查询包含已软删除记录的模板，用于删除后重新创建同一标识时恢复原记录。 */
    @Select("SELECT * FROM sys_email_template WHERE template_key = #{templateKey} LIMIT 1")
    EmailTemplate selectAnyByKey(String templateKey);

    @Update("UPDATE sys_email_template SET template_key=#{templateKey}, template_name=#{templateName}, subject=#{subject}, "
            + "html_content=#{htmlContent}, description=#{description}, enabled=#{enabled}, deleted=0, updated_at=NOW() "
            + "WHERE id=#{id}")
    int restore(@Param("id") Long id,
                @Param("templateKey") String templateKey,
                @Param("templateName") String templateName,
                @Param("subject") String subject,
                @Param("htmlContent") String htmlContent,
                @Param("description") String description,
                @Param("enabled") Boolean enabled);
}
