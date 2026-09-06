package com.knowledgestarmap.service.impl;

import com.alibaba.fastjson2.JSON;
import com.knowledgestarmap.dto.UserConfigDTO;
import com.knowledgestarmap.dto.UserLoginDTO;
import com.knowledgestarmap.dto.UserRegisterDTO;
import com.knowledgestarmap.entity.UserDO;
import com.knowledgestarmap.enums.BizErrorCode;
import com.knowledgestarmap.exception.BizException;
import com.knowledgestarmap.mapper.UserMapper;
import com.knowledgestarmap.security.JwtService;
import com.knowledgestarmap.security.UserContext;
import com.knowledgestarmap.service.AuthService;
import com.knowledgestarmap.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Override
    public String register(UserRegisterDTO dto) {
        if (dto.getPassword() == null || dto.getPassword().length() < 8) {
            throw new BizException(BizErrorCode.PARAM_ERROR, "密码长度不能少于8位");
        }
        if (!dto.getPassword().matches(".*[a-zA-Z].*") || !dto.getPassword().matches(".*\\d.*")) {
            throw new BizException(BizErrorCode.PARAM_ERROR, "密码必须包含字母和数字");
        }

        UserDO exist = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getUsername, dto.getUsername())
        );
        if (exist != null) {
            throw new BizException(BizErrorCode.USER_ALREADY_EXISTS);
        }

        UserDO user = new UserDO();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname() != null ? dto.getNickname() : dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setStudyDirection(dto.getStudyDirection());
        user.setJobTarget(dto.getJobTarget());
        user.setStatus(1);
        userMapper.insert(user);

        return jwtService.generateToken(user.getId(), user.getUsername());
    }

    @Override
    public String login(UserLoginDTO dto) {
        UserDO user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserDO>()
                        .eq(UserDO::getUsername, dto.getUsername())
        );
        if (user == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(BizErrorCode.PASSWORD_INCORRECT);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        return jwtService.generateToken(user.getId(), user.getUsername());
    }

    @Override
    public UserVO getCurrentUser() {
        Long userId = UserContext.getUserId();
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setStudyDirection(user.getStudyDirection());
        vo.setJobTarget(user.getJobTarget());
        vo.setEmail(user.getEmail());
        return vo;
    }

    @Override
    public UserVO updateCurrentUser(UserRegisterDTO dto) {
        Long userId = UserContext.getUserId();
        UserDO user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(BizErrorCode.USER_NOT_FOUND);
        }
        if (dto != null) {
            if (dto.getNickname() != null) {
                user.setNickname(dto.getNickname());
            }
            if (dto.getEmail() != null) {
                user.setEmail(dto.getEmail());
            }
            if (dto.getStudyDirection() != null) {
                user.setStudyDirection(dto.getStudyDirection());
            }
            if (dto.getJobTarget() != null) {
                user.setJobTarget(dto.getJobTarget());
            }
            userMapper.updateById(user);
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setStudyDirection(user.getStudyDirection());
        vo.setJobTarget(user.getJobTarget());
        vo.setEmail(user.getEmail());
        return vo;
    }
}
