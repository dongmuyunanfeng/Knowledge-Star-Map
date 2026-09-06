package com.knowledgestarmap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgestarmap.entity.KnowledgeInfoDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface KnowledgeInfoMapper extends BaseMapper<KnowledgeInfoDO> {

    @Delete("DELETE FROM knowledge_info WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
