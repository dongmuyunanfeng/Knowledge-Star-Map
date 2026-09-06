package com.knowledgestarmap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgestarmap.entity.FileKnowledgeDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FileKnowledgeMapper extends BaseMapper<FileKnowledgeDO> {

    @Delete("DELETE FROM file_knowledge WHERE file_id = #{fileId} AND user_id = #{userId}")
    int physicalDeleteByFileId(@Param("fileId") Long fileId, @Param("userId") Long userId);
}
