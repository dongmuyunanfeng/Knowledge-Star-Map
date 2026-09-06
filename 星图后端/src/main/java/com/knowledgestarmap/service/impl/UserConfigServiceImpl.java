package com.knowledgestarmap.service.impl;

import com.alibaba.fastjson2.JSON;
import com.knowledgestarmap.dto.UserConfigDTO;
import com.knowledgestarmap.enums.BizErrorCode;
import com.knowledgestarmap.exception.BizException;
import com.knowledgestarmap.service.UserConfigService;
import com.knowledgestarmap.util.RedisKeyBuilder;
import com.knowledgestarmap.vo.UserConfigVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserConfigServiceImpl implements UserConfigService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public UserConfigVO getConfig(Long userId) {
        String key = RedisKeyBuilder.userConfig(userId);
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                UserConfigVO defaultConfig = new UserConfigVO();
                defaultConfig.setEnableAutoKnowledgeSuggestion(false);
                return defaultConfig;
            }
            return JSON.parseObject(json, UserConfigVO.class);
        } catch (Exception e) {
            log.warn("读取用户配置失败, userId={}", userId, e);
            UserConfigVO fallback = new UserConfigVO();
            fallback.setEnableAutoKnowledgeSuggestion(false);
            return fallback;
        }
    }

    @Override
    public void updateConfig(Long userId, UserConfigDTO dto) {
        if (dto == null) {
            throw new BizException(BizErrorCode.PARAM_ERROR);
        }
        UserConfigVO vo = new UserConfigVO();
        vo.setEnableAutoKnowledgeSuggestion(
                dto.getEnableAutoKnowledgeSuggestion() != null
                        ? dto.getEnableAutoKnowledgeSuggestion()
                        : false
        );
        try {
            String key = RedisKeyBuilder.userConfig(userId);
            redisTemplate.opsForValue().set(key, JSON.toJSONString(vo));
        } catch (Exception e) {
            log.error("写入用户配置失败, userId={}", userId, e);
            throw new BizException(BizErrorCode.CONFIG_WRITE_ERROR);
        }
    }
}
