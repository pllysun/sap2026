package com.sap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sap.entity.FileObject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileObjectMapper extends BaseMapper<FileObject> {

    /** 下载重定向计量端点据此按公网直链反查文件大小。 */
    @Select("SELECT * FROM stat_file_object WHERE url = #{url} LIMIT 1")
    FileObject selectByUrl(String url);
}
