package com.knowledgestarmap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgestarmap.entity.ProjectExperienceDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProjectExperienceMapper extends BaseMapper<ProjectExperienceDO> {

    /** 物理删除：项目经历可随时重生成，无需保留历史，避免软删行占用唯一键 uk_user_project。 */
    @Delete("DELETE FROM project_experience WHERE user_id = #{userId} AND project_id = #{projectId}")
    int physicalDeleteByProject(@Param("userId") Long userId, @Param("projectId") Long projectId);
}
