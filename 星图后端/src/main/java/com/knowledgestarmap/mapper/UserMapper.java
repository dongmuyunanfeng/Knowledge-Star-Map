package com.knowledgestarmap.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.knowledgestarmap.entity.UserDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
