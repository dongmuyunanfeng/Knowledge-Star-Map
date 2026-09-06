package com.knowledgestarmap.service;

import com.knowledgestarmap.dto.UserConfigDTO;
import com.knowledgestarmap.vo.UserConfigVO;

public interface UserConfigService {

    UserConfigVO getConfig(Long userId);

    void updateConfig(Long userId, UserConfigDTO dto);
}
